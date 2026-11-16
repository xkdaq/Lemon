package com.lemon.biz.recite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * “错误说法”已掌握标记：按 资料 + 套 + 题号 + 第几条 记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`recite_trap_mark`")
public class ReciteTrapMark extends BaseEntity {

    private Long userId;

    private Long bookId;

    private Integer setNo;

    private Integer no;

    private Integer idx;
}
