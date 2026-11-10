package com.lemon.controller.admin;

import com.lemon.biz.paper.dto.PaperRuleDtos.PaperRuleSave;
import com.lemon.biz.paper.dto.PaperRuleDtos.PaperRuleVO;
import com.lemon.biz.paper.dto.PaperRuleDtos.PoolStat;
import com.lemon.biz.paper.dto.PaperRuleDtos.PreviewQuestion;
import com.lemon.biz.paper.service.PaperRuleService;
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

@Tag(name = "管理端-组卷规则")
@RestController
@RequestMapping("/api/admin/paper-rules")
@RequiredArgsConstructor
public class AdminPaperRuleController {

    private final PaperRuleService service;

    @Operation(summary = "组卷规则列表（可按题库筛选）")
    @GetMapping
    public R<List<PaperRuleVO>> list(@RequestParam(required = false) Long bankId) {
        return R.ok(service.list(bankId));
    }

    @Operation(summary = "抽题范围内各题型的可用题量（chapterIds 为空表示整个题库）")
    @GetMapping("/pool")
    public R<List<PoolStat>> pool(@RequestParam Long bankId, @RequestParam(required = false) List<Long> chapterIds) {
        return R.ok(service.poolStats(bankId, chapterIds));
    }

    @Operation(summary = "组卷规则详情")
    @GetMapping("/{id}")
    public R<PaperRuleVO> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @Operation(summary = "新建组卷规则（会校验各题型题量是否够）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody PaperRuleSave req) {
        return R.ok(service.create(req));
    }

    @Operation(summary = "修改组卷规则")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PaperRuleSave req) {
        service.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除组卷规则（被打卡活动使用时不能删）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "按规则试抽一次")
    @PostMapping("/{id}/preview")
    public R<List<PreviewQuestion>> preview(@PathVariable Long id) {
        return R.ok(service.preview(id));
    }
}
