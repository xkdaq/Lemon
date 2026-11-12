package com.lemon.controller.admin;

import com.lemon.biz.notify.DailyReportService;
import com.lemon.biz.notify.FeishuNotifier;
import com.lemon.common.BizException;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-飞书推送")
@RestController
@RequestMapping("/api/admin/notify")
@RequiredArgsConstructor
public class AdminNotifyController {

    private final FeishuNotifier notifier;
    private final DailyReportService reportService;

    @Operation(summary = "发送一条测试消息到飞书")
    @PostMapping("/test")
    public R<Void> test() {
        String err = notifier.sendNow(FeishuNotifier.Level.OK, "Lemon 刷题：飞书推送测试",
                "收到这条消息说明配置正确。之后重要告警和每天 9 点的日报会推送到这个群。");
        BizException.throwIf(err != null, err);
        return R.ok();
    }

    @Operation(summary = "立即推送某一天的日报（默认昨天）")
    @PostMapping("/daily-report")
    public R<Void> dailyReport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        BizException.throwIf(!day.isBefore(LocalDate.now()), "只能推送今天之前的日报");
        BizException.throwIf(day.isBefore(LocalDate.now().minusDays(80)), "最多推送 80 天内的日报");
        String err = reportService.sendNow(day);
        BizException.throwIf(err != null, err);
        return R.ok();
    }
}
