package com.lemon.controller.app;

import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.service.AppAuthService;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-登录注册")
@RestController
@RequiredArgsConstructor
public class AppAuthController {

    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    private final AppAuthService appAuthService;

    public record PhoneReq(@NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone) {
    }

    public record SmsLoginReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            @NotBlank @Size(max = 8) String code) {
    }

    public record SetPasswordReq(
            @NotBlank @Size(max = 8) String code,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password) {
    }

    public record LoginReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            @NotBlank String password) {
    }

    public record TokenResp(String token) {
    }

    @Operation(summary = "发送登录验证码")
    @PostMapping("/api/app/auth/sms-code")
    public R<Void> smsCode(@Valid @RequestBody PhoneReq req, HttpServletRequest request) {
        appAuthService.sendLoginCode(req.phone(), request.getRemoteAddr());
        return R.ok();
    }

    @Operation(summary = "手机号 + 验证码登录（未注册自动注册）")
    @PostMapping("/api/app/auth/sms-login")
    public R<AppAuthService.SmsLoginResult> smsLogin(@Valid @RequestBody SmsLoginReq req) {
        return R.ok(appAuthService.smsLogin(req.phone(), req.code()));
    }

    @Operation(summary = "手机号 + 密码登录（设置过密码的账号）")
    @PostMapping("/api/app/auth/login")
    public R<TokenResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(new TokenResp(appAuthService.login(req.phone(), req.password())));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/api/app/auth/logout")
    public R<Void> logout() {
        StpAppUtil.logout();
        return R.ok();
    }

    @Operation(summary = "给当前账号的手机号发验证码（设置密码用）")
    @PostMapping("/api/app/user/password/code")
    public R<Void> passwordCode(HttpServletRequest request) {
        appAuthService.sendSelfCode(request.getRemoteAddr());
        return R.ok();
    }

    @Operation(summary = "设置 / 修改密码（验证码验证）")
    @PostMapping("/api/app/user/password")
    public R<Void> setPassword(@Valid @RequestBody SetPasswordReq req) {
        appAuthService.setPassword(req.code(), req.password());
        return R.ok();
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/api/app/user/me")
    public R<AppUser> me() {
        return R.ok(appAuthService.current());
    }
}
