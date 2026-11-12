package com.lemon.biz.notify;

import com.lemon.biz.risk.service.RiskAlertAdminService;
import com.lemon.biz.stats.service.StatsService;
import com.lemon.biz.stats.service.StatsService.DayStat;
import com.lemon.biz.stats.service.StatsService.RankItem;
import com.lemon.biz.stats.service.StatsService.Retention;
import com.lemon.biz.user.mapper.AppUserMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每日汇总：每天 9 点把前一天的数据推送到飞书（和上周同一天对比）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M 月 d 日");

    private final StatsService statsService;
    private final RiskAlertAdminService riskService;
    private final AppUserMapper userMapper;
    private final FeishuNotifier notifier;
    private final StringRedisTemplate redis;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Shanghai")
    public void scheduled() {
        LocalDate day = LocalDate.now().minusDays(1);
        try {
            // 同一天只推一次（重启、多实例都不会重复）
            Boolean first = redis.opsForValue().setIfAbsent("notify:daily:" + day, "1", Duration.ofDays(3));
            if (!Boolean.TRUE.equals(first) || !notifier.configured()) {
                return;
            }
            notifier.send(FeishuNotifier.Level.INFO, title(day), build(day));
        } catch (Exception e) {
            log.warn("每日汇总推送失败: {}", e.getMessage());
        }
    }

    /** 立即推送某一天的汇总（后台手动发送），返回飞书错误信息，成功为 null */
    public String sendNow(LocalDate day) {
        return notifier.sendNow(FeishuNotifier.Level.INFO, title(day), build(day));
    }

    private static String title(LocalDate day) {
        return "Lemon 刷题日报 · " + day.format(MD);
    }

    /** day 必须是今天之前的日期 */
    public String build(LocalDate day) {
        LocalDate today = LocalDate.now();
        int back = (int) (today.toEpochDay() - day.toEpochDay());
        // trend 以今天结尾：取到 day 往前 7 天
        List<DayStat> trend = statsService.trend(back + 8);
        DayStat d = trend.get(7);
        DayStat w = trend.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("**新增用户**：").append(d.newUsers()).append(cmp(d.newUsers(), w.newUsers()))
                .append("\n**活跃用户**：").append(d.activeUsers()).append(cmp(d.activeUsers(), w.activeUsers()))
                .append("\n**做题人数**：").append(d.answerUsers()).append(cmp(d.answerUsers(), w.answerUsers()))
                .append("\n**答题数**：").append(d.answerCount()).append(cmp(d.answerCount(), w.answerCount()));
        if (d.answerUsers() > 0) {
            sb.append("（人均 ").append(Math.round((double) d.answerCount() / d.answerUsers())).append(" 题）");
        }
        if (d.correctRate() != null) {
            sb.append("\n**正确率**：").append(d.correctRate()).append("%");
        }
        sb.append("\n**激活码激活**：").append(d.activations())
                .append("\n**新增笔记**：").append(d.newNotes());

        // 留存：day 前一天注册的次日留存、day 前 7 天注册的 7 日留存
        List<Retention> retention = statsService.retention(back + 8);
        Retention r1 = find(retention, day.minusDays(1));
        Retention r7 = find(retention, day.minusDays(7));
        if (r1 != null && r1.newUsers() > 0 && r1.d1() != null) {
            sb.append("\n**次日留存**（").append(r1.date().format(MD)).append("注册）：").append(pct(r1.d1(), r1.newUsers()));
        }
        if (r7 != null && r7.newUsers() > 0 && r7.d7() != null) {
            sb.append("\n**7 日留存**（").append(r7.date().format(MD)).append("注册）：").append(pct(r7.d7(), r7.newUsers()));
        }

        sb.append("\n**累计用户**：").append(userMapper.selectCount(null));
        long pending = riskService.pendingCount();
        sb.append("\n**待处理风险告警**：").append(pending).append(pending > 0 ? "（请到后台处理）" : "");

        List<RankItem> banks = statsService.content(day, day.plusDays(1), 3, 0).banks();
        if (!banks.isEmpty()) {
            sb.append("\n\n**热门题库**");
            for (int i = 0; i < banks.size(); i++) {
                RankItem b = banks.get(i);
                sb.append("\n").append(i + 1).append(". ").append(b.name()).append("：")
                        .append(b.answers()).append(" 题 · ").append(b.users()).append(" 人");
            }
        }
        return sb.toString();
    }

    private static Retention find(List<Retention> list, LocalDate date) {
        return list.stream().filter(r -> r.date().equals(date)).findFirst().orElse(null);
    }

    /** 和上周同一天对比 */
    private static String cmp(long now, long weekAgo) {
        if (weekAgo == 0) {
            return "";
        }
        long change = Math.round((now - weekAgo) * 100.0 / weekAgo);
        return "（较上周同日 " + (change >= 0 ? "+" : "") + change + "%）";
    }

    private static String pct(long n, long total) {
        return Math.round(n * 100.0 / total) + "%（" + n + " / " + total + "）";
    }
}
