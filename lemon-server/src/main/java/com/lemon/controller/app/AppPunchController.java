package com.lemon.controller.app;

import com.lemon.biz.practice.dto.PracticeDtos.SessionVO;
import com.lemon.biz.punch.dto.PunchDtos.CampCard;
import com.lemon.biz.punch.dto.PunchDtos.CampDetail;
import com.lemon.biz.punch.dto.PunchDtos.RankVO;
import com.lemon.biz.punch.dto.PunchDtos.StartReq;
import com.lemon.biz.punch.service.PunchAppService;
import com.lemon.common.R;
import com.lemon.security.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询接口（/punch-camps/**）未登录也能看；加入、打卡（/punch/**）需要登录。
 */
@Tag(name = "用户端-打卡")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppPunchController {

    private final PunchAppService service;

    @Operation(summary = "打卡活动列表")
    @GetMapping("/punch-camps")
    public R<List<CampCard>> list() {
        return R.ok(service.list(StpAppUtil.userIdOrNull()));
    }

    @Operation(summary = "打卡活动详情和某月日历（month 格式 2026-11，不传为本月）")
    @GetMapping("/punch-camps/{id}")
    public R<CampDetail> detail(@PathVariable Long id, @RequestParam(required = false) YearMonth month) {
        return R.ok(service.detail(StpAppUtil.userIdOrNull(), id, month));
    }

    @Operation(summary = "打卡排行榜（按打卡天数）")
    @GetMapping("/punch-camps/{id}/rank")
    public R<RankVO> rank(@PathVariable Long id) {
        return R.ok(service.rank(StpAppUtil.userIdOrNull(), id));
    }

    @Operation(summary = "加入打卡活动")
    @PostMapping("/punch/{campId}/join")
    public R<Void> join(@PathVariable Long campId) {
        service.join(StpAppUtil.userId(), campId);
        return R.ok();
    }

    @Operation(summary = "开始 / 继续某天的打卡（day 不传为今天，早于今天为补卡），返回做题会话")
    @PostMapping("/punch/{campId}/start")
    public R<SessionVO> start(@PathVariable Long campId, @RequestBody(required = false) StartReq req) {
        return R.ok(service.start(StpAppUtil.userId(), campId, req == null ? null : req.day()));
    }
}
