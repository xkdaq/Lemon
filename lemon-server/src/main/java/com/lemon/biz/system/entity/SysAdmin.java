package com.lemon.biz.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`sys_admin`")
public class SysAdmin extends BaseEntity {

    /** 登录名 */
    private String username;

    /** BCrypt 密码 */
    @JsonIgnore
    private String password;

    /** 昵称 */
    private String nickname;

    /** 1 正常 0 禁用 */
    private Integer status;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
