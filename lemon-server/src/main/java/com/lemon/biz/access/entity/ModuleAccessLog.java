package com.lemon.biz.access.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.common.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模块权限变更记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`module_access_log`")
public class ModuleAccessLog extends BaseEntity {

    public static final int SOURCE_CODE = 1;
    public static final int SOURCE_ADMIN_GRANT = 2;
    public static final int SOURCE_ADMIN_REVOKE = 3;
    public static final int SOURCE_LEGACY = 4;

    private Long userId;

    private Long moduleId;

    /** 1 激活码 2 后台开通 / 延长 3 后台取消 4 恢复老系统错题赠送 */
    private Integer source;

    private Long codeId;

    private Long adminId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beforeExpire;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime afterExpire;

    private String remark;
}
