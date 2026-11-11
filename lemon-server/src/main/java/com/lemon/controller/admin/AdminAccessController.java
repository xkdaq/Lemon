package com.lemon.controller.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.access.service.AccessAdminService;
import com.lemon.biz.access.service.AccessAdminService.CodeQuery;
import com.lemon.biz.access.service.ActivationService;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import com.lemon.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 激活码管理、用户模块权限。仅超级管理员（WebConfig 路由鉴权）。
 */
@Tag(name = "管理端-激活码")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAccessController {

    private final ActivationService activationService;
    private final AccessAdminService adminService;

    public record GenerateReq(
            @NotNull Long moduleId,
            @NotNull @Min(1) @Max(1000) Integer count,
            /** 1 激活后有效 N 天 2 到固定日期为止 */
            @NotNull Integer validityType,
            Integer validDays,
            LocalDate expireDate,
            @Size(max = 64) String batchName) {
    }

    public record GenerateResp(List<String> codes) {
    }

    public record GrantReq(@NotNull Long moduleId, Integer days, LocalDate until, @Size(max = 255) String remark) {
    }

    public record RevokeReq(@NotNull Long moduleId, @Size(max = 255) String remark) {
    }

    public record GrantResp(@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expireAt) {
    }

    @Operation(summary = "激活码列表（关键字：激活码 / 使用者手机号 / 用户名）")
    @GetMapping("/activation-codes")
    public R<PageResult<AccessAdminService.CodeVO>> codes(@Valid CodeQuery query) {
        return R.ok(adminService.pageCodes(query));
    }

    @Operation(summary = "已有的批次名")
    @GetMapping("/activation-codes/batches")
    public R<List<String>> batches() {
        return R.ok(adminService.batchNames());
    }

    @Operation(summary = "导出激活码 CSV（按当前筛选条件）")
    @GetMapping("/activation-codes/export")
    public ResponseEntity<byte[]> export(@Valid CodeQuery query) {
        String name = "激活码-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(adminService.exportCodes(query));
    }

    @Operation(summary = "批量生成激活码，返回生成的码（XXXX-XXXX-XXXX）")
    @PostMapping("/activation-codes")
    public R<GenerateResp> generate(@Valid @RequestBody GenerateReq req) {
        List<String> codes = activationService.generate(StpAdminUtil.adminId(), req.moduleId(), req.count(),
                req.validityType(), req.validDays(), req.expireDate(), req.batchName());
        return R.ok(new GenerateResp(codes.stream().map(ActivationService::format).toList()));
    }

    @Operation(summary = "作废激活码（只能作废未使用的）")
    @PostMapping("/activation-codes/{id}/void")
    public R<Void> voidCode(@PathVariable Long id) {
        activationService.voidCode(id);
        return R.ok();
    }

    @Operation(summary = "用户的模块权限和变更记录")
    @GetMapping("/app-users/{id}/access")
    public R<AccessAdminService.UserAccess> userAccess(@PathVariable Long id) {
        return R.ok(adminService.userAccess(id));
    }

    @Operation(summary = "给用户开通 / 延长模块（days 或 until 二选一）")
    @PostMapping("/app-users/{id}/access/grant")
    public R<GrantResp> grant(@PathVariable Long id, @Valid @RequestBody GrantReq req) {
        return R.ok(new GrantResp(activationService.grant(StpAdminUtil.adminId(), id, req.moduleId(), req.days(),
                req.until(), req.remark())));
    }

    @Operation(summary = "取消用户的模块权限（立即到期）")
    @PostMapping("/app-users/{id}/access/revoke")
    public R<Void> revoke(@PathVariable Long id, @Valid @RequestBody RevokeReq req) {
        activationService.revoke(StpAdminUtil.adminId(), id, req.moduleId(), req.remark());
        return R.ok();
    }
}
