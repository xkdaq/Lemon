package com.lemon.biz.access.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.common.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 激活码：一个码激活一个模块，只能用一次
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`activation_code`")
public class ActivationCode extends BaseEntity {

    public static final int TYPE_DAYS = 1;
    public static final int TYPE_DATE = 2;

    public static final int UNUSED = 0;
    public static final int USED = 1;
    public static final int VOIDED = 2;

    /** 激活码（大写，不含分隔符） */
    private String code;

    private Long moduleId;

    /** 批次 / 渠道备注 */
    private String batchName;

    /** 1 激活后有效 N 天 2 到固定日期为止 */
    private Integer validityType;

    /** validityType=1：激活后有效天数 */
    private Integer validDays;

    /** validityType=2：到期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;

    /** 0 未使用 1 已使用 2 已作废 */
    private Integer status;

    private Long usedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedAt;

    /** 使用后该模块的到期时间（含顺延） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime accessUntil;

    private Long createdBy;
}
