package com.lemon.biz.access.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.common.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户的模块权限：到期时间之前可以刷该模块下所有题库
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user_module_access`")
public class UserModuleAccess extends BaseEntity {

    private Long userId;

    private Long moduleId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;
}
