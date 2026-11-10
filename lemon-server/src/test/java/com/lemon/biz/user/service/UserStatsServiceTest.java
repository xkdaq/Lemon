package com.lemon.biz.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserStatsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static LocalDate daysAgo(int n) {
        return TODAY.minusDays(n);
    }

    @Test
    void noRecords() {
        assertThat(UserStatsService.streakDays(List.of(), TODAY)).isZero();
    }

    @Test
    void consecutiveIncludingToday() {
        assertThat(UserStatsService.streakDays(List.of(daysAgo(0), daysAgo(1), daysAgo(2), daysAgo(4)), TODAY)).isEqualTo(3);
    }

    @Test
    void todayNotYetButYesterdayCounts() {
        // 今天还没做题，昨天之前连续 2 天：仍算 2 天，不清零
        assertThat(UserStatsService.streakDays(List.of(daysAgo(1), daysAgo(2)), TODAY)).isEqualTo(2);
    }

    @Test
    void brokenStreak() {
        assertThat(UserStatsService.streakDays(List.of(daysAgo(2), daysAgo(3)), TODAY)).isZero();
    }

    @Test
    void onlyToday() {
        assertThat(UserStatsService.streakDays(List.of(daysAgo(0)), TODAY)).isEqualTo(1);
    }
}
