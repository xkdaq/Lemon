package com.lemon.controller.app;

import com.lemon.biz.legacy.LegacyRestoreService;
import com.lemon.biz.legacy.LegacyRestoreService.Preview;
import com.lemon.biz.legacy.LegacyRestoreService.Result;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 激活码 / 订单号放在请求体里，不放 URL，避免出现在访问日志
 */
@Tag(name = "用户端-老系统错题恢复")
@RestController
@RequestMapping("/api/app/legacy")
@RequiredArgsConstructor
public class AppLegacyController {

    private final LegacyRestoreService service;

    public record CodeReq(@NotBlank @Size(max = 64) String code) {
    }

    @Operation(summary = "按激活码或订单号查老账号的错题情况")
    @PostMapping("/lookup")
    public R<Preview> lookup(@Valid @RequestBody CodeReq req) {
        return R.ok(service.lookup(StpAppUtil.userId(), req.code()));
    }

    @Operation(summary = "恢复老错题到错题集（一个老账号只能恢复一次）")
    @PostMapping("/claim")
    public R<Result> claim(@Valid @RequestBody CodeReq req) {
        return R.ok(service.claim(StpAppUtil.userId(), req.code()));
    }
}
