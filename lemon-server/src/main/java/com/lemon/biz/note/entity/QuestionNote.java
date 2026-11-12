package com.lemon.biz.note.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目笔记（私密）：每人每题一条
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`question_note`")
public class QuestionNote extends BaseEntity {

    private Long userId;

    private Long questionId;

    /** 模块 / 题库 / 章节（冗余） */
    private Long moduleId;

    private Long bankId;

    private Long chapterId;

    /** 纯文本 */
    private String content;

    /** 图片 URL 的 JSON 数组 */
    private String images;
}
