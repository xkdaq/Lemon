package com.lemon.controller.admin;

import com.lemon.biz.file.FileService;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "管理端-文件")
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class AdminFileController {

    private final FileService fileService;

    public record UploadResp(String url) {
    }

    @Operation(summary = "上传图片（题目配图、封面、图标），返回访问 URL")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<UploadResp> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(new UploadResp(fileService.uploadImage(file)));
    }
}
