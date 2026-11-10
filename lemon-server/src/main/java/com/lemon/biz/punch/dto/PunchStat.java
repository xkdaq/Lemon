package com.lemon.biz.punch.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 打卡统计查询结果（按需使用其中的字段）
 */
@Data
public class PunchStat {
    private Long campId;
    private Long userId;
    private LocalDate day;
    /** 计数：打卡人数 / 打卡天数 */
    private Long cnt;
    /** 最近一次打卡的日期 */
    private LocalDate lastDay;
    /** 最近一次打卡完成时间（排行榜同天数时先完成的靠前） */
    private LocalDateTime lastFinishedAt;
}
