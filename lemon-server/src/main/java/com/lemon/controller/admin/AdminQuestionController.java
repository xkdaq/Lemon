package com.lemon.controller.admin;

import com.lemon.biz.content.dto.QuestionReqs.BatchIds;
import com.lemon.biz.content.dto.QuestionReqs.BatchMove;
import com.lemon.biz.content.dto.QuestionReqs.BatchStatus;
import com.lemon.biz.content.dto.QuestionReqs.QuestionQuery;
import com.lemon.biz.content.dto.QuestionReqs.QuestionSave;
import com.lemon.biz.content.dto.QuestionVO;
import com.lemon.biz.content.service.QuestionAdminService;
import com.lemon.biz.content.service.QuestionExcelService;
import com.lemon.common.BizException;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "管理端-题目管理")
@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final QuestionAdminService service;
    private final QuestionExcelService excelService;

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static ResponseEntity<byte[]> xlsx(String filename, byte[] body) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @Operation(summary = "下载题目导入模板")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return xlsx("题目导入模板.xlsx", excelService.template());
    }

    @Operation(summary = "Excel 导入题目（全有或全无：有任何一行错误则不导入，返回错误明细）")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<QuestionExcelService.ImportResult> importQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long bankId,
            @RequestParam(required = false) Long chapterId) throws IOException {
        BizException.throwIf(file.isEmpty(), "请选择文件");
        try (InputStream in = file.getInputStream()) {
            return R.ok(excelService.importQuestions(bankId, chapterId, in));
        }
    }

    @Operation(summary = "按当前筛选条件导出题目（最多 5000 题，格式与导入模板一致）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@Valid QuestionQuery query) {
        String name = "题目导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        return xlsx(name, excelService.export(query));
    }

    @Operation(summary = "题目分页（可按模块/题库/科目/章节/题型/状态/题干关键字筛选）")
    @GetMapping
    public R<PageResult<QuestionVO>> page(@Valid QuestionQuery query) {
        return R.ok(service.page(query));
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{id}")
    public R<QuestionVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @Operation(summary = "新建题目（sort 不传则排到章节末尾）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody QuestionSave req) {
        return R.ok(service.create(req));
    }

    @Operation(summary = "修改题目（可改 chapterId 移动到其他章节）")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionSave req) {
        service.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除单题")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(List.of(id));
        return R.ok();
    }

    @Operation(summary = "批量上下架")
    @PutMapping("/batch-status")
    public R<Void> batchStatus(@Valid @RequestBody BatchStatus req) {
        service.updateStatus(req.ids(), req.status());
        return R.ok();
    }

    @Operation(summary = "批量移动到其他章节（追加到目标章节末尾）")
    @PostMapping("/batch-move")
    public R<Void> batchMove(@Valid @RequestBody BatchMove req) {
        service.move(req.ids(), req.targetChapterId());
        return R.ok();
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batch-delete")
    public R<Void> batchDelete(@Valid @RequestBody BatchIds req) {
        service.delete(req.ids());
        return R.ok();
    }
}
