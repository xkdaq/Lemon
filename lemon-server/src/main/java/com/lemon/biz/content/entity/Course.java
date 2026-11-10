package com.lemon.biz.content.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科目（仅科目型题库，如马原、毛中特）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`course`")
public class Course extends BaseEntity {

    /** 所属题库 */
    private Long bankId;

    /** 名称 */
    private String name;

    /** 排序 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
