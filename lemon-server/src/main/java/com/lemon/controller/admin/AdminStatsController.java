package com.lemon.controller.admin;

import com.lemon.biz.stats.service.StatsService;
import com.lemon.biz.stats.service.StatsService.Content;
import com.lemon.biz.stats.service.StatsService.DayStat;
import com.lemon.biz.stats.service.StatsService.Retention;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-数据统计")
@Validated
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService service;

    @Operation(summary = "每日趋势：新增、活跃、答题、做题人数、正确率、激活、笔记")
    @GetMapping("/trend")
    public R<List<DayStat>> trend(@RequestParam(defaultValue = "30") @Min(1) @Max(180) int days) {
        return R.ok(service.trend(days));
    }

    @Operation(summary = "新用户留存（按注册日）")
    @GetMapping("/retention")
    public R<List<Retention>> retention(@RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return R.ok(service.retention(days));
    }

    @Operation(summary = "内容热度：热门题库、热门章节、错误率最高的题")
    @GetMapping("/content")
    public R<Content> content(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return R.ok(service.content(days));
    }
}
