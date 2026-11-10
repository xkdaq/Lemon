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
 * 题库
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`question_bank`")
public class QuestionBank extends BaseEntity {

    /** 所属模块 */
    private Long moduleId;

    /** 所属分类，null 为未分类 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;

    /** 名称 */
    private String name;

    /** 1 科目型 2 试卷型，见 BankType */
    private Integer type;

    /** 封面 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cover;

    /** 简介 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String intro;

    /** 排序 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    /** 题量（冗余） */
    private Integer questionCount;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
