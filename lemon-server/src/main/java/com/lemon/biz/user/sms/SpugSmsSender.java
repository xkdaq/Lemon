package com.lemon.biz.user.sms;

import com.lemon.common.BizException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通过 Spug 推送助手发短信验证码（平台官方模板，无需自己申请签名）。
 * 文档：https://push.spug.cc/guide/sms
 * <p>
 * 在 Spug 控制台「短信验证码」选官方模板后复制完整调用 URL（形如 https://push.spug.cc/sms/xxxx），
 * 放在服务器环境变量 SMS_SPUG_URL 里。URL 里的模板编码相当于调用凭证，不能提交到代码仓库。
 * 注意「通知模板」的 /send/xxx 地址只会发给模板里预设的接收人，不能用来给用户发验证码。
 * 没配 URL 时：开发环境（lemon.sms.log-code=true）把验证码打到日志里方便联调，生产环境直接报错。
 */
@Slf4j
@Component
public class SpugSmsSender {

    private final String url;
    private final String minutes;
    private final boolean logCode;
    private final RestClient client = RestClient.create();

    public SpugSmsSender(@Value("${lemon.sms.spug-url:}") String url,
                         @Value("${lemon.sms.minutes:}") String minutes,
                         @Value("${lemon.sms.log-code:false}") boolean logCode) {
        this.url = url == null ? "" : url.trim();
        this.minutes = minutes == null ? "" : minutes.trim();
        this.logCode = logCode;
    }

    /** 发送结果：status 见 SmsLog.STATUS_*；error 是给运营看的失败原因 */
    public record Result(int status, String requestId, String error) {
    }

    /** 没配置短信服务时（生产环境）直接报错，不算一次发送 */
    public void checkAvailable() {
        BizException.throwIf(url.isEmpty() && !logCode, "短信服务暂未开通，请使用密码登录");
    }

    public Result send(String phone, String code) {
        if (url.isEmpty()) {
            log.info("[开发环境] 短信验证码 {} -> {}", phone, code);
            return new Result(SmsLog.STATUS_MOCK, null, null);
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("to", phone);
        body.put("code", code);
        if (!minutes.isEmpty()) {
            body.put("number", minutes);
        }
        Map<?, ?> resp;
        try {
            resp = client.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
        } catch (Exception e) {
            log.error("短信发送失败（请求异常）phone={}", mask(phone), e);
            return new Result(SmsLog.STATUS_FAIL, null, "请求短信服务异常：" + e.getClass().getSimpleName());
        }
        Object rc = resp == null ? null : resp.get("code");
        Object requestId = resp == null ? null : resp.get("request_id");
        if (rc == null || !"200".equals(String.valueOf(rc))) {
            log.error("短信发送失败 phone={} resp={}", mask(phone), resp);
            Object msg = resp == null ? null : resp.get("msg");
            return new Result(SmsLog.STATUS_FAIL, requestId == null ? null : String.valueOf(requestId),
                    "code=" + rc + (msg == null ? "" : "，" + msg));
        }
        log.info("短信已发送 phone={} requestId={}", mask(phone), requestId);
        return new Result(SmsLog.STATUS_OK, requestId == null ? null : String.valueOf(requestId), null);
    }

    private static String mask(String phone) {
        return phone.length() < 7 ? phone : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
