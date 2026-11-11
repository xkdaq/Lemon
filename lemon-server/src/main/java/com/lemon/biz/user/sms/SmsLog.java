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

    public static final int SCENE_LOGIN = 1;
    public static final int SCENE_PASSWORD = 2;

    public static final int STATUS_FAIL = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_MOCK = 2;

    /** 接收手机号 */
    private String phone;

    /** 1 登录 2 设置密码 */
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
