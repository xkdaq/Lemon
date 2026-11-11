package com.lemon.biz.user.sms;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短信发送记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`sms_log`")
public class SmsLog extends BaseEntity {

    /** 验证码登录（已下线，保留历史记录的含义） */
    public static final int SCENE_LOGIN = 1;
    /** 登录后设置密码（已下线，保留历史记录的含义） */
    public static final int SCENE_PASSWORD = 2;
    public static final int SCENE_REGISTER = 3;
    public static final int SCENE_RESET = 4;

    public static final int STATUS_FAIL = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_MOCK = 2;

    /** 接收手机号 */
    private String phone;

    /** 1 验证码登录（旧） 2 设置密码（旧） 3 注册 4 找回密码 */
    private Integer scene;

    /** 1 已提交 0 提交失败 2 未实际发送（开发环境） */
    private Integer status;

    /** 短信服务返回的请求 ID */
    private String requestId;

    /** 失败原因 */
    private String error;

    /** 请求 IP */
    private String ip;
}
