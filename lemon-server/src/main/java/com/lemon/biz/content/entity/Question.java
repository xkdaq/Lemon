package com.lemon.biz.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`question`")
public class Question extends BaseEntity {

    /** 所属章节 */
    private Long chapterId;

    /** 所属题库（冗余） */
    private Long bankId;

    /** 所属模块（冗余） */
    private Long moduleId;

    /** 题型 1 单选 2 多选 3 判断 4 主观，见 QuestionType */
    private Integer type;

    /** 题干（富文本） */
    private String stem;

    /** 选项 JSON：[{"key":"A","content":"..."}] */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String options;

    /** 答案：客观题如 A / ABD */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String answer;

    /** 解析 / 主观题参考答案 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String analysis;

    /** 分值 */
    private BigDecimal score;

    /** 难度 1-3 */
    private Integer difficulty;

    /** 来源，如 2023 真题 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String source;

    /** 排序 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
