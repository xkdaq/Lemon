package com.lemon.biz.content.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题库分类：模块 → 分类 → 题库，如政治下的“强化刷题”“冲刺刷题”“时政”“冲刺背诵”
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`bank_category`")
public class BankCategory extends BaseEntity {

    /** 所属模块 */
    private Long moduleId;

    /** 名称 */
    private String name;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    /** 管理端列表：分类下题库数（非表字段） */
    @TableField(exist = false)
    private Integer bankCount;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
