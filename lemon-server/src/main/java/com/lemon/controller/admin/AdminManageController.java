package com.lemon.controller.admin;

import com.lemon.biz.system.dto.AdminReqs.AdminCreate;
import com.lemon.biz.system.dto.AdminReqs.AdminQuery;
import com.lemon.biz.system.dto.AdminReqs.AdminUpdate;
import com.lemon.biz.system.dto.AdminReqs.PasswordReset;
import com.lemon.biz.system.dto.AdminVO;
import com.lemon.biz.system.entity.SysRole;
import com.lemon.biz.system.service.AdminManageService;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-管理员账号（仅超级管理员）")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminManageController {

    private final AdminManageService service;

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public R<List<SysRole>> roles() {
        return R.ok(service.roles());
    }

    @Operation(summary = "管理员分页")
    @GetMapping("/admins")
    public R<PageResult<AdminVO>> page(@Valid AdminQuery query) {
        return R.ok(service.page(query));
    }

    @Operation(summary = "新建管理员")
    @PostMapping("/admins")
    public R<Long> create(@Valid @RequestBody AdminCreate req) {
        return R.ok(service.create(req));
    }

    @Operation(summary = "修改管理员（昵称、状态、角色）")
    @PutMapping("/admins/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AdminUpdate req) {
        service.update(id, req);
        return R.ok();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/admins/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordReset req) {
        service.resetPassword(id, req.password());
        return R.ok();
    }

    @Operation(summary = "删除管理员")
    @DeleteMapping("/admins/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
