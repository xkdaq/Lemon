package com.lemon.biz.recite.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 背诵资料（如 27肖八选择题）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`recite_book`")
public class ReciteBook extends BaseEntity {

    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String intro;

    /** 套列表 JSON：[{"no":1,"name":"第一套"}] */
    private String setList;

    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    private Integer itemCount;

    /** 导入时对照的题库（只读引用） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long linkBankId;

    private Integer linkedCount;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
