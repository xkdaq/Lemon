package com.lemon.biz.user.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户端用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`app_user`")
public class AppUser extends BaseEntity {

    /** 手机号，跨端合并账号的依据 */
    private String phone;

    /** BCrypt 密码 */
    @JsonIgnore
    private String password;

    /** 小程序 openid（二期） */
    private String openid;

    /** 微信 unionid（二期） */
    private String unionid;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 1 Web 2 小程序 */
    private Integer registerFrom;

    /** 1 正常 0 禁用 */
    private Integer status;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
