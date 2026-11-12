package com.lemon.biz.notify;

import com.lemon.biz.operation.service.SysConfigService;
import com.lemon.common.JsonUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 飞书群机器人推送（重要告警、每日汇总）。
 * <p>
 * webhook 地址和签名密钥放在系统参数（notify.feishu_webhook / notify.feishu_secret），不进代码仓库；
 * 没配置就不发。消息在后台线程发送，失败只打日志，不影响业务请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuNotifier {

    public static final String WEBHOOK = "notify.feishu_webhook";
    public static final String SECRET = "notify.feishu_secret";

    /** 卡片标题颜色 */
    public enum Level {
        INFO("blue"), WARN("orange"), DANGER("red"), OK("green");

        final String color;

        Level(String color) {
            this.color = color;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "feishu-notify");
        t.setDaemon(true);
        return t;
    });

    private final SysConfigService configService;
    private final StringRedisTemplate redis;
    private final Environment env;

    public boolean configured() {
        return !configService.getString(WEBHOOK, "").isBlank();
    }

    /** 异步发送一张卡片；content 支持飞书 lark_md（**加粗**、换行） */
    public void send(Level level, String title, String content) {
        String webhook = configService.getString(WEBHOOK, "");
        if (webhook.isBlank()) {
            return;
        }
        String secret = configService.getString(SECRET, "");
        POOL.submit(() -> post(webhook, secret, level, title, content));
    }

    /**
     * 同一个 key 在 interval 内只发一次（防止同一类问题刷屏）
     *
     * @return 这次是否发出
     */
    public boolean sendThrottled(String key, Duration interval, Level level, String title, String content) {
        if (!configured()) {
            return false;
        }
        try {
            Boolean first = redis.opsForValue().setIfAbsent("notify:throttle:" + key, "1", interval);
            if (!Boolean.TRUE.equals(first)) {
                return false;
            }
        } catch (Exception e) {
            log.warn("飞书推送限流检查失败: {}", e.getMessage());
        }
        send(level, title, content);
        return true;
    }

    /** 同步发送（后台「发送测试消息」用），返回飞书的错误信息，成功返回 null */
    public String sendNow(Level level, String title, String content) {
        String webhook = configService.getString(WEBHOOK, "");
        if (webhook.isBlank()) {
            return "还没有配置飞书 webhook 地址";
        }
        return post(webhook, configService.getString(SECRET, ""), level, title, content);
    }

    private String post(String webhook, String secret, Level level, String rawTitle, String content) {
        // 非线上环境发出的消息加前缀，避免和线上数据混淆
        String title = env.matchesProfiles("prod") ? rawTitle : "【测试环境】" + rawTitle;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (!secret.isBlank()) {
                long ts = System.currentTimeMillis() / 1000;
                body.put("timestamp", String.valueOf(ts));
                body.put("sign", sign(ts, secret));
            }
            body.put("msg_type", "interactive");
            body.put("card", Map.of(
                    "config", Map.of("wide_screen_mode", true),
                    "header", Map.of("template", level.color,
                            "title", Map.of("tag", "plain_text", "content", title)),
                    "elements", List.of(Map.of("tag", "div",
                            "text", Map.of("tag", "lark_md", "content", content)))));
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhook))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 飞书成功返回 {"code":0,...}（旧版为 StatusCode 0）
            String r = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() != 200 || !(r.contains("\"code\":0") || r.contains("\"StatusCode\":0"))) {
                log.warn("飞书推送失败: {} {}", resp.statusCode(), r);
                return "飞书返回：" + r;
            }
            return null;
        } catch (Exception e) {
            log.warn("飞书推送失败: {}", e.getMessage());
            return "发送失败：" + e.getMessage();
        }
    }

    /** 飞书签名：base64(HmacSHA256(key = timestamp + "\n" + secret, data = 空)) */
    private static String sign(long timestamp, String secret) throws Exception {
        String key = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }
}
