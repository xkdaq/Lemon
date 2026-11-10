package com.lemon.controller.admin;

import com.lemon.biz.content.dto.ContentReqs.BankQuery;
import com.lemon.biz.content.dto.ContentReqs.BankSave;
import com.lemon.biz.content.dto.ContentReqs.ChapterSave;
import com.lemon.biz.content.dto.ContentReqs.CourseSave;
import com.lemon.biz.content.dto.ContentReqs.ModuleSave;
import com.lemon.biz.content.dto.ContentReqs.StatusUpdate;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.service.ContentAdminService;
import com.lemon.common.PageResult;
import com.lemon.common.R;
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

@Tag(name = "管理端-内容管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final ContentAdminService service;

    @Operation(summary = "题库-科目-章节目录树（级联选择用）")
    @GetMapping("/content-tree")
    public R<List<ContentAdminService.TreeNode>> tree() {
        return R.ok(service.tree());
    }

    // -------- 模块 --------

    @Operation(summary = "模块列表")
    @GetMapping("/modules")
    public R<List<ExamModule>> listModules() {
        return R.ok(service.listModules());
    }

    @Operation(summary = "新建模块")
    @PostMapping("/modules")
    public R<Long> createModule(@Valid @RequestBody ModuleSave req) {
        return R.ok(service.createModule(req));
    }

    @Operation(summary = "修改模块")
    @PutMapping("/modules/{id}")
    public R<Void> updateModule(@PathVariable Long id, @Valid @RequestBody ModuleSave req) {
        service.updateModule(id, req);
        return R.ok();
    }

    @Operation(summary = "删除模块")
    @DeleteMapping("/modules/{id}")
    public R<Void> deleteModule(@PathVariable Long id) {
        service.deleteModule(id);
        return R.ok();
    }

    // -------- 题库 --------

    @Operation(summary = "题库分页")
    @GetMapping("/banks")
    public R<PageResult<QuestionBank>> pageBanks(@Valid BankQuery query) {
        return R.ok(service.pageBanks(query));
    }

    @Operation(summary = "题库详情")
    @GetMapping("/banks/{id}")
    public R<QuestionBank> getBank(@PathVariable Long id) {
        return R.ok(service.getBank(id));
    }

    @Operation(summary = "新建题库（type：1 科目型 2 试卷型）")
    @PostMapping("/banks")
    public R<Long> createBank(@Valid @RequestBody BankSave req) {
        return R.ok(service.createBank(req));
    }

    @Operation(summary = "修改题库")
    @PutMapping("/banks/{id}")
    public R<Void> updateBank(@PathVariable Long id, @Valid @RequestBody BankSave req) {
        service.updateBank(id, req);
        return R.ok();
    }

    @Operation(summary = "题库上下架")
    @PutMapping("/banks/{id}/status")
    public R<Void> updateBankStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate req) {
        service.updateBankStatus(id, req.status());
        return R.ok();
    }

    @Operation(summary = "删除题库")
    @DeleteMapping("/banks/{id}")
    public R<Void> deleteBank(@PathVariable Long id) {
        service.deleteBank(id);
        return R.ok();
    }

    // -------- 科目 --------

    @Operation(summary = "科目列表")
    @GetMapping("/courses")
    public R<List<Course>> listCourses(@RequestParam Long bankId) {
        return R.ok(service.listCourses(bankId));
    }

    @Operation(summary = "新建科目（仅科目型题库）")
    @PostMapping("/courses")
    public R<Long> createCourse(@Valid @RequestBody CourseSave req) {
        return R.ok(service.createCourse(req));
    }

    @Operation(summary = "修改科目")
    @PutMapping("/courses/{id}")
    public R<Void> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseSave req) {
        service.updateCourse(id, req);
        return R.ok();
    }

    @Operation(summary = "删除科目")
    @DeleteMapping("/courses/{id}")
    public R<Void> deleteCourse(@PathVariable Long id) {
        service.deleteCourse(id);
        return R.ok();
    }

    // -------- 章节 --------

    @Operation(summary = "章节列表")
    @GetMapping("/chapters")
    public R<List<Chapter>> listChapters(@RequestParam Long bankId, @RequestParam(required = false) Long courseId) {
        return R.ok(service.listChapters(bankId, courseId));
    }

    @Operation(summary = "新建章节")
    @PostMapping("/chapters")
    public R<Long> createChapter(@Valid @RequestBody ChapterSave req) {
        return R.ok(service.createChapter(req));
    }

    @Operation(summary = "修改章节")
    @PutMapping("/chapters/{id}")
    public R<Void> updateChapter(@PathVariable Long id, @Valid @RequestBody ChapterSave req) {
        service.updateChapter(id, req);
        return R.ok();
    }

    @Operation(summary = "删除章节")
    @DeleteMapping("/chapters/{id}")
    public R<Void> deleteChapter(@PathVariable Long id) {
        service.deleteChapter(id);
        return R.ok();
    }
}
