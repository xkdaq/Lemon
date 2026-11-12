package com.lemon.biz.user.sms;

import com.lemon.biz.notify.FeishuNotifier;
import com.lemon.biz.user.mapper.SmsLogMapper;
import com.lemon.common.BizException;
import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 短信验证码：发送限流 + 校验。
 * <ul>
 *   <li>验证码 6 位数字，5 分钟有效，用过即失效；同一个验证码最多试错 5 次</li>
 *   <li>同一手机号 60 秒内只能发一次、每天最多 10 次；同一 IP 每小时最多 20 次（防短信轰炸刷钱）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_TRIES = 5;
    private static final int PHONE_DAILY_LIMIT = 10;
    private static final int IP_HOURLY_LIMIT = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final SpugSmsSender sender;
    private final SmsLogMapper logMapper;
    private final FeishuNotifier notifier;

    /**
     * @param scene SmsLog.SCENE_*，记录用
     */
    public void send(String phone, int scene, String ip) {
        sender.checkAvailable();
        Boolean first = redis.opsForValue().setIfAbsent("sms:cool:" + phone, "1", COOLDOWN);
        BizException.throwIf(!Boolean.TRUE.equals(first), "验证码已发送，请 60 秒后再试");
        if (count("sms:day:" + phone, Duration.ofDays(1)) > PHONE_DAILY_LIMIT) {
            throw new BizException("该手机号今天获取验证码次数过多，请明天再试");
        }
        if (ip != null && count("sms:ip:" + ip, Duration.ofHours(1)) > IP_HOURLY_LIMIT) {
            throw new BizException("获取验证码过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        SpugSmsSender.Result r = sender.send(phone, code);
        writeLog(phone, scene, ip, r);
        if (r.status() == SmsLog.STATUS_FAIL) {
            // 短信发不出去用户就没法注册 / 找回密码：推送飞书（10 分钟内只推一次）
            notifier.sendThrottled("sms-fail", java.time.Duration.ofMinutes(10), FeishuNotifier.Level.DANGER,
                    "短信发送失败：用户收不到验证码",
                    "**错误**：" + (r.error() == null ? "未知" : r.error())
                            + "\n可能是短信服务余额不足或模板异常，请检查 Spug 控制台和后台「短信记录」");
            redis.delete("sms:cool:" + phone); // 没发出去不占冷却时间
            throw new BizException("短信发送失败，请稍后再试");
        }
        redis.opsForValue().set("sms:code:" + phone, code, CODE_TTL);
        redis.delete("sms:try:" + phone);
    }

    /** 校验通过即作废这个验证码；不通过抛出业务异常 */
    public void verify(String phone, String code) {
        String key = "sms:code:" + phone;
        String saved = redis.opsForValue().get(key);
        BizException.throwIf(saved == null, "验证码已失效，请重新获取");
        if (!saved.equals(code == null ? "" : code.trim())) {
            long tries = count("sms:try:" + phone, CODE_TTL);
            if (tries >= MAX_TRIES) {
                redis.delete(key);
                throw new BizException("验证码错误次数过多，请重新获取");
            }
            throw new BizException("验证码错误");
        }
        redis.delete(key);
        redis.delete("sms:try:" + phone);
    }

    private void writeLog(String phone, int scene, String ip, SpugSmsSender.Result r) {
        SmsLog row = new SmsLog();
        row.setPhone(phone);
        row.setScene(scene);
        row.setStatus(r.status());
        row.setRequestId(r.requestId());
        row.setError(r.error() == null ? null : r.error().substring(0, Math.min(255, r.error().length())));
        row.setIp(ip);
        logMapper.insert(row);
    }

    /** 计数 +1，首次计数时设置过期时间 */
    private long count(String key, Duration ttl) {
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) {
            redis.expire(key, ttl);
        }
        return n == null ? 0 : n;
    }
}
