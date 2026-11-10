package com.lemon.biz.operation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统参数（key-value）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`sys_config`")
public class SysConfig extends BaseEntity {

    /** 键 */
    private String configKey;

    /** 值 */
    private String configValue;

    /** 说明 */
    private String remark;
}
