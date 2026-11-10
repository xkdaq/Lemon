package com.lemon.controller.app;

import com.lemon.biz.favorite.service.FavoriteService;
import com.lemon.biz.practice.dto.PracticeDtos.AnswerReq;
import com.lemon.biz.practice.dto.PracticeDtos.AnswerResult;
import com.lemon.biz.practice.dto.PracticeDtos.ProgressReq;
import com.lemon.biz.practice.dto.PracticeDtos.QuestionItem;
import com.lemon.biz.practice.dto.PracticeDtos.ResultVO;
import com.lemon.biz.practice.dto.PracticeDtos.SessionVO;
import com.lemon.biz.practice.dto.PracticeDtos.StartReq;
import com.lemon.biz.practice.service.PracticeService;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-做题")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppPracticeController {

    private final PracticeService practiceService;
    private final FavoriteService favoriteService;

    @Operation(summary = "开始做题（mode：1 练习 2 背题 3 考试）；有未完成的会话则续做，restart=true 重新开始")
    @PostMapping("/sessions")
    public R<SessionVO> start(@Valid @RequestBody StartReq req) {
        return R.ok(practiceService.start(StpAppUtil.userId(), req));
    }

    @Operation(summary = "会话详情（考试超时会自动交卷）")
    @GetMapping("/sessions/{id}")
    public R<SessionVO> get(@PathVariable Long id) {
        return R.ok(practiceService.get(StpAppUtil.userId(), id));
    }

    @Operation(summary = "批量获取题目详情（ids 逗号分隔，最多 50 个）；答案按模式和作答状态决定是否返回")
    @GetMapping("/sessions/{id}/questions")
    public R<List<QuestionItem>> questions(@PathVariable Long id, @RequestParam List<Long> ids) {
        return R.ok(practiceService.questions(StpAppUtil.userId(), id, ids));
    }

    @Operation(summary = "提交一题：练习模式立即判题并返回答案解析；考试模式只保存草稿")
    @PostMapping("/sessions/{id}/answers")
    public R<AnswerResult> answer(@PathVariable Long id, @Valid @RequestBody AnswerReq req) {
        return R.ok(practiceService.answer(StpAppUtil.userId(), id, req));
    }

    @Operation(summary = "保存做到第几题（续做用）")
    @PutMapping("/sessions/{id}/progress")
    public R<Void> progress(@PathVariable Long id, @Valid @RequestBody ProgressReq req) {
        practiceService.saveProgress(StpAppUtil.userId(), id, req.currentIndex());
        return R.ok();
    }

    @Operation(summary = "交卷 / 结束练习，返回结果")
    @PostMapping("/sessions/{id}/submit")
    public R<ResultVO> submit(@PathVariable Long id) {
        return R.ok(practiceService.submit(StpAppUtil.userId(), id));
    }

    @Operation(summary = "查看结果（已交卷的会话）")
    @GetMapping("/sessions/{id}/result")
    public R<ResultVO> result(@PathVariable Long id) {
        return R.ok(practiceService.result(StpAppUtil.userId(), id));
    }

    @Operation(summary = "收藏题目")
    @PostMapping("/favorites/{questionId}")
    public R<Void> favorite(@PathVariable Long questionId) {
        favoriteService.add(StpAppUtil.userId(), questionId);
        return R.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorites/{questionId}")
    public R<Void> unfavorite(@PathVariable Long questionId) {
        favoriteService.remove(StpAppUtil.userId(), questionId);
        return R.ok();
    }
}
