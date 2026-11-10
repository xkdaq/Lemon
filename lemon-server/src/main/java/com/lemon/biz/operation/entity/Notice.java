package com.lemon.biz.operation.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公告
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`notice`")
public class Notice extends BaseEntity {

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 排序 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
