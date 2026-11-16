package com.lemon.controller.admin;

import com.lemon.biz.recite.service.ReciteAdminService;
import com.lemon.biz.recite.service.ReciteAdminService.AdminBookVO;
import com.lemon.biz.recite.service.ReciteAdminService.BookUpdate;
import com.lemon.biz.recite.service.ReciteAdminService.ImportResult;
import com.lemon.common.BizException;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

/**
 * 背诵资料管理：超级管理员和录题员都可用（WebConfig 默认规则）
 */
@Tag(name = "管理端-背诵资料")
@RestController
@RequestMapping("/api/admin/recite-books")
@RequiredArgsConstructor
public class AdminReciteController {

    private final ReciteAdminService service;

    @Operation(summary = "背诵资料列表")
    @GetMapping
    public R<List<AdminBookVO>> list() {
        return R.ok(service.list());
    }

    @Operation(summary = "导入背诵资料 JSON：不传 bookId 新建，传 bookId 覆盖该资料；linkBankId 为只读对照的题库")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportResult> importBook(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) Long bookId,
                                      @RequestParam(required = false) Long linkBankId) throws IOException {
        BizException.throwIf(file.isEmpty(), "请选择文件");
        return R.ok(service.importBook(file.getBytes(), bookId, linkBankId));
    }

    @Operation(summary = "修改名称 / 简介 / 排序 / 上下架")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BookUpdate req) {
        service.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除背诵资料")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
