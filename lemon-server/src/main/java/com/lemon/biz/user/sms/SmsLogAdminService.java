package com.lemon.biz.user.sms;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.user.mapper.SmsLogMapper;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端：短信发送记录与统计
 */
@Service
@RequiredArgsConstructor
public class SmsLogAdminService {

    /** 统计最近多少天 */
    private static final int STAT_DAYS = 30;

    private final SmsLogMapper mapper;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LogQuery extends PageQuery {
        private String phone;
        private Integer status;
        private Integer scene;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
    }

    /**
     * @param today     今天：发送数 / 已提交 / 失败
     * @param last7     最近 7 天合计
     * @param last30    最近 30 天合计
     * @param days      最近 30 天每天的数据（没有发送的日子补 0），按日期升序
     */
    public record Stats(SmsLogMapper.DayCount today, SmsLogMapper.DayCount last7, SmsLogMapper.DayCount last30,
                        List<SmsLogMapper.DayCount> days) {
    }

    public PageResult<SmsLog> page(LogQuery q) {
        return PageResult.of(mapper.selectPage(q.toPage(), Wrappers.<SmsLog>lambdaQuery()
                .like(StringUtils.hasText(q.getPhone()), SmsLog::getPhone, q.getPhone() == null ? null : q.getPhone().trim())
                .eq(q.getStatus() != null, SmsLog::getStatus, q.getStatus())
                .eq(q.getScene() != null, SmsLog::getScene, q.getScene())
                .ge(q.getStartDate() != null, SmsLog::getCreatedAt, q.getStartDate() == null ? null : q.getStartDate().atStartOfDay())
                .lt(q.getEndDate() != null, SmsLog::getCreatedAt, q.getEndDate() == null ? null : q.getEndDate().plusDays(1).atStartOfDay())
                .orderByDesc(SmsLog::getId)));
    }

    public Stats stats() {
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(STAT_DAYS - 1);
        Map<String, SmsLogMapper.DayCount> byDay = new LinkedHashMap<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            byDay.put(d.toString(), new SmsLogMapper.DayCount(d.toString(), 0, 0, 0));
        }
        for (SmsLogMapper.DayCount c : mapper.countByDay(first.atStartOfDay())) {
            byDay.put(c.day(), c);
        }
        List<SmsLogMapper.DayCount> days = List.copyOf(byDay.values());
        return new Stats(days.get(days.size() - 1), sum("近 7 天", days.subList(days.size() - 7, days.size())),
                sum("近 30 天", days), days);
    }

    private static SmsLogMapper.DayCount sum(String label, List<SmsLogMapper.DayCount> list) {
        long t = 0, ok = 0, fail = 0;
        for (SmsLogMapper.DayCount c : list) {
            t += c.total();
            ok += c.ok();
            fail += c.fail();
        }
        return new SmsLogMapper.DayCount(label, t, ok, fail);
    }
}
