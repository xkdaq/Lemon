package com.lemon.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    OK(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    BIZ_ERROR(1000, "业务处理失败"),
    /** 模块未激活：data = { moduleId, moduleName }，前端引导去激活 */
    NEED_ACTIVATION(1010, "该模块需要激活后才能使用"),
    SERVER_ERROR(500, "服务器开小差了，请稍后再试");

    private final int code;
    private final String msg;
}
