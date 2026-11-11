package com.lemon.controller.app;

import com.lemon.biz.operation.service.FeedbackService;
import com.lemon.biz.operation.service.FeedbackService.FeedbackVO;
import com.lemon.biz.operation.service.FeedbackService.SubmitReq;
import com.lemon.biz.user.service.UserStatsService;
import com.lemon.biz.user.service.UserStatsService.Stats;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.lemon.biz.practice.service.RecordService;
import com.lemon.biz.practice.service.RecordService.BankRecord;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-我的")
@Validated
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppMineController {

    private final UserStatsService statsService;
    private final FeedbackService feedbackService;
    private final RecordService recordService;

    public record ClearReq(@NotNull Long bankId, Boolean includeWrong) {
    }

    @Operation(summary = "有做题记录的题库（做过的题数、错题数）")
    @GetMapping("/user/records")
    public R<List<BankRecord>> records() {
        return R.ok(recordService.banks(StpAppUtil.userId()));
    }

    @Operation(summary = "清除某个题库的做题记录和进度；includeWrong=true 连同该题库的错题")
    @PostMapping("/user/records/clear")
    public R<Void> clearRecords(@Valid @RequestBody ClearReq req) {
        recordService.clear(StpAppUtil.userId(), req.bankId(), Boolean.TRUE.equals(req.includeWrong()));
        return R.ok();
    }

    @Operation(summary = "做题统计")
    @GetMapping("/user/stats")
    public R<Stats> stats() {
        return R.ok(statsService.get(StpAppUtil.userId()));
    }

    @Operation(summary = "提交反馈（type：1 题目纠错，需带 questionId；2 意见建议）")
    @PostMapping("/feedback")
    public R<Long> submit(@Valid @RequestBody SubmitReq req) {
        return R.ok(feedbackService.submit(StpAppUtil.userId(), req));
    }

    @Operation(summary = "我的反馈（含处理回复）")
    @GetMapping("/feedback/mine")
    public R<PageResult<FeedbackVO>> mine(@RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return R.ok(feedbackService.mine(StpAppUtil.userId(), pageNum, pageSize));
    }
}
