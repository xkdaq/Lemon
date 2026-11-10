package com.lemon.biz.punch.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lemon.biz.paper.service.PaperRuleService;
import com.lemon.biz.punch.dto.PunchDtos.CampSave;
import com.lemon.biz.punch.dto.PunchDtos.CampVO;
import com.lemon.biz.punch.dto.PunchDtos.MemberQuery;
import com.lemon.biz.punch.dto.PunchDtos.MemberVO;
import com.lemon.biz.punch.dto.PunchDtos.ScheduleDay;
import com.lemon.biz.punch.dto.PunchDtos.ScheduleSet;
import com.lemon.biz.punch.dto.PunchStat;
import com.lemon.biz.punch.entity.PunchCamp;
import com.lemon.biz.punch.entity.PunchMember;
import com.lemon.biz.punch.entity.PunchSchedule;
import com.lemon.biz.punch.mapper.PunchCampMapper;
import com.lemon.biz.punch.mapper.PunchMemberMapper;
import com.lemon.biz.punch.mapper.PunchRecordMapper;
import com.lemon.biz.punch.mapper.PunchScheduleMapper;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理端打卡活动：活动增删改、日程（按日覆盖组卷规则）、学员
 */
@Service
@RequiredArgsConstructor
public class PunchAdminService {

    private static final int MAX_SCHEDULE_DAYS = 400;

    private final PunchCampMapper campMapper;
    private final PunchScheduleMapper scheduleMapper;
    private final PunchMemberMapper memberMapper;
    private final PunchRecordMapper recordMapper;
    private final PaperRuleService ruleService;
    private final AppUserMapper userMapper;

    // ==================== 活动 ====================

    public List<CampVO> list() {
        List<PunchCamp> camps = campMapper.selectList(Wrappers.<PunchCamp>lambdaQuery()
                .orderByAsc(PunchCamp::getSort).orderByDesc(PunchCamp::getId));
        if (camps.isEmpty()) {
            return List.of();
        }
        List<Long> ids = camps.stream().map(PunchCamp::getId).toList();
        Map<Long, Long> members = toMap(recordMapper.memberCounts(ids));
        Map<Long, Long> today = toMap(recordMapper.doneOnDay(ids, LocalDate.now()));
        Map<Long, String> ruleNames = ruleService.names(camps.stream().map(PunchCamp::getPaperRuleId).toList());
        return camps.stream().map(c -> new CampVO(c.getId(), c.getName(), c.getCover(), c.getIntro(),
                c.getPaperRuleId(), ruleNames.get(c.getPaperRuleId()), c.getStartDate(), c.getEndDate(),
                PunchDays.restWeekdays(c), c.getMakeupLimit(), c.getSort(), c.getStatus(),
                members.getOrDefault(c.getId(), 0L), today.getOrDefault(c.getId(), 0L), c.getCreatedAt())).toList();
    }

    public Long create(CampSave req) {
        PunchCamp c = new PunchCamp();
        fill(c, req);
        campMapper.insert(c);
        return c.getId();
    }

    public void update(Long id, CampSave req) {
        PunchCamp c = mustGet(id);
        fill(c, req);
        campMapper.updateById(c);
    }

    public void updateStatus(Long id, int status) {
        PunchCamp c = mustGet(id);
        c.setStatus(status);
        campMapper.updateById(c);
    }

    /** 逻辑删除；学员的打卡记录保留 */
    public void delete(Long id) {
        mustGet(id);
        campMapper.deleteById(id);
    }

    // ==================== 日程 ====================

