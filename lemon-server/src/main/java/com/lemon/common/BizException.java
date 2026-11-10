package com.lemon.common;

import lombok.Getter;

/**
 * 业务异常，抛出后由 GlobalExceptionHandler 转成 R.fail 返回。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        this(ErrorCode.BIZ_ERROR.getCode(), msg);
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMsg());
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
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
