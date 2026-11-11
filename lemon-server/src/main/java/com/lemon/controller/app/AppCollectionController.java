package com.lemon.controller.app;

import com.lemon.biz.collection.CollectionService;
import com.lemon.biz.collection.CollectionService.Kind;
import com.lemon.biz.practice.dto.PracticeDtos.CollectedQuestion;
import com.lemon.biz.practice.dto.PracticeDtos.Group;
import com.lemon.biz.practice.dto.PracticeDtos.Scope;
import com.lemon.biz.practice.dto.PracticeDtos.SessionVO;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import com.lemon.biz.collection.WrongExportService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-收藏集与错题集")
@Validated
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppCollectionController {

    private final CollectionService service;
    private final WrongExportService exportService;

    /** 发起练习：范围 + 可选的起始题目 */
    public record PracticeReq(Long moduleId, Long bankId, Long courseId, Long chapterId, Long firstQuestionId) {
        Scope scope() {
            return new Scope(moduleId, bankId, courseId, chapterId);
        }
    }

    // -------- 收藏集 --------

    @Operation(summary = "收藏集分组：模块 → 题库 → 章节，带题数")
    @GetMapping("/favorites/groups")
    public R<List<Group>> favoriteGroups() {
        return R.ok(service.groups(StpAppUtil.userId(), Kind.FAVORITE));
    }

    @Operation(summary = "收藏列表（按收藏时间倒序）")
    @GetMapping("/favorites")
    public R<PageResult<CollectedQuestion>> favorites(
            @RequestParam(required = false) Long moduleId, @RequestParam(required = false) Long bankId,
            @RequestParam(required = false) Long courseId, @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return R.ok(service.list(StpAppUtil.userId(), Kind.FAVORITE, new Scope(moduleId, bankId, courseId, chapterId), pageNum, pageSize));
    }

    @Operation(summary = "收藏练习：按范围新建练习会话")
    @PostMapping("/favorites/practice")
    public R<SessionVO> favoritePractice(@RequestBody PracticeReq req) {
        return R.ok(service.practice(StpAppUtil.userId(), Kind.FAVORITE, req.scope(), req.firstQuestionId()));
    }

    // -------- 错题集 --------

    @Operation(summary = "错题集分组：模块 → 题库 → 章节，带题数")
    @GetMapping("/wrongs/groups")
    public R<List<Group>> wrongGroups() {
        return R.ok(service.groups(StpAppUtil.userId(), Kind.WRONG));
    }

    @Operation(summary = "错题列表（按最近答错时间倒序）")
    @GetMapping("/wrongs")
    public R<PageResult<CollectedQuestion>> wrongs(
            @RequestParam(required = false) Long moduleId, @RequestParam(required = false) Long bankId,
            @RequestParam(required = false) Long courseId, @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return R.ok(service.list(StpAppUtil.userId(), Kind.WRONG, new Scope(moduleId, bankId, courseId, chapterId), pageNum, pageSize));
    }

    @Operation(summary = "错题导出 Word（按题库）；answers=end 答案解析放在文末，否则跟在每题后面")
    @GetMapping("/wrongs/export")
    public ResponseEntity<byte[]> exportWrongs(@RequestParam Long bankId, @RequestParam(defaultValue = "inline") String answers) {
        WrongExportService.ExportFile f = exportService.export(StpAppUtil.userId(), bankId, "end".equals(answers));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.filename(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    @Operation(summary = "手动移出错题集")
    @DeleteMapping("/wrongs/{questionId}")
    public R<Void> removeWrong(@PathVariable Long questionId) {
        service.removeWrong(StpAppUtil.userId(), questionId);
        return R.ok();
    }

    @Operation(summary = "错题重练：按范围新建练习会话（答对 N 次自动移出）")
    @PostMapping("/wrongs/practice")
    public R<SessionVO> wrongPractice(@RequestBody PracticeReq req) {
        return R.ok(service.practice(StpAppUtil.userId(), Kind.WRONG, req.scope(), req.firstQuestionId()));
    }
}
