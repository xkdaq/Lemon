package com.lemon.biz.user.wx;

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
 */
@Slf4j
@Component
public class WxMiniClient {

    private static final String API = "https://api.weixin.qq.com";
    private static final String TOKEN_KEY = "wx:mini:access_token";

    private final String appid;
    private final String secret;
    private final boolean mock;
    private final StringRedisTemplate redis;
    private final RestClient client = RestClient.create();

    public WxMiniClient(@Value("${lemon.wx.appid:}") String appid,
                        @Value("${lemon.wx.secret:}") String secret,
                        @Value("${lemon.wx.mock:false}") boolean mock,
                        StringRedisTemplate redis) {
        this.appid = appid == null ? "" : appid.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.mock = mock && this.appid.isEmpty();
        this.redis = redis;
    }

    /** code2session 结果；unionid 只有小程序绑定了开放平台才有 */
    public record Session(String openid, String unionid) {
    }

    /** wx.login 拿到的 code 换 openid（code 只能用一次，5 分钟有效） */
    public Session code2Session(String code) {
        if (mock) {
            // 开发者工具每次 wx.login 的 code 都不同，而真实 openid 对同一个微信是固定的：
            // 默认当成同一个微信用户；接口测试要模拟不同微信时传 code = "mock:<任意标识>"
            return new Session(code.startsWith("mock:") ? "mock-" + code.substring(5) : "mock-devtools", null);
        }
        checkConfigured();
        Map<?, ?> resp = client.get()
                .uri(API + "/sns/jscode2session?appid={a}&secret={s}&js_code={c}&grant_type=authorization_code",
                        appid, secret, code)
                .retrieve().body(Map.class);
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
        if (mock) {
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
        return client.post().uri(API + "/wxa/business/getuserphonenumber?access_token={t}", token)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("code", phoneCode))
                .retrieve().body(Map.class);
    }

    private String accessToken(boolean forceRefresh) {
        if (!forceRefresh) {
            String cached = redis.opsForValue().get(TOKEN_KEY);
            if (cached != null) {
                return cached;
            }
        }
        Map<?, ?> resp = client.post().uri(API + "/cgi-bin/stable_token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("grant_type", "client_credential", "appid", appid, "secret", secret,
                        "force_refresh", forceRefresh))
                .retrieve().body(Map.class);
        Object token = resp == null ? null : resp.get("access_token");
        if (token == null) {
            log.error("获取小程序 access_token 失败 resp={}", resp);
            throw new BizException("微信服务暂时不可用，请稍后再试");
        }
        long expires = resp.get("expires_in") instanceof Number n ? n.longValue() : 7200;
        redis.opsForValue().set(TOKEN_KEY, String.valueOf(token), Duration.ofSeconds(Math.max(60, expires - 300)));
        return String.valueOf(token);
    }

    private void checkConfigured() {
        BizException.throwIf(appid.isEmpty() || secret.isEmpty(), "小程序登录暂未开通");
    }
}
