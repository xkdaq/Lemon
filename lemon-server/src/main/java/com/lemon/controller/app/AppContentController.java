package com.lemon.controller.app;

import com.lemon.biz.content.entity.BankCategory;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.BankType;
import com.lemon.biz.content.service.ContentQueryService;
import com.lemon.biz.practice.dto.PracticeDtos.LastSession;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.practice.dto.ChapterTypeStat;
import com.lemon.biz.practice.service.PracticeService;
import com.lemon.security.StpAppUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.lemon.biz.operation.entity.Banner;
import com.lemon.biz.operation.entity.Notice;
import com.lemon.biz.operation.service.BannerService;
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
                              Integer timeLimit, Integer totalScore, int doneCount, int correctCount,
                              Set<Integer> inProgressModes, BigDecimal lastScore, List<Section> sections) {
    }

    /**
     * 章节下的“节”：按题型分组（单选题 / 多选题 / 分析题），可单独练习
     *
     * @param inProgress 有未完成的该题型练习，显示“继续”
     */
    public record Section(int type, String name, int questionCount, int doneCount, int correctCount, boolean inProgress) {
    }

    private List<ChapterItem> withProgress(List<Chapter> chapters) {
        Long userId = StpAppUtil.userIdOrNull();
        List<Long> ids = chapters.stream().map(Chapter::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // 按“章节-题型”统计题量和进度（只算上架题，删除 / 下架的题不计入）
        Map<String, ChapterTypeStat> totals = questionMapper.countByType(ids).stream()
                .collect(Collectors.toMap(s -> s.getChapterId() + "-" + s.getType(), Function.identity()));
        Map<String, ChapterTypeStat> done = userId == null ? Map.of() : questionMapper.doneByType(userId, ids).stream()
                .collect(Collectors.toMap(s -> s.getChapterId() + "-" + s.getType(), Function.identity()));
        Map<Long, Set<Integer>> inProgress = userId == null ? Map.of() : practiceService.inProgress(userId, ids);
        Map<Long, Set<Integer>> typesInProgress = userId == null ? Map.of() : practiceService.typesInProgress(userId, ids);
        Map<Long, BigDecimal> scores = userId == null ? Map.of() : practiceService.lastExamScores(userId, ids);
        return chapters.stream().map(c -> {
            List<Section> sections = new ArrayList<>();
            for (QuestionType t : QuestionType.values()) {
                ChapterTypeStat total = totals.get(c.getId() + "-" + t.getCode());
                if (total == null) {
                    continue;
                }
                ChapterTypeStat d = done.get(c.getId() + "-" + t.getCode());
                sections.add(new Section(t.getCode(), t.sectionLabel(), total.getCnt(),
                        d == null ? 0 : d.getCnt(), d == null ? 0 : d.getCorrect(),
                        typesInProgress.getOrDefault(c.getId(), Set.of()).contains(t.getCode())));
            }
            return new ChapterItem(c.getId(), c.getBankId(), c.getCourseId(), c.getName(),
                    sections.stream().mapToInt(Section::questionCount).sum(),
                    c.getTimeLimit(), c.getTotalScore(),
                    sections.stream().mapToInt(Section::doneCount).sum(),
                    sections.stream().mapToInt(Section::correctCount).sum(),
                    inProgress.getOrDefault(c.getId(), Set.of()), scores.get(c.getId()), sections);
        }).toList();
    }
}
