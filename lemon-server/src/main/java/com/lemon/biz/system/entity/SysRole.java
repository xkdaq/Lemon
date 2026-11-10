package com.lemon.biz.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`sys_role`")
public class SysRole extends BaseEntity {

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 1 正常 0 禁用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
