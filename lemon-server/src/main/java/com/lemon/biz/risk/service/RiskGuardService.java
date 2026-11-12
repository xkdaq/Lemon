package com.lemon.biz.risk.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.notify.FeishuNotifier;
import com.lemon.biz.operation.service.SysConfigService;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.biz.risk.entity.RiskAlert;
import com.lemon.biz.risk.mapper.RiskAlertMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 防偷题监控：按账号统计 1 小时 / 1 天内看过的不同题目数（Redis HyperLogLog），以及 1 小时内开始练习的次数；
 * 超过阈值（系统参数 risk.*）时写一条告警，后台「风险告警」处理，同时打 WARN 日志。
 * <p>
 * 只记录、不拦截，避免误伤正常刷题的用户；确认是偷题后由管理员禁用账号。
 * 统计失败不影响用户正常做题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskGuardService {

    public static final String HOUR_QUESTIONS = "risk.hour_questions";
    public static final String DAY_QUESTIONS = "risk.day_questions";
    public static final String HOUR_SESSIONS = "risk.hour_sessions";
    /** 已告警后，数值最多每 10 分钟更新一次 */
    private static final Duration UPDATE_INTERVAL = Duration.ofMinutes(10);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;
    private final RiskAlertMapper mapper;
    private final SysConfigService configService;
    private final FeishuNotifier notifier;
    private final AppUserMapper userMapper;

    /** 用户看到了这些题目（拉题目详情、看笔记详情） */
    public void onQuestionsViewed(Long userId, Collection<Long> questionIds) {
        if (userId == null || questionIds == null || questionIds.isEmpty()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            String[] ids = questionIds.stream().map(String::valueOf).toArray(String[]::new);
            String hourKey = "risk:q:h:" + userId + ":" + now.format(HOUR);
            String dayKey = "risk:q:d:" + userId + ":" + now.format(DAY);
            redis.opsForHyperLogLog().add(hourKey, ids);
            redis.opsForHyperLogLog().add(dayKey, ids);
            redis.expire(hourKey, Duration.ofHours(2));
            redis.expire(dayKey, Duration.ofDays(2));
            check(userId, RiskAlert.HOUR_QUESTIONS, HOUR_QUESTIONS, 600, redis.opsForHyperLogLog().size(hourKey));
            check(userId, RiskAlert.DAY_QUESTIONS, DAY_QUESTIONS, 2000, redis.opsForHyperLogLog().size(dayKey));
        } catch (Exception e) {
            log.warn("风险统计失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 用户开始了一次练习 / 考试 */
    public void onSessionStarted(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = "risk:s:h:" + userId + ":" + LocalDateTime.now().format(HOUR);
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1) {
                redis.expire(key, Duration.ofHours(2));
            }
            check(userId, RiskAlert.HOUR_SESSIONS, HOUR_SESSIONS, 60, n == null ? 0 : n);
        } catch (Exception e) {
            log.warn("风险统计失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private void check(Long userId, String type, String configKey, int defaultThreshold, long value) {
        int threshold = configService.getInt(configKey, defaultThreshold);
        if (threshold <= 0 || value <= threshold) {
            return;
        }
        LocalDate today = LocalDate.now();
        // 已经告警过：最多每 10 分钟把数值更新为最新的
        Boolean first = redis.opsForValue().setIfAbsent(
                "risk:alerted:" + type + ":" + userId + ":" + today + ":tick", "1", UPDATE_INTERVAL);
        if (!Boolean.TRUE.equals(first)) {
            return;
        }
        String ip = clientIp();
        RiskAlert existing = mapper.selectOne(Wrappers.<RiskAlert>lambdaQuery()
                .eq(RiskAlert::getUserId, userId).eq(RiskAlert::getAlertType, type).eq(RiskAlert::getAlertDate, today));
        if (existing == null) {
            RiskAlert a = new RiskAlert();
            a.setUserId(userId);
            a.setAlertType(type);
            a.setAlertDate(today);
            a.setMetric((int) value);
            a.setThreshold(threshold);
            a.setIp(ip);
            a.setStatus(0);
            try {
                mapper.insert(a);
                log.warn("[风险告警] userId={} type={} value={} threshold={} ip={}", userId, type, value, threshold, ip);
                pushAlert(userId, type, value, threshold, ip);
            } catch (DuplicateKeyException ignored) {
                // 并发：另一个请求已经记录
            }
        } else if (value > existing.getMetric()) {
            existing.setMetric((int) value);
            existing.setIp(ip);
            mapper.updateById(existing);
        }
    }

    private static final java.util.Map<String, String> TYPE_LABELS = java.util.Map.of(
            RiskAlert.HOUR_QUESTIONS, "1 小时内看题过多",
            RiskAlert.DAY_QUESTIONS, "1 天内看题过多",
            RiskAlert.HOUR_SESSIONS, "1 小时内开练习过多");

    private void pushAlert(Long userId, String type, long value, int threshold, String ip) {
        AppUser u = userMapper.selectById(userId);
        String who = u == null ? "用户 #" + userId
                : u.getNickname() + "（" + u.getUsername() + (u.getPhone() == null ? "" : "，" + maskPhone(u.getPhone())) + "）";
        notifier.send(FeishuNotifier.Level.WARN, "风险告警：疑似批量获取题目",
                "**类型**：" + TYPE_LABELS.getOrDefault(type, type)
                        + "\n**用户**：" + who
                        + "\n**数值 / 阈值**：" + value + " / " + threshold
                        + (ip == null ? "" : "\n**IP**：" + ip)
                        + "\n请到后台「运营 → 风险告警」查看处理");
    }

    static String maskPhone(String phone) {
        return phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : phone;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
