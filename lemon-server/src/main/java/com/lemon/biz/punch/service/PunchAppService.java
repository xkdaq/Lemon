package com.lemon.biz.punch.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.access.service.AccessService;
import com.lemon.biz.paper.entity.PaperRule;
import com.lemon.biz.paper.mapper.PaperRuleMapper;
import com.lemon.biz.paper.service.PaperRuleService;
import com.lemon.biz.practice.dto.PracticeDtos.SessionVO;
import com.lemon.biz.practice.enums.PracticeMode;
import com.lemon.biz.practice.event.PracticeFinishedEvent;
import com.lemon.biz.practice.service.PracticeService;
import com.lemon.biz.punch.dto.PunchDtos.CalendarDay;
import com.lemon.biz.punch.dto.PunchDtos.CampCard;
import com.lemon.biz.punch.dto.PunchDtos.CampDetail;
import com.lemon.biz.punch.dto.PunchDtos.RankItem;
import com.lemon.biz.punch.dto.PunchDtos.RankVO;
import com.lemon.biz.punch.dto.PunchStat;
import com.lemon.biz.punch.entity.PunchCamp;
import com.lemon.biz.punch.entity.PunchMember;
import com.lemon.biz.punch.entity.PunchRecord;
import com.lemon.biz.punch.entity.PunchSchedule;
import com.lemon.biz.punch.mapper.PunchCampMapper;
import com.lemon.biz.punch.mapper.PunchMemberMapper;
import com.lemon.biz.punch.mapper.PunchRecordMapper;
import com.lemon.biz.punch.mapper.PunchScheduleMapper;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户端打卡。
 * <ul>
 *   <li>先加入活动才能打卡；活动期内、非休息日、不晚于今天的日期可以打卡</li>
 *   <li>开始打卡时按当天的组卷规则抽题并新建做题会话（模式：每日打卡），交卷后当天记为已打卡</li>
 *   <li>早于今天的日期为补卡：只能补加入之后的日期，每开始一次补卡消耗一次补卡次数</li>
 *   <li>同一天再次进入时续做 / 查看同一个会话</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PunchAppService {

    private static final int RANK_SIZE = 50;

    private final PunchCampMapper campMapper;
    private final PunchScheduleMapper scheduleMapper;
    private final PunchMemberMapper memberMapper;
    private final PunchRecordMapper recordMapper;
    private final PaperRuleService ruleService;
    private final PaperRuleMapper paperRuleMapper;
    private final AccessService accessService;
    private final PracticeService practiceService;
    private final AppUserMapper userMapper;

    // ==================== 查询 ====================

    /** 上架的打卡活动（未结束的在前） */
    public List<CampCard> list(Long userId) {
        LocalDate today = LocalDate.now();
        List<PunchCamp> camps = campMapper.selectList(Wrappers.<PunchCamp>lambdaQuery()
                .eq(PunchCamp::getStatus, Status.ON)
                .orderByAsc(PunchCamp::getSort).orderByDesc(PunchCamp::getId));
        if (camps.isEmpty()) {
            return List.of();
        }
        List<Long> ids = camps.stream().map(PunchCamp::getId).toList();
        Map<Long, Long> members = recordMapper.memberCounts(ids).stream()
                .collect(Collectors.toMap(PunchStat::getCampId, PunchStat::getCnt));
        Set<Long> joined = userId == null ? Set.of() : memberMapper.selectList(Wrappers.<PunchMember>lambdaQuery()
                        .eq(PunchMember::getUserId, userId).in(PunchMember::getCampId, ids))
                .stream().map(PunchMember::getCampId).collect(Collectors.toSet());
        Set<Long> todayDone = userId == null ? Set.of() : recordMapper.selectList(Wrappers.<PunchRecord>lambdaQuery()
                        .eq(PunchRecord::getUserId, userId).eq(PunchRecord::getDay, today)
                        .eq(PunchRecord::getStatus, PunchRecord.DONE).in(PunchRecord::getCampId, ids))
                .stream().map(PunchRecord::getCampId).collect(Collectors.toSet());
        Map<Long, Integer> counts = questionCounts(camps);
        return camps.stream()
                .sorted((a, b) -> Boolean.compare(a.getEndDate().isBefore(today), b.getEndDate().isBefore(today)))
                .map(c -> new CampCard(c.getId(), c.getName(), c.getCover(), c.getStartDate(), c.getEndDate(),
                        counts.getOrDefault(c.getPaperRuleId(), 0), members.getOrDefault(c.getId(), 0L),
                        joined.contains(c.getId()), todayDone.contains(c.getId())))
                .toList();
    }

    /**
     * 活动详情和某月日历
     *
     * @param month 不传为本月（活动未开始时为开始月份）
     */
    public CampDetail detail(Long userId, Long campId, YearMonth month) {
        PunchCamp camp = onCamp(campId);
        LocalDate today = LocalDate.now();
        if (month == null) {
            month = YearMonth.from(today.isBefore(camp.getStartDate()) ? camp.getStartDate()
                    : today.isAfter(camp.getEndDate()) ? camp.getEndDate() : today);
        }
        List<Long> ids = List.of(campId);
        long memberCount = recordMapper.memberCounts(ids).stream().mapToLong(PunchStat::getCnt).sum();
        long todayCount = recordMapper.doneOnDay(ids, today).stream().mapToLong(PunchStat::getCnt).sum();
        int questionCount = questionCounts(List.of(camp)).getOrDefault(camp.getPaperRuleId(), 0);
        List<Integer> rest = PunchDays.restWeekdays(camp);
        PunchMember member = userId == null ? null : member(campId, userId);

        Map<LocalDate, PunchRecord> records = member == null ? Map.of()
                : recordMapper.selectList(Wrappers.<PunchRecord>lambdaQuery()
                        .eq(PunchRecord::getCampId, campId).eq(PunchRecord::getUserId, userId))
                .stream().collect(Collectors.toMap(PunchRecord::getDay, Function.identity()));
        LocalDate joinDay = member == null ? null : member.getCreatedAt().toLocalDate();

        List<CalendarDay> calendar = new ArrayList<>();
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            calendar.add(dayState(camp, rest, joinDay, records, today, d));
        }
        CalendarDay todayState = PunchDays.inRange(camp, today) ? dayState(camp, rest, joinDay, records, today, today) : null;

        Long punchDays = null;
        Integer streak = null;
        Integer makeupLeft = null;
        if (member != null) {
            punchDays = records.values().stream().filter(r -> r.getStatus() == PunchRecord.DONE).count();
            streak = streak(camp, rest, records, today);
            makeupLeft = Math.max(0, camp.getMakeupLimit() - member.getMakeupUsed());
        }
        return new CampDetail(camp.getId(), camp.getName(), camp.getCover(), camp.getIntro(), camp.getStartDate(),
                camp.getEndDate(), rest, camp.getMakeupLimit(), questionCount, memberCount, todayCount,
                member != null, todayState, punchDays, streak, makeupLeft, month.toString(), calendar);
    }

    public RankVO rank(Long userId, Long campId) {
        onCamp(campId);
        List<PunchStat> top = recordMapper.rank(campId, RANK_SIZE);
        Map<Long, AppUser> users = top.isEmpty() ? Map.of() : userMapper.selectByIds(
                        top.stream().map(PunchStat::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<RankItem> list = new ArrayList<>();
        RankItem me = null;
        for (int i = 0; i < top.size(); i++) {
            PunchStat s = top.get(i);
            AppUser u = users.get(s.getUserId());
            RankItem item = new RankItem(i + 1, s.getUserId(), displayName(u), u == null ? null : u.getAvatar(), s.getCnt());
            list.add(item);
            if (s.getUserId().equals(userId)) {
                me = item;
            }
        }
        if (me == null && userId != null) {
            List<PunchStat> mine = recordMapper.doneByUsers(campId, List.of(userId));
            if (!mine.isEmpty()) {
                long days = mine.get(0).getCnt();
                AppUser u = userMapper.selectById(userId);
                me = new RankItem((int) recordMapper.countAhead(campId, days) + 1, userId, displayName(u),
                        u == null ? null : u.getAvatar(), days);
            }
        }
        return new RankVO(list, me);
    }

    // ==================== 加入 / 打卡 ====================

    public void join(Long userId, Long campId) {
        PunchCamp camp = onCamp(campId);
        BizException.throwIf(LocalDate.now().isAfter(camp.getEndDate()), "活动已结束");
        if (member(campId, userId) != null) {
            return;
        }
        PunchMember m = new PunchMember();
        m.setCampId(campId);
        m.setUserId(userId);
        m.setMakeupUsed(0);
        try {
            memberMapper.insert(m);
        } catch (DuplicateKeyException ignored) {
            // 并发重复加入
        }
    }

    /** 开始（或继续）某天的打卡，返回做题会话 */
    @Transactional
    public SessionVO start(Long userId, Long campId, LocalDate day) {
        PunchCamp camp = onCamp(campId);
        PunchMember member = BizException.notNull(member(campId, userId), "请先加入打卡活动");
        LocalDate today = LocalDate.now();
        if (day == null) {
            day = today;
        }
        PunchRecord existing = recordMapper.selectOne(Wrappers.<PunchRecord>lambdaQuery()
                .eq(PunchRecord::getCampId, campId).eq(PunchRecord::getUserId, userId).eq(PunchRecord::getDay, day));
        if (existing != null) {
            return practiceService.get(userId, existing.getSessionId());
        }

        BizException.throwIf(!PunchDays.inRange(camp, day), "不在活动日期内");
        BizException.throwIf(PunchDays.isRest(PunchDays.restWeekdays(camp), day), "这天是休息日，不用打卡");
        BizException.throwIf(day.isAfter(today), "还没到这一天");
        boolean makeup = day.isBefore(today);
        if (makeup) {
            BizException.throwIf(day.isBefore(member.getCreatedAt().toLocalDate()), "只能补加入活动之后的日期");
            BizException.throwIf(member.getMakeupUsed() >= camp.getMakeupLimit(),
                    camp.getMakeupLimit() == 0 ? "该活动不支持补卡" : "补卡次数已用完");
            // 条件更新，防止并发时补卡次数超用
            int updated = memberMapper.update(Wrappers.<PunchMember>lambdaUpdate()
                    .setSql("makeup_used = makeup_used + 1")
                    .eq(PunchMember::getId, member.getId())
                    .lt(PunchMember::getMakeupUsed, camp.getMakeupLimit()));
            BizException.throwIf(updated == 0, "补卡次数已用完");
        }

        Long ruleId = ruleFor(camp, day);
        // 打卡题目来自组卷规则所在题库的模块，需要激活该模块
        PaperRule rule = BizException.notNull(paperRuleMapper.selectById(ruleId), "打卡规则不存在");
        accessService.requireAccess(userId, accessService.moduleOfBank(rule.getBankId()));
        List<Long> questionIds = ruleService.pick(ruleId, PunchDays.seed(campId, day));
        SessionVO session = practiceService.startPunch(userId, questionIds);
        PunchRecord r = new PunchRecord();
        r.setCampId(campId);
        r.setUserId(userId);
        r.setDay(day);
        r.setSessionId(session.id());
        r.setStatus(PunchRecord.DOING);
        r.setMakeup(makeup ? 1 : 0);
        r.setTotal(questionIds.size());
        r.setCorrectCount(0);
        try {
            recordMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 同一天被并发开始了两次：整个事务回滚（包括会话和补卡次数），让用户重进一次
            throw new BizException("操作太快了，请重新进入");
        }
        return session;
    }

    /** 打卡做题交卷后，当天记为已打卡 */
    @EventListener
    public void onFinished(PracticeFinishedEvent e) {
        if (e.mode() != PracticeMode.PUNCH.getCode()) {
            return;
        }
        recordMapper.update(Wrappers.<PunchRecord>lambdaUpdate()
                .set(PunchRecord::getStatus, PunchRecord.DONE)
                .set(PunchRecord::getCorrectCount, e.correctCount())
                .set(PunchRecord::getFinishedAt, LocalDateTime.now())
                .eq(PunchRecord::getSessionId, e.sessionId())
                .eq(PunchRecord::getStatus, PunchRecord.DOING));
    }

    // ==================== 工具 ====================

    private CalendarDay dayState(PunchCamp camp, List<Integer> rest, LocalDate joinDay,
                                 Map<LocalDate, PunchRecord> records, LocalDate today, LocalDate d) {
        PunchRecord r = records.get(d);
        if (r != null) {
            boolean done = r.getStatus() == PunchRecord.DONE;
            return new CalendarDay(d, done ? "done" : "doing", r.getSessionId(), r.getMakeup() == 1 ? true : null);
        }
        if (!PunchDays.inRange(camp, d)) {
            return new CalendarDay(d, "out", null, null);
        }
        if (PunchDays.isRest(rest, d)) {
            return new CalendarDay(d, "rest", null, null);
        }
        if (d.isAfter(today)) {
            return new CalendarDay(d, "future", null, null);
        }
        if (d.isEqual(today)) {
            return new CalendarDay(d, "todo", null, null);
        }
        // 过去的日期：未加入时或加入之前的不算漏打
        return new CalendarDay(d, joinDay == null || d.isBefore(joinDay) ? "out" : "missed", null, null);
    }

    /** 连续打卡天数：从今天往前数，休息日跳过；今天还没打不算中断 */
    private static int streak(PunchCamp camp, List<Integer> rest, Map<LocalDate, PunchRecord> records, LocalDate today) {
        int streak = 0;
        LocalDate d = today.isAfter(camp.getEndDate()) ? camp.getEndDate() : today;
        boolean first = true;
        for (; !d.isBefore(camp.getStartDate()); d = d.minusDays(1), first = false) {
            if (PunchDays.isRest(rest, d)) {
                continue;
            }
            PunchRecord r = records.get(d);
            if (r != null && r.getStatus() == PunchRecord.DONE) {
                streak++;
            } else if (!(first && d.isEqual(today))) {
                break;
            }
        }
        return streak;
    }

    /** 当天使用的组卷规则：有单独设置用单独设置的，否则用活动默认规则 */
    private Long ruleFor(PunchCamp camp, LocalDate day) {
        PunchSchedule s = scheduleMapper.selectOne(Wrappers.<PunchSchedule>lambdaQuery()
                .eq(PunchSchedule::getCampId, camp.getId()).eq(PunchSchedule::getDay, day));
        return s != null ? s.getPaperRuleId() : camp.getPaperRuleId();
    }

    /** 默认规则每天的题数：规则 id → 题数 */
    private Map<Long, Integer> questionCounts(List<PunchCamp> camps) {
        return ruleService.questionCounts(camps.stream().map(PunchCamp::getPaperRuleId).collect(Collectors.toSet()));
    }

    private PunchCamp onCamp(Long campId) {
        PunchCamp camp = campMapper.selectById(campId);
        BizException.throwIf(camp == null || camp.getStatus() != Status.ON, "打卡活动不存在或已下架");
        return camp;
    }

    private PunchMember member(Long campId, Long userId) {
        return memberMapper.selectOne(Wrappers.<PunchMember>lambdaQuery()
                .eq(PunchMember::getCampId, campId).eq(PunchMember::getUserId, userId));
    }

    private static String displayName(AppUser u) {
        if (u == null) {
            return "已注销用户";
        }
        if (u.getNickname() != null && !u.getNickname().isBlank()) {
            return u.getNickname();
        }
        String phone = u.getPhone();
        return phone != null && phone.length() >= 4 ? "用户" + phone.substring(phone.length() - 4) : "用户" + u.getId();
    }
}
