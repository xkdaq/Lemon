package com.lemon.controller.app;

import com.lemon.biz.content.entity.BankCategory;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.BankType;
import com.lemon.biz.content.service.ContentQueryService;
import com.lemon.biz.practice.dto.PracticeDtos.LastSession;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.practice.dto.ChapterSectionStat;
import com.lemon.biz.practice.service.PracticeService;
import com.lemon.security.StpAppUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.lemon.biz.operation.entity.Banner;
import com.lemon.biz.operation.entity.Notice;
import com.lemon.biz.operation.service.BannerService;
import com.lemon.biz.content.service.ScoreService;
import com.lemon.biz.operation.service.SysConfigService;
import com.lemon.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户端-题库目录")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppContentController {

    private final ContentQueryService service;
    private final PracticeService practiceService;
    private final BannerService bannerService;
    private final QuestionMapper questionMapper;
    private final SysConfigService configService;
    private final ScoreService scoreService;
    private final com.lemon.biz.document.service.AppDocumentService documentService;

    /** 用户端全局配置：theme 为主题配色（预设名或自定义主色 #RRGGBB） */
    public record AppConfigResp(String theme) {
    }

    @Operation(summary = "用户端全局配置（主题配色），启动时读取，无需登录")
    @GetMapping("/app-config")
    public R<AppConfigResp> appConfig() {
        return R.ok(new AppConfigResp(configService.getString(SysConfigService.APP_THEME, SysConfigService.DEFAULT_THEME)));
    }

    public record DocumentResp(String title, String content,
                               @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd") java.time.LocalDateTime updatedAt) {
    }

    @Operation(summary = "协议文档：agreement 用户协议 / privacy 隐私政策")
    @GetMapping("/documents/{key}")
    public R<DocumentResp> document(@org.springframework.web.bind.annotation.PathVariable String key) {
        var d = documentService.get(key);
        return R.ok(new DocumentResp(d.getTitle(), d.getContent(), d.getUpdatedAt()));
    }

    public record ServiceInfo(String wechat, String note) {
    }

    @Operation(summary = "联系客服：客服微信号和说明")
    @GetMapping("/service-info")
    public R<ServiceInfo> serviceInfo() {
        return R.ok(new ServiceInfo(configService.getString(SysConfigService.SERVICE_WECHAT, ""),
                configService.getString(SysConfigService.SERVICE_NOTE, "")));
    }

    /** lastSession：登录用户最近一次未完成的练习，未登录或没有时为 null */
    public record HomeResp(List<ExamModule> modules, List<Notice> notices, List<Banner> banners, LastSession lastSession) {
    }

    public record BankDetailResp(QuestionBank bank, List<Course> courses, List<ChapterItem> chapters) {
    }

    @Operation(summary = "首页：模块 + 公告")
    @GetMapping("/home")
    public R<HomeResp> home() {
        Long userId = StpAppUtil.userIdOrNull();
        return R.ok(new HomeResp(service.modules(), service.notices(), bannerService.active(),
                userId == null ? null : practiceService.last(userId)));
    }

    @Operation(summary = "模块下的分类（上架的），题库列表按分类分组展示")
    @GetMapping("/modules/{moduleId}/categories")
    public R<List<BankCategory>> categories(@PathVariable Long moduleId) {
        return R.ok(service.categories(moduleId));
    }

    @Operation(summary = "模块下的题库列表（带 categoryId，未分类为 null）")
    @GetMapping("/modules/{moduleId}/banks")
    public R<List<QuestionBank>> banks(@PathVariable Long moduleId) {
        return R.ok(service.banks(moduleId));
    }

    @Operation(summary = "题库详情：科目型返回科目列表，试卷型返回章节（试卷）列表")
    @GetMapping("/banks/{bankId}")
    public R<BankDetailResp> bank(@PathVariable Long bankId) {
        QuestionBank bank = service.bank(bankId);
        boolean paper = BankType.of(bank.getType()) == BankType.PAPER;
        return R.ok(new BankDetailResp(bank,
                paper ? List.of() : service.courses(bankId),
                paper ? withProgress(service.chapters(bankId, null)) : List.of()));
    }

    @Operation(summary = "章节列表（科目型需传 courseId）")
    @GetMapping("/banks/{bankId}/chapters")
    public R<List<ChapterItem>> chapters(@PathVariable Long bankId, @RequestParam(required = false) Long courseId) {
        return R.ok(withProgress(service.chapters(bankId, courseId)));
    }

    /**
     * 章节 + 当前用户进度（未登录时进度为 0）
     *
     * @param doneCount       已做题数（去重）
     * @param correctCount    最近一次作答正确的题数
     * @param inProgressModes 未完成的会话模式（1 练习 2 背题 3 考试），用于显示“继续”
     * @param lastScore       试卷最近一次考试得分
     */
    public record ChapterItem(Long id, Long bankId, Long courseId, String name, int questionCount,
                              Integer timeLimit, Number totalScore, int doneCount, int correctCount,
                              Set<Integer> inProgressModes, BigDecimal lastScore, List<Section> sections) {
    }

    /**
     * 章节下的“节”：按小节分组（如 单选题 / 论述题 / 材料分析题；题目没填小节时按题型），可单独练习
     *
     * @param inProgress 有未完成的该小节练习，显示“继续”
     */
    public record Section(String name, int questionCount, int doneCount, int correctCount, boolean inProgress) {
    }

    private List<ChapterItem> withProgress(List<Chapter> chapters) {
        Long userId = StpAppUtil.userIdOrNull();
        List<Long> ids = chapters.stream().map(Chapter::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // 按“章节-小节”统计题量和进度（只算上架题，删除 / 下架的题不计入）；小节按在试卷中出现的顺序排列
        Map<Long, List<ChapterSectionStat>> totals = questionMapper.countBySection(ids).stream()
                .sorted(Comparator.comparing(ChapterSectionStat::getOrd, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ChapterSectionStat::getFirstId))
                .collect(Collectors.groupingBy(ChapterSectionStat::getChapterId));
        Map<String, ChapterSectionStat> done = userId == null ? Map.of() : questionMapper.doneBySection(userId, ids).stream()
                .collect(Collectors.toMap(s -> s.getChapterId() + "-" + s.getSectionName(), Function.identity()));
        Map<Long, Set<Integer>> inProgress = userId == null ? Map.of() : practiceService.inProgress(userId, ids);
        Map<Long, Set<String>> sectionsInProgress = userId == null ? Map.of() : practiceService.sectionsInProgress(userId, ids);
        Map<Long, BigDecimal> scores = userId == null ? Map.of() : practiceService.lastExamScores(userId, ids);
        // 试卷满分：选择题分值之和（模拟考试只考选择题）
        Map<Long, BigDecimal> fullScores = scoreService.objectiveTotals(ids);
        return chapters.stream().map(c -> {
            List<Section> sections = new ArrayList<>();
            for (ChapterSectionStat total : totals.getOrDefault(c.getId(), List.of())) {
                ChapterSectionStat d = done.get(c.getId() + "-" + total.getSectionName());
                sections.add(new Section(total.getSectionName(), total.getCnt(),
                        d == null ? 0 : d.getCnt(), d == null ? 0 : d.getCorrect(),
                        sectionsInProgress.getOrDefault(c.getId(), Set.of()).contains(total.getSectionName())));
            }
            return new ChapterItem(c.getId(), c.getBankId(), c.getCourseId(), c.getName(),
                    sections.stream().mapToInt(Section::questionCount).sum(),
                    c.getTimeLimit(), c.getTimeLimit() == null ? null : ScoreService.display(fullScores.get(c.getId())),
                    sections.stream().mapToInt(Section::doneCount).sum(),
                    sections.stream().mapToInt(Section::correctCount).sum(),
                    inProgress.getOrDefault(c.getId(), Set.of()), scores.get(c.getId()), sections);
        }).toList();
    }
}