    /** 某月的日程（只含活动期内的日期） */
    public List<ScheduleDay> schedule(Long campId, YearMonth month) {
        PunchCamp camp = mustGet(campId);
        LocalDate from = max(month.atDay(1), camp.getStartDate());
        LocalDate to = min(month.atEndOfMonth(), camp.getEndDate());
        if (from.isAfter(to)) {
            return List.of();
        }
        Map<LocalDate, Long> overrides = scheduleMapper.selectList(Wrappers.<PunchSchedule>lambdaQuery()
                        .eq(PunchSchedule::getCampId, campId).between(PunchSchedule::getDay, from, to))
                .stream().collect(Collectors.toMap(PunchSchedule::getDay, PunchSchedule::getPaperRuleId));
        Map<LocalDate, Long> done = recordMapper.doneByDay(campId, from, to).stream()
                .collect(Collectors.toMap(PunchStat::getDay, PunchStat::getCnt));
        Set<Long> ruleIds = new HashSet<>(overrides.values());
        ruleIds.add(camp.getPaperRuleId());
        Map<Long, String> ruleNames = ruleService.names(ruleIds);
        List<Integer> rest = PunchDays.restWeekdays(camp);

        List<ScheduleDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Long override = overrides.get(d);
            Long ruleId = override != null ? override : camp.getPaperRuleId();
            days.add(new ScheduleDay(d, d.getDayOfWeek().getValue(), PunchDays.isRest(rest, d), ruleId,
                    ruleNames.get(ruleId), override != null, done.getOrDefault(d, 0L)));
        }
        return days;
    }

    /** 把一段日期设为某个组卷规则；不传规则表示恢复默认 */
    @Transactional
    public void setSchedule(Long campId, ScheduleSet req) {
        PunchCamp camp = mustGet(campId);
        BizException.throwIf(req.from().isAfter(req.to()), "开始日期不能晚于结束日期");
        LocalDate from = max(req.from(), camp.getStartDate());
        LocalDate to = min(req.to(), camp.getEndDate());
        BizException.throwIf(from.isAfter(to), "所选日期不在活动期内");
        BizException.throwIf(ChronoUnit.DAYS.between(from, to) >= MAX_SCHEDULE_DAYS, "一次最多设置 " + MAX_SCHEDULE_DAYS + " 天");
        if (req.paperRuleId() != null) {
            ruleService.mustGet(req.paperRuleId());
        }
        scheduleMapper.delete(Wrappers.<PunchSchedule>lambdaQuery()
                .eq(PunchSchedule::getCampId, campId).between(PunchSchedule::getDay, from, to));
        if (req.paperRuleId() == null || req.paperRuleId().equals(camp.getPaperRuleId())) {
            return;
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            PunchSchedule s = new PunchSchedule();
            s.setCampId(campId);
            s.setDay(d);
            s.setPaperRuleId(req.paperRuleId());
            scheduleMapper.insert(s);
        }
    }

    // ==================== 学员 ====================

    public PageResult<MemberVO> members(Long campId, MemberQuery q) {
        mustGet(campId);
        List<Long> matchedUsers = null;
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().strip();
            matchedUsers = userMapper.selectList(Wrappers.<AppUser>lambdaQuery().select(AppUser::getId)
                            .and(w -> w.like(AppUser::getNickname, kw).or().like(AppUser::getPhone, kw)))
                    .stream().map(AppUser::getId).toList();
            if (matchedUsers.isEmpty()) {
                return new PageResult<>(List.of(), 0, q.getPageNum(), q.getPageSize());
            }
        }
        Page<PunchMember> page = memberMapper.selectPage(q.toPage(), Wrappers.<PunchMember>lambdaQuery()
                .eq(PunchMember::getCampId, campId)
                .in(matchedUsers != null, PunchMember::getUserId, matchedUsers)
                .orderByDesc(PunchMember::getId));
        List<Long> userIds = page.getRecords().stream().map(PunchMember::getUserId).toList();
        if (userIds.isEmpty()) {
            return PageResult.of(page, m -> null);
        }
        Map<Long, AppUser> users = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<Long, PunchStat> stats = recordMapper.doneByUsers(campId, userIds).stream()
                .collect(Collectors.toMap(PunchStat::getUserId, Function.identity()));
        return PageResult.of(page, m -> {
            AppUser u = users.get(m.getUserId());
            PunchStat s = stats.get(m.getUserId());
            return new MemberVO(m.getUserId(), u == null ? null : u.getNickname(), maskPhone(u == null ? null : u.getPhone()),
                    s == null ? 0 : s.getCnt(), s == null ? null : s.getLastDay(), m.getMakeupUsed(), m.getCreatedAt());
        });
    }

    // ==================== 工具 ====================

    private void fill(PunchCamp c, CampSave req) {
        BizException.throwIf(req.startDate().isAfter(req.endDate()), "开始日期不能晚于结束日期");
        ruleService.mustGet(req.paperRuleId());
        BizException.throwIf(req.restWeekdays() != null && new HashSet<>(req.restWeekdays()).size() >= 7,
                "不能每天都休息");
        c.setName(req.name().strip());
        c.setCover(StringUtils.hasText(req.cover()) ? req.cover().strip() : null);
        c.setIntro(StringUtils.hasText(req.intro()) ? req.intro().strip() : null);
        c.setPaperRuleId(req.paperRuleId());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setRestWeekdays(PunchDays.formatRestWeekdays(req.restWeekdays()));
        c.setMakeupLimit(Objects.requireNonNullElse(req.makeupLimit(), 0));
        c.setSort(Objects.requireNonNullElse(req.sort(), 0));
        c.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }

    private PunchCamp mustGet(Long id) {
        return BizException.notNull(campMapper.selectById(id), "打卡活动不存在");
    }

    private static Map<Long, Long> toMap(List<PunchStat> stats) {
        return stats.stream().collect(Collectors.toMap(PunchStat::getCampId, PunchStat::getCnt));
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
