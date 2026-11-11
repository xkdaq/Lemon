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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-登录注册")
@RestController
@RequiredArgsConstructor
public class AppAuthController {

    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    private final AppAuthService appAuthService;

    public record CodeReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            /** register 注册 / reset 找回密码 */
            @NotBlank @Pattern(regexp = "^(register|reset)$", message = "不正确") String purpose) {
    }

    public record RegisterReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            @NotBlank @Size(max = 8) String code,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password,
            @Size(max = 32) String nickname) {
    }

    public record ResetReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            @NotBlank @Size(max = 8) String code,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password) {
    }

    public record LoginReq(
            /** 手机号或用户名 */
            @NotBlank @Size(max = 32) String account,
            @NotBlank String password) {
    }

    public record ChangePasswordReq(
            /** 原密码；还没设置过密码的账号不用传 */
            String oldPassword,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password) {
    }

    public record NicknameReq(@NotBlank @Size(max = 32, message = "最多 32 个字") String nickname) {
    }

    public record TokenResp(String token) {
    }

    @Operation(summary = "发送短信验证码（注册 / 找回密码）")
    @PostMapping("/api/app/auth/sms-code")
    public R<Void> smsCode(@Valid @RequestBody CodeReq req, HttpServletRequest request) {
        appAuthService.sendCode(req.phone(), req.purpose(), request.getRemoteAddr());
        return R.ok();
    }

    @Operation(summary = "注册：手机号 + 验证码 + 密码（注册后自动登录）")
    @PostMapping("/api/app/auth/register")
    public R<TokenResp> register(@Valid @RequestBody RegisterReq req) {
        return R.ok(new TokenResp(appAuthService.register(req.phone(), req.code(), req.password(), req.nickname())));
    }

    @Operation(summary = "找回密码：手机号 + 验证码 + 新密码（重置后自动登录，其他设备下线）")
    @PostMapping("/api/app/auth/reset-password")
    public R<TokenResp> resetPassword(@Valid @RequestBody ResetReq req) {
        return R.ok(new TokenResp(appAuthService.resetPassword(req.phone(), req.code(), req.password())));
    }

    @Operation(summary = "账号密码登录（账号 = 手机号或用户名）")
    @PostMapping("/api/app/auth/login")
    public R<TokenResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(new TokenResp(appAuthService.login(req.account(), req.password())));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/api/app/auth/logout")
    public R<Void> logout() {
        StpAppUtil.logout();
        return R.ok();
    }

    @Operation(summary = "设置 / 修改密码（已有密码需验证原密码）")
    @PostMapping("/api/app/user/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        appAuthService.changePassword(req.oldPassword(), req.password());
        return R.ok();
    }

    @Operation(summary = "修改昵称")
    @PutMapping("/api/app/user/nickname")
    public R<Void> nickname(@Valid @RequestBody NicknameReq req) {
        appAuthService.updateNickname(req.nickname());
        return R.ok();
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/api/app/user/me")
    public R<AppUser> me() {
        return R.ok(appAuthService.current());
    }
}
