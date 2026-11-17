package com.lemon.biz.user.wx;

import com.lemon.biz.operation.service.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.common.BizException;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 微信小程序服务端接口：登录凭证校验（code 换 openid）、手机号快速验证（phoneCode 换手机号）。
 * <p>
 * access_token 用「稳定版」接口获取（多实例 / 重启不会互相顶掉），缓存在 Redis，提前 5 分钟过期。
 * mock 模式（开发环境默认开）不调微信：所有 code 都当成同一个微信（openid = mock-devtools），
 * code 写成 "mock:xxx" 时 openid = mock-xxx（模拟不同微信）；phoneCode 原样当手机号。方便本地和自动化测试。
 * <p>
 * AppID / AppSecret 优先用后台「系统参数」里填的（每次调用时读取，改了立即生效），留空时用环境变量 WX_APPID / WX_SECRET。
 */
@Slf4j
@Component
public class WxMiniClient {

    private static final String API = "https://api.weixin.qq.com";
    /** access_token 按 AppID 分开缓存：后台换了小程序不会用到旧的 token */
    private static final String TOKEN_KEY = "wx:mini:access_token:";

    private final String envAppid;
    private final String envSecret;
    private final boolean mockEnabled;
    private final StringRedisTemplate redis;
    private final SysConfigService configService;
    private final RestClient client = RestClient.create();
    private static final ObjectMapper JSON = new ObjectMapper();

    public WxMiniClient(@Value("${lemon.wx.appid:}") String appid,
                        @Value("${lemon.wx.secret:}") String secret,
                        @Value("${lemon.wx.mock:false}") boolean mock,
                        StringRedisTemplate redis,
                        SysConfigService configService) {
        this.envAppid = appid == null ? "" : appid.trim();
        this.envSecret = secret == null ? "" : secret.trim();
        this.mockEnabled = mock;
        this.redis = redis;
        this.configService = configService;
    }

    private String appid() {
        return configService.getString(SysConfigService.WX_APPID, envAppid).trim();
    }

    private String secret() {
        return configService.getString(SysConfigService.WX_SECRET, envSecret).trim();
    }

    /** mock 只在开了 mock 且后台、环境变量都没配 AppID 时生效 */
    private boolean mock() {
        return mockEnabled && appid().isEmpty();
    }

    /**
     * 后台保存 AppSecret 前校验：用一个假的 code 调 jscode2session，
     * AppID / AppSecret 对的话微信只会报 code 无效（40029），不对会报 40013 / 40125 等。
     * 不需要 IP 白名单，也不会刷新 access_token。
     */
    public void verify(String secret) {
        String appid = appid();
        BizException.throwIf(appid.isEmpty(), "请先填写并保存小程序 AppID");
        Map<?, ?> resp;
        try {
            resp = parse(client.get()
                    .uri(API + "/sns/jscode2session?appid={a}&secret={s}&js_code={c}&grant_type=authorization_code",
                            appid, secret, "lemon-verify")
                    .retrieve().body(String.class));
        } catch (Exception e) {
            log.warn("校验小程序 AppSecret 时连不上微信", e);
            throw new BizException("连不上微信服务器，暂时无法校验，请稍后再试");
        }
        String err = resp == null ? "" : String.valueOf(resp.get("errcode"));
        if ("40013".equals(err)) {
            throw new BizException("AppID 不对，请检查后重新保存 AppID");
        }
        if ("40125".equals(err) || "40001".equals(err)) {
            throw new BizException("AppSecret 不对，请到小程序后台确认（重置过的话旧的会失效）");
        }
        if (!"40029".equals(err) && !"40163".equals(err)) {
            log.warn("校验小程序 AppSecret 返回 {}", resp);
            throw new BizException("微信返回：" + (resp == null ? "无响应" : resp.get("errmsg")));
        }
    }

    /** code2session 结果；unionid 只有小程序绑定了开放平台才有 */
    public record Session(String openid, String unionid) {
    }

