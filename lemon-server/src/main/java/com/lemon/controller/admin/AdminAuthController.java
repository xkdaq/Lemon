package com.lemon.controller.admin;

import com.lemon.biz.system.dto.AdminReqs.PasswordChange;
import com.lemon.biz.system.dto.AdminVO;
import com.lemon.biz.system.service.AdminAuthService;
import com.lemon.common.R;
import com.lemon.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-登录")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public record LoginReq(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResp(String token) {
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(new LoginResp(adminAuthService.login(req.username(), req.password())));
    }

    @Operation(summary = "当前管理员信息（含角色）")
    @GetMapping("/info")
    public R<AdminVO> info() {
        return R.ok(adminAuthService.current());
    }

    @Operation(summary = "修改自己的密码")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordChange req) {
        adminAuthService.changePassword(req.oldPassword(), req.newPassword());
        return R.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout() {
        StpAdminUtil.logout();
        return R.ok();
    }
}
