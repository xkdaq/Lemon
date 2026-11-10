package com.lemon.controller.admin;

import com.lemon.biz.content.dto.ContentReqs.StatusUpdate;
import com.lemon.biz.operation.entity.Banner;
import com.lemon.biz.operation.entity.Notice;
import com.lemon.biz.operation.service.BannerService;
import com.lemon.biz.operation.service.BannerService.BannerSave;
import com.lemon.biz.operation.service.FeedbackService;
import com.lemon.biz.operation.service.FeedbackService.FeedbackVO;
import com.lemon.biz.operation.service.FeedbackService.HandleReq;
import com.lemon.biz.operation.entity.SysConfig;
import com.lemon.biz.operation.service.DashboardService;
import com.lemon.biz.operation.service.NoticeService;
import com.lemon.biz.operation.service.NoticeService.NoticeSave;
import com.lemon.biz.operation.service.SysConfigService;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.service.AppUserAdminService;
import com.lemon.biz.user.service.AppUserAdminService.UserQuery;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

/**
 * 工作台、用户端用户、公告、Banner、系统参数、反馈。工作台和反馈两种角色都可用，其余仅超级管理员（WebConfig 路由鉴权）。
 */
@Tag(name = "管理端-运营")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOperationController {

    private final DashboardService dashboardService;
    private final AppUserAdminService userService;
    private final NoticeService noticeService;
    private final SysConfigService configService;
    private final FeedbackService feedbackService;
    private final BannerService bannerService;

    @Operation(summary = "工作台统计")
    @GetMapping("/dashboard")
    public R<DashboardService.Dashboard> dashboard() {
        return R.ok(dashboardService.get());
    }

    // -------- 用户端用户 --------

    @Operation(summary = "用户列表")
    @GetMapping("/app-users")
    public R<PageResult<AppUser>> users(@Valid UserQuery query) {
        return R.ok(userService.page(query));
    }

    @Operation(summary = "启用 / 禁用用户（禁用后立即下线）")
    @PutMapping("/app-users/{id}/status")
    public R<Void> userStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate req) {
        userService.updateStatus(id, req.status());
        return R.ok();
    }

    // -------- 公告 --------

    @Operation(summary = "公告分页")
    @GetMapping("/notices")
    public R<PageResult<Notice>> notices(@Valid PageQuery query) {
        return R.ok(noticeService.page(query));
    }

    @Operation(summary = "新建公告")
    @PostMapping("/notices")
    public R<Long> createNotice(@Valid @RequestBody NoticeSave req) {
        return R.ok(noticeService.create(req));
    }

    @Operation(summary = "修改公告")
    @PutMapping("/notices/{id}")
    public R<Void> updateNotice(@PathVariable Long id, @Valid @RequestBody NoticeSave req) {
        noticeService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/notices/{id}")
    public R<Void> deleteNotice(@PathVariable Long id) {
        noticeService.delete(id);
        return R.ok();
    }

    // -------- 反馈（超级管理员和录题员都可处理） --------

    @Operation(summary = "反馈分页（待处理在前）")
    @GetMapping("/feedback")
    public R<PageResult<FeedbackVO>> feedback(@RequestParam(required = false) Integer type,
                                              @RequestParam(required = false) Integer status,
                                              @Valid PageQuery query) {
        return R.ok(feedbackService.page(type, status, query.getPageNum(), query.getPageSize()));
    }

    @Operation(summary = "处理反馈：回复并标记状态")
    @PutMapping("/feedback/{id}")
    public R<Void> handleFeedback(@PathVariable Long id, @Valid @RequestBody HandleReq req) {
        feedbackService.handle(id, req);
        return R.ok();
    }

    // -------- Banner --------

    @Operation(summary = "Banner 列表")
    @GetMapping("/banners")
    public R<List<Banner>> banners() {
        return R.ok(bannerService.list());
    }

    @Operation(summary = "新建 Banner")
    @PostMapping("/banners")
    public R<Long> createBanner(@Valid @RequestBody BannerSave req) {
        return R.ok(bannerService.create(req));
    }

    @Operation(summary = "修改 Banner")
    @PutMapping("/banners/{id}")
    public R<Void> updateBanner(@PathVariable Long id, @Valid @RequestBody BannerSave req) {
        bannerService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除 Banner")
    @DeleteMapping("/banners/{id}")
    public R<Void> deleteBanner(@PathVariable Long id) {
        bannerService.delete(id);
        return R.ok();
    }

    // -------- 系统参数 --------

    public record ConfigUpdate(@Size(max = 500) String value) {
    }

    @Operation(summary = "系统参数列表")
    @GetMapping("/configs")
    public R<List<SysConfig>> configs() {
        return R.ok(configService.list());
    }

    @Operation(summary = "修改系统参数值")
    @PutMapping("/configs/{key}")
    public R<Void> updateConfig(@PathVariable String key, @Valid @RequestBody ConfigUpdate req) {
        configService.update(key, req.value());
        return R.ok();
    }
}
