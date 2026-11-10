package com.lemon.controller.app;

import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.service.AppAuthService;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public record RegisterReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            @NotBlank @Size(min = 6, max = 32, message = "长度需为 6-32 位") String password,
            @Size(max = 32) String nickname) {
    }

    public record LoginReq(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "格式不正确") String phone,
            @NotBlank String password) {
    }

    public record TokenResp(String token) {
    }

    @Operation(summary = "手机号 + 密码注册（注册后自动登录）")
    @PostMapping("/api/app/auth/register")
    public R<TokenResp> register(@Valid @RequestBody RegisterReq req) {
        return R.ok(new TokenResp(appAuthService.register(req.phone(), req.password(), req.nickname())));
    }

    @Operation(summary = "手机号 + 密码登录")
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

    @Operation(summary = "当前用户信息")
    @GetMapping("/api/app/user/me")
    public R<AppUser> me() {
        return R.ok(appAuthService.current());
    }
}
