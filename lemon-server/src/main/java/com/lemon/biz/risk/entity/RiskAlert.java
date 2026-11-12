package com.lemon.biz.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.common.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 风险告警（防偷题）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`risk_alert`")
public class RiskAlert extends BaseEntity {

    public static final String HOUR_QUESTIONS = "hour_questions";
    public static final String DAY_QUESTIONS = "day_questions";
    public static final String HOUR_SESSIONS = "hour_sessions";

    private Long userId;

    private String alertType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate alertDate;

    private Integer metric;

    private Integer threshold;

    private String ip;

    /** 0 待处理 1 已处理 */
    private Integer status;

    private Long handledBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handledAt;

    private String remark;
}
