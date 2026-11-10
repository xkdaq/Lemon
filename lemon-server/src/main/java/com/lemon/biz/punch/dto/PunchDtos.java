package com.lemon.biz.punch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.lemon.common.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡相关的请求与响应
 */
public final class PunchDtos {

    private PunchDtos() {
    }

    // ==================== 管理端 ====================

    /**
     * 新建 / 修改打卡活动
     *
     * @param restWeekdays 休息日，周一到周日为 1-7
     * @param makeupLimit  每人可补卡次数，0 不允许补卡
     */
    public record CampSave(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String cover,
            @Size(max = 2000) String intro,
            @NotNull Long paperRuleId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 6) List<@NotNull @Min(1) @Max(7) Integer> restWeekdays,
            @Min(0) @Max(365) Integer makeupLimit,
            Integer sort,
            @Min(0) @Max(1) Integer status) {
    }

    /**
     * 打卡活动（管理端）
     *
     * @param todayCount 今天已打卡人数
     */
    public record CampVO(
            Long id, String name, String cover, String intro, Long paperRuleId, String paperRuleName,
            LocalDate startDate, LocalDate endDate, List<Integer> restWeekdays, int makeupLimit, int sort, int status,
            long memberCount, long todayCount,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
    }

    /**
     * 日程里的一天
     *
     * @param weekday    周一到周日为 1-7
     * @param overridden 当天单独设置了组卷规则
     * @param doneCount  已打卡人数
     */
    public record ScheduleDay(LocalDate day, int weekday, boolean rest, Long paperRuleId, String paperRuleName,
                              boolean overridden, long doneCount) {
    }

    /**
     * 批量设置日程
     *
     * @param paperRuleId 为空表示恢复为活动默认规则
     */
    public record ScheduleSet(@NotNull LocalDate from, @NotNull LocalDate to, Long paperRuleId) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MemberQuery extends PageQuery {
        /** 昵称或手机号 */
        private String keyword;
    }

    /**
     * 打卡学员（管理端）
     *
     * @param phone 手机号（中间四位打码）
     */
    public record MemberVO(
            Long userId, String nickname, String phone, long punchDays, LocalDate lastDay, int makeupUsed,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime joinedAt) {
    }

    // ==================== 用户端 ====================

    /**
     * 打卡活动卡片
     *
     * @param joined    已加入（未登录为 false）
     * @param todayDone 今天已打卡
     */
    public record CampCard(Long id, String name, String cover, LocalDate startDate, LocalDate endDate,
                           int questionCount, long memberCount, boolean joined, boolean todayDone) {
    }

    /**
     * 日历上的一天
     *
     * @param state     out 不在活动期内或早于加入日期 / rest 休息日 / future 未到 / todo 今天还没打 /
     *                  doing 做题中 / done 已打卡 / missed 漏打（可补卡）
     * @param sessionId 做过题时的会话 id
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalendarDay(LocalDate day, String state, Long sessionId, Boolean makeup) {
    }

    /**
     * 打卡活动详情（用户端）
     *
     * @param questionCount 每天题数（默认规则）
     * @param today         今天的状态，不在活动期内为 null
     * @param punchDays     已打卡天数
     * @param streakDays    连续打卡天数（休息日不打断）
     * @param makeupLeft    剩余补卡次数
     * @param calendar      所查月份的日历
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampDetail(
            Long id, String name, String cover, String intro, LocalDate startDate, LocalDate endDate,
            List<Integer> restWeekdays, int makeupLimit, int questionCount, long memberCount, long todayCount,
            boolean joined, CalendarDay today, Long punchDays, Integer streakDays, Integer makeupLeft,
            String month, List<CalendarDay> calendar) {
    }

    /**
     * 开始打卡
     *
     * @param day 打卡日期，不传为今天；早于今天为补卡
     */
    public record StartReq(LocalDate day) {
    }

    public record RankItem(int rank, Long userId, String nickname, String avatar, long days) {
    }

    /**
     * 排行榜
     *
     * @param me 我的名次，未登录或没打过卡为 null
     */
    public record RankVO(List<RankItem> list, RankItem me) {
    }
}
