package com.lemon.controller.admin;

import com.lemon.biz.content.dto.ContentReqs.StatusUpdate;
import com.lemon.biz.punch.dto.PunchDtos.CampSave;
import com.lemon.biz.punch.dto.PunchDtos.CampVO;
import com.lemon.biz.punch.dto.PunchDtos.MemberQuery;
import com.lemon.biz.punch.dto.PunchDtos.MemberVO;
import com.lemon.biz.punch.dto.PunchDtos.ScheduleDay;
import com.lemon.biz.punch.dto.PunchDtos.ScheduleSet;
import com.lemon.biz.punch.service.PunchAdminService;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
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

@Tag(name = "管理端-打卡活动")
@RestController
@RequestMapping("/api/admin/punch-camps")
@RequiredArgsConstructor
public class AdminPunchController {

    private final PunchAdminService service;

    @Operation(summary = "打卡活动列表（含学员数、今日打卡人数）")
    @GetMapping
    public R<List<CampVO>> list() {
        return R.ok(service.list());
    }

    @Operation(summary = "新建打卡活动")
    @PostMapping
    public R<Long> create(@Valid @RequestBody CampSave req) {
        return R.ok(service.create(req));
    }

    @Operation(summary = "修改打卡活动")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CampSave req) {
        service.update(id, req);
        return R.ok();
    }

    @Operation(summary = "打卡活动上下架")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate req) {
        service.updateStatus(id, req.status());
        return R.ok();
    }

    @Operation(summary = "删除打卡活动")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "某月日程（month 格式 2026-11）：每天的组卷规则、是否休息、打卡人数")
    @GetMapping("/{id}/schedule")
    public R<List<ScheduleDay>> schedule(@PathVariable Long id, @RequestParam YearMonth month) {
        return R.ok(service.schedule(id, month));
    }

    @Operation(summary = "把一段日期设为某个组卷规则；paperRuleId 为空表示恢复默认规则")
    @PutMapping("/{id}/schedule")
    public R<Void> setSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleSet req) {
        service.setSchedule(id, req);
        return R.ok();
    }

    @Operation(summary = "学员分页（含打卡天数）")
    @GetMapping("/{id}/members")
    public R<PageResult<MemberVO>> members(@PathVariable Long id, @Valid MemberQuery query) {
        return R.ok(service.members(id, query));
    }
}
