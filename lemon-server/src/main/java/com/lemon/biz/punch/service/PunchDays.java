package com.lemon.biz.punch.service;

import com.lemon.biz.punch.entity.PunchCamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 打卡日期规则：活动期、休息日、抽题种子
 */
final class PunchDays {

    private PunchDays() {
    }

    static List<Integer> restWeekdays(PunchCamp camp) {
        String s = camp.getRestWeekdays();
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split(",")).map(String::strip).filter(x -> !x.isEmpty())
                .map(Integer::valueOf).sorted().toList();
    }

    static String formatRestWeekdays(List<Integer> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return null;
        }
        return weekdays.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    static boolean inRange(PunchCamp camp, LocalDate day) {
        return !day.isBefore(camp.getStartDate()) && !day.isAfter(camp.getEndDate());
    }

    static boolean isRest(List<Integer> restWeekdays, LocalDate day) {
        return restWeekdays.contains(day.getDayOfWeek().getValue());
    }

    /** 抽题种子：同一活动同一天所有人题目相同，方便对答案、排行 */
    static long seed(Long campId, LocalDate day) {
        return Objects.hash(campId, day.toEpochDay()) * 31L + day.toEpochDay();
    }
}
