package com.lemon.common;

import lombok.Getter;

/**
 * 业务异常，抛出后由 GlobalExceptionHandler 转成 R.fail 返回。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 附带给前端的数据（如需要激活的模块），一般为空 */
    private final transient Object data;

    public BizException(String msg) {
        this(ErrorCode.BIZ_ERROR.getCode(), msg);
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMsg());
    }

    public BizException(int code, String msg) {
        this(code, msg, null);
    }

    public BizException(int code, String msg, Object data) {
        super(msg);
        this.code = code;
        this.data = data;
    }

    public static void throwIf(boolean condition, String msg) {
        if (condition) {
            throw new BizException(msg);
        }
    }

    public static <T> T notNull(T obj, String msg) {
        if (obj == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), msg);
        }
        return obj;
    }
}
