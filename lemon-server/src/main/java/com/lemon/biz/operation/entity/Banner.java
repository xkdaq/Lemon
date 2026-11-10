package com.lemon.biz.operation.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 首页 Banner
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`banner`")
public class Banner extends BaseEntity {

    /** 投放模块，null 为首页 */
    private Long moduleId;

    /** 图片 */
    private String image;

    /** 跳转链接 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String link;

    /** 排序 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
