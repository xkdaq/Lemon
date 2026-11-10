package com.lemon.biz.punch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡学员（加入时间即 createdAt）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`punch_member`")
public class PunchMember extends BaseEntity {

    private Long campId;

    private Long userId;

    /** 已用补卡次数 */
    private Integer makeupUsed;
}
