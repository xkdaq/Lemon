package com.lemon.controller.app;

import com.lemon.biz.note.service.NoteService;
import com.lemon.biz.note.service.NoteService.NoteDetail;
import com.lemon.biz.note.service.NoteService.NoteItem;
import com.lemon.biz.note.service.NoteService.NoteVO;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
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

@Tag(name = "用户端-题目笔记")
@Validated
@RestController
@RequestMapping("/api/app/notes")
@RequiredArgsConstructor
public class AppNoteController {

    private final NoteService service;

    public record SaveReq(String content, List<String> images) {
    }

    public record UploadResp(String url) {
    }

    @Operation(summary = "我的笔记（最近修改的在前）")
    @GetMapping
    public R<PageResult<NoteItem>> page(@RequestParam(required = false) Long moduleId,
                                        @RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return R.ok(service.page(StpAppUtil.userId(), moduleId, pageNum, pageSize));
    }

    @Operation(summary = "笔记详情：题目 + 笔记（模块未激活时不带答案解析）")
    @GetMapping("/{questionId}")
    public R<NoteDetail> detail(@PathVariable Long questionId) {
        return R.ok(service.detail(StpAppUtil.userId(), questionId));
    }

    @Operation(summary = "保存笔记（文字和图片都为空时删除），返回保存后的笔记")
    @PutMapping("/{questionId}")
    public R<NoteVO> save(@PathVariable Long questionId, @RequestBody SaveReq req) {
        return R.ok(service.save(StpAppUtil.userId(), questionId, req.content(), req.images()));
    }

    @Operation(summary = "删除笔记")
    @DeleteMapping("/{questionId}")
    public R<Void> delete(@PathVariable Long questionId) {
        service.delete(StpAppUtil.userId(), questionId);
        return R.ok();
    }

    @Operation(summary = "上传笔记图片，返回访问地址")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<UploadResp> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(new UploadResp(service.uploadImage(StpAppUtil.userId(), file)));
    }
}
