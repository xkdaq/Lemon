package com.lemon.biz.operation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反馈 / 题目纠错
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`feedback`")
public class Feedback extends BaseEntity {

    /** 用户 */
    private Long userId;

    /** 纠错题目 */
    private Long questionId;

    /** 1 题目纠错 2 意见建议 */
    private Integer type;

    /** 内容 */
    private String content;

    /** 图片 JSON */
    private String images;

    /** 0 待处理 1 已处理 */
    private Integer status;

    /** 回复 */
    private String reply;
}
