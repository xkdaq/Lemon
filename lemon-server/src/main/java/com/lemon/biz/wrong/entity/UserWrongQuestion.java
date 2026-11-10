package com.lemon.biz.wrong.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.common.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 错题集
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user_wrong_question`")
public class UserWrongQuestion extends BaseEntity {

    /** 用户 */
    private Long userId;

    /** 题目 */
    private Long questionId;

    /** 模块（冗余） */
    private Long moduleId;

    /** 题库（冗余） */
    private Long bankId;

    /** 章节（冗余） */
    private Long chapterId;

    /** 累计答错次数 */
    private Integer wrongCount;

    /** 连续答对次数，达到阈值自动移出 */
    private Integer rightStreak;

    /** 最近一次错误答案 */
    private String lastWrongAnswer;

    /** 最近答错时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastWrongAt;

    /** 1 在错题集 0 已移出 */
    private Integer status;
}