    /** wx.login 拿到的 code 换 openid（code 只能用一次，5 分钟有效） */
    public Session code2Session(String code) {
        if (mock()) {
            // 开发者工具每次 wx.login 的 code 都不同，而真实 openid 对同一个微信是固定的：
            // 默认当成同一个微信用户；接口测试要模拟不同微信时传 code = "mock:<任意标识>"
            return new Session(code.startsWith("mock:") ? "mock-" + code.substring(5) : "mock-devtools", null);
        }
        checkConfigured();
        Map<?, ?> resp = parse(client.get()
                .uri(API + "/sns/jscode2session?appid={a}&secret={s}&js_code={c}&grant_type=authorization_code",
                        appid(), secret(), code)
                .retrieve().body(String.class));
        Object openid = resp == null ? null : resp.get("openid");
        if (openid == null) {
            log.warn("jscode2session 失败 resp={}", resp);
            throw new BizException("微信登录失败，请重试");
        }
        Object unionid = resp.get("unionid");
        return new Session(String.valueOf(openid), unionid == null ? null : String.valueOf(unionid));
    }

    /** 手机号快速验证：button open-type=getPhoneNumber 拿到的 code 换手机号（不带区号） */
    public String phoneNumber(String phoneCode) {
        if (mock()) {
            return phoneCode;
        }
        checkConfigured();
        Map<?, ?> resp = callPhone(phoneCode, accessToken(false));
        Object err = resp == null ? null : resp.get("errcode");
        // access_token 失效（被别处刷新等）：强制刷新后重试一次
        if (err != null && ("40001".equals(String.valueOf(err)) || "42001".equals(String.valueOf(err)))) {
            resp = callPhone(phoneCode, accessToken(true));
        }
        Object info = resp == null ? null : resp.get("phone_info");
        if (!(info instanceof Map<?, ?> m) || m.get("purePhoneNumber") == null) {
            log.warn("getuserphonenumber 失败 resp={}", resp);
            throw new BizException("获取手机号失败，请重试");
        }
        return String.valueOf(m.get("purePhoneNumber"));
    }

    private Map<?, ?> callPhone(String phoneCode, String token) {
        return parse(client.post().uri(API + "/wxa/business/getuserphonenumber?access_token={t}", token)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("code", phoneCode))
                .retrieve().body(String.class));
    }

    private String accessToken(boolean forceRefresh) {
        String appid = appid();
        String tokenKey = TOKEN_KEY + appid;
        if (!forceRefresh) {
            String cached = redis.opsForValue().get(tokenKey);
            if (cached != null) {
                return cached;
            }
        }
        Map<?, ?> resp = parse(client.post().uri(API + "/cgi-bin/stable_token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("grant_type", "client_credential", "appid", appid, "secret", secret(),
                        "force_refresh", forceRefresh))
                .retrieve().body(String.class));
        Object token = resp == null ? null : resp.get("access_token");
        if (token == null) {
            log.error("获取小程序 access_token 失败 resp={}", resp);
            throw new BizException("微信服务暂时不可用，请稍后再试");
        }
        long expires = resp.get("expires_in") instanceof Number n ? n.longValue() : 7200;
        redis.opsForValue().set(tokenKey, String.valueOf(token), Duration.ofSeconds(Math.max(60, expires - 300)));
        return String.valueOf(token);
    }

    /** 微信接口返回的是 JSON，但 jscode2session 的 Content-Type 是 text/plain，所以按字符串取回再自己解析 */
    private static Map<?, ?> parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(body, Map.class);
        } catch (Exception e) {
            log.warn("微信接口返回的不是 JSON: {}", body.length() > 200 ? body.substring(0, 200) : body);
            return null;
        }
    }

    private void checkConfigured() {
        BizException.throwIf(appid().isEmpty() || secret().isEmpty(), "小程序登录暂未开通");
    }
}
