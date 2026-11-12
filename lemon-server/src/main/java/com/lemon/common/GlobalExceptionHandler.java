package com.lemon.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import com.lemon.biz.notify.FeishuNotifier;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * HTTP 状态码统一返回 200，错误通过 R.code 区分，前端拦截器按 code 处理（401 跳登录）。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final FeishuNotifier notifier;

    @ExceptionHandler(BizException.class)
    public R<Object> handleBiz(BizException e) {
        R<Object> r = R.fail(e.getCode(), e.getMessage());
        r.setData(e.getData());
        return r;
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public R<Void> handleForbidden(Exception e) {
        return R.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMsg());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler({ConstraintViolationException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public R<Void> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMsg());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNotFound(NoResourceFoundException e) {
        return R.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("未处理异常", e);
        // 推送飞书：同一接口同一类异常 10 分钟内只推一次
        try {
            String path = request.getMethod() + " " + request.getRequestURI().replaceAll("/\\d+", "/{id}");
            String msg = e.getMessage() == null ? "" : e.getMessage();
            notifier.sendThrottled("error:" + path + ":" + e.getClass().getName(), Duration.ofMinutes(10),
                    FeishuNotifier.Level.DANGER, "系统异常：用户看到「服务器开小差了」",
                    "**接口**：" + path + "\n**异常**：" + e.getClass().getSimpleName()
                            + "\n**信息**：" + (msg.length() > 300 ? msg.substring(0, 300) + "…" : msg)
                            + "\n同类异常 10 分钟内不重复推送，详细堆栈见服务器日志");
        } catch (Exception ignored) {
            // 推送失败不影响返回
        }
        return R.fail(ErrorCode.SERVER_ERROR);
    }
}
