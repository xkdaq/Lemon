package com.lemon.biz.stats.service;

import com.lemon.biz.stats.mapper.StatsMapper;
import com.lemon.security.StpAppUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 记录用户活跃日：登录用户每天第一次请求用户端接口时写一条 user_active_day（Redis 去重，每人每天只写一次库）。
 * 用于工作台日活和数据统计里的活跃、留存。失败不影响正常请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityTracker implements HandlerInterceptor {

    private final StringRedisTemplate redis;
    private final StatsMapper mapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            Long userId = StpAppUtil.userIdOrNull();
            if (userId != null) {
                LocalDate today = LocalDate.now();
                Boolean first = redis.opsForValue().setIfAbsent("stat:act:" + today + ":" + userId, "1", Duration.ofDays(2));
                if (Boolean.TRUE.equals(first)) {
                    mapper.markActive(userId, today);
                }
            }
        } catch (Exception e) {
            log.warn("记录活跃失败: {}", e.getMessage());
        }
        return true;
    }
}
