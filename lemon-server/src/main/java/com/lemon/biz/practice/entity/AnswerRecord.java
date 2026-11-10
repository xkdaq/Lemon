package com.lemon.biz.practice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 答题明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`answer_record`")
public class AnswerRecord extends BaseEntity {

    /** 用户 */
    private Long userId;

    /** 题目 */
    private Long questionId;

    /** 章节 */
    private Long chapterId;

    /** 所属会话 */
    private Long sessionId;

    /** 用户答案 */
    private String userAnswer;

    /** 1 对 0 错（主观题为自评） */
    private Integer isCorrect;

    /** 用时毫秒 */
    private Integer costMs;
}
