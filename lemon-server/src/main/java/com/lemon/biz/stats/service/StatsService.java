package com.lemon.biz.stats.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.stats.mapper.StatsMapper;
import com.lemon.biz.stats.mapper.StatsMapper.DayRow;
import com.lemon.biz.stats.mapper.StatsMapper.RankRow;
import com.lemon.common.HtmlUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端数据统计：每日趋势、新用户留存、内容热度（热门题库 / 章节、错误率最高的题）。
 * 实时从业务表汇总（都有时间索引），数据量大了再改成每日汇总表。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int HARD_MIN_ATTEMPTS = 10;
    private static final int STEM_PREVIEW = 60;

    private final StatsMapper mapper;
    private final QuestionBankMapper bankMapper;
    private final ChapterMapper chapterMapper;
    private final ExamModuleMapper moduleMapper;
    private final QuestionMapper questionMapper;

    /**
     * 一天的数据
     *
     * @param activeUsers 活跃用户（当天用过用户端的登录用户）
     * @param answerUsers 做题人数
     * @param correctRate 客观题正确率（%），当天没有判题作答为 null
     */
    public record DayStat(@JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, long newUsers, long activeUsers,
                          long answerCount, long answerUsers, Integer correctRate, long activations, long newNotes) {
    }

    /** 注册日留存：d1 / d3 / d7 为注册后第 N 天仍活跃的人数，还没到那一天为 null */
    public record Retention(@JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, long newUsers, Long d1, Long d3, Long d7) {
    }

    public record RankItem(Long id, String name, String sub, long answers, long users, Integer wrongRate) {
    }

    public record Content(List<RankItem> banks, List<RankItem> chapters, List<RankItem> hardQuestions) {
    }

    public long activeToday() {
        return mapper.activeOn(LocalDate.now());
    }

    /** 最近 days 天（含今天），按日期正序 */
    public List<DayStat> trend(int days) {
        LocalDate to = LocalDate.now().plusDays(1);
        LocalDate from = to.minusDays(days);
        LocalDateTime f = from.atStartOfDay();
        LocalDateTime t = to.atStartOfDay();
        Map<LocalDate, DayRow> newUsers = byDay(mapper.newUsers(f, t));
        Map<LocalDate, DayRow> active = byDay(mapper.activeUsers(from, to));
        Map<LocalDate, DayRow> answers = byDay(mapper.answers(f, t));
        Map<LocalDate, DayRow> activations = byDay(mapper.activations(f, t));
        Map<LocalDate, DayRow> notes = byDay(mapper.notes(f, t));
        List<DayStat> list = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            DayRow a = answers.get(d);
            Integer rate = a == null || a.getJudged() == 0 ? null : (int) Math.round(a.getRightCount() * 100.0 / a.getJudged());
            list.add(new DayStat(d, c(newUsers.get(d)), c(active.get(d)), c(a), a == null ? 0 : a.getU(), rate,
                    c(activations.get(d)), c(notes.get(d))));
        }
        return list;
    }

    /** 最近 days 天注册的用户的留存，按日期倒序 */
    public List<Retention> retention(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, StatsMapper.RetentionRow> rows = mapper.retention(from.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream().collect(Collectors.toMap(StatsMapper.RetentionRow::getD, Function.identity()));
        List<Retention> list = new ArrayList<>();
        for (LocalDate d = today; !d.isBefore(from); d = d.minusDays(1)) {
            var r = rows.get(d);
            long total = r == null ? 0 : r.getTotal();
            list.add(new Retention(d, total,
                    reached(d, 1, today) ? (r == null ? 0L : r.getD1()) : null,
                    reached(d, 3, today) ? (r == null ? 0L : r.getD3()) : null,
                    reached(d, 7, today) ? (r == null ? 0L : r.getD7()) : null));
        }
        return list;
    }

    /** 最近 days 天的内容热度 */
    public Content content(int days) {
        LocalDate today = LocalDate.now();
        return content(today.minusDays(days - 1L), today.plusDays(1), 10, 20);
    }

    /** [from, to) 的内容热度 */
    public Content content(LocalDate fromDay, LocalDate toDay, int rankLimit, int hardLimit) {
        LocalDateTime from = fromDay.atStartOfDay();
        LocalDateTime to = toDay.atStartOfDay();
        List<RankRow> bankRows = mapper.hotBanks(from, to, rankLimit);
        List<RankRow> chapterRows = mapper.hotChapters(from, to, rankLimit);
        List<RankRow> hardRows = mapper.hardQuestions(from, to, HARD_MIN_ATTEMPTS, hardLimit);

        Map<Long, Chapter> chapters = byId(chapterRows.isEmpty() ? List.of()
                : chapterMapper.selectByIds(ids(chapterRows)), Chapter::getId);
        Map<Long, Question> questions = byId(hardRows.isEmpty() ? List.of()
                : questionMapper.selectByIds(ids(hardRows)), Question::getId);
        var bankIds = new java.util.HashSet<>(ids(bankRows));
        chapters.values().forEach(c -> bankIds.add(c.getBankId()));
        questions.values().forEach(q -> bankIds.add(q.getBankId()));
        Map<Long, QuestionBank> banks = byId(bankIds.isEmpty() ? List.of() : bankMapper.selectByIds(bankIds), QuestionBank::getId);
        Map<Long, ExamModule> modules = byId(moduleMapper.selectList(null), ExamModule::getId);

        List<RankItem> bankItems = bankRows.stream().map(r -> {
            QuestionBank b = banks.get(r.getId());
            return item(r, b == null ? "（已删除）" : b.getName(),
                    b == null || modules.get(b.getModuleId()) == null ? null : modules.get(b.getModuleId()).getName());
        }).toList();
        List<RankItem> chapterItems = chapterRows.stream().map(r -> {
            Chapter c = chapters.get(r.getId());
            QuestionBank b = c == null ? null : banks.get(c.getBankId());
            return item(r, c == null ? "（已删除）" : c.getName(), b == null ? null : b.getName());
        }).toList();
        List<RankItem> hardItems = hardRows.stream().map(r -> {
            Question q = questions.get(r.getId());
            QuestionBank b = q == null ? null : banks.get(q.getBankId());
            return item(r, q == null ? "（已删除）" : preview(q.getStem()), b == null ? null : b.getName());
        }).toList();
        return new Content(bankItems, chapterItems, hardItems);
    }

    private static RankItem item(RankRow r, String name, String sub) {
        Integer rate = r.getAnswers() == 0 ? null : (int) Math.round(r.getWrong() * 100.0 / r.getAnswers());
        return new RankItem(r.getId(), name, sub, r.getAnswers(), r.getUsers(), rate);
    }

    private static boolean reached(LocalDate cohort, int n, LocalDate today) {
        return !cohort.plusDays(n).isAfter(today);
    }

    private static long c(DayRow r) {
        return r == null ? 0 : r.getC();
    }

    private static Map<LocalDate, DayRow> byDay(List<DayRow> rows) {
        return rows.stream().collect(Collectors.toMap(DayRow::getD, Function.identity(), (a, b) -> a));
    }

    private static List<Long> ids(List<RankRow> rows) {
        return rows.stream().map(RankRow::getId).toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static String preview(String html) {
        String text = HtmlUtil.htmlToText(html).replaceAll("\\s+", " ").strip();
        return text.length() > STEM_PREVIEW ? text.substring(0, STEM_PREVIEW) + "…" : text;
    }
}
