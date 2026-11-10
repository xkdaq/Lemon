package com.lemon.biz.punch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡记录：一人一天一条，开始做题时创建，交卷后变为已打卡
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`punch_record`")
public class PunchRecord extends BaseEntity {

    public static final int DOING = 0;
    public static final int DONE = 1;

    private Long campId;

    private Long userId;

    /** 打卡日期 */
    private LocalDate day;

    /** 做题会话 */
    private Long sessionId;

    /** 0 做题中 1 已打卡 */
    private Integer status;

    /** 1 补卡 */
    private Integer makeup;

    /** 题数 */
    private Integer total;

    /** 答对数 */
    private Integer correctCount;

    /** 打卡完成时间 */
    private LocalDateTime finishedAt;
}
