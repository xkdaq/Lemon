package com.lemon.biz.punch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡日程：某一天改用其他组卷规则（只存覆盖了活动默认规则的日期）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`punch_schedule`")
public class PunchSchedule extends BaseEntity {

    private Long campId;

    private LocalDate day;

    /** 当天使用的组卷规则 */
    private Long paperRuleId;
}
