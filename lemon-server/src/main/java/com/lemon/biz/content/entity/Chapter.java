package com.lemon.biz.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 章节；试卷型题库的一个章节即一套卷
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`chapter`")
public class Chapter extends BaseEntity {

    /** 所属题库 */
    private Long bankId;

    /** 所属科目，试卷型为 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long courseId;

    /** 名称 */
    private String name;

    /** 排序 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    /** 试卷型：考试时长（分钟） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer timeLimit;

    /** 试卷型：总分 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer totalScore;

    /** 题量（冗余） */
    private Integer questionCount;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
