package com.lemon.controller.admin;

import com.lemon.biz.risk.service.RiskAlertAdminService;
import com.lemon.biz.risk.service.RiskAlertAdminService.AlertVO;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import com.lemon.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-风险告警")
@RestController
@RequestMapping("/api/admin/risk-alerts")
@RequiredArgsConstructor
public class AdminRiskController {

    private final RiskAlertAdminService service;

    public record HandleReq(@Size(max = 255) String remark) {
    }

    @Operation(summary = "风险告警列表（待处理在前）")
    @GetMapping
    public R<PageResult<AlertVO>> page(@Valid RiskAlertAdminService.Query query) {
        return R.ok(service.page(query));
    }

    @Operation(summary = "待处理告警数")
    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        return R.ok(service.pendingCount());
    }

    @Operation(summary = "标记已处理")
    @PutMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody HandleReq req) {
        service.handle(StpAdminUtil.adminId(), id, req.remark());
        return R.ok();
    }
}
