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
 * 模块（政治 / 教育学）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`module`")
public class ExamModule extends BaseEntity {

    /** 名称 */
    private String name;

    /** 图标 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String icon;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
