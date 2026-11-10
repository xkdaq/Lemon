package com.lemon.biz.paper.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.paper.dto.PaperRuleDtos.PaperRuleSave;
import com.lemon.biz.paper.dto.PaperRuleDtos.PaperRuleVO;
import com.lemon.biz.paper.dto.PaperRuleDtos.PoolStat;
import com.lemon.biz.paper.dto.PaperRuleDtos.PreviewQuestion;
import com.lemon.biz.paper.dto.PaperRuleDtos.RuleItem;
import com.lemon.biz.paper.entity.PaperRule;
import com.lemon.biz.paper.mapper.PaperRuleMapper;
import com.lemon.biz.paper.service.PaperPicker.Candidate;
import com.lemon.biz.punch.entity.PunchCamp;
import com.lemon.biz.punch.entity.PunchSchedule;
import com.lemon.biz.punch.mapper.PunchCampMapper;
import com.lemon.biz.punch.mapper.PunchScheduleMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.JsonUtil;
import com.lemon.common.Status;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

/**
 * 组卷规则：管理端增删改查，以及按规则抽题（打卡等场景调用）。
 * 抽题只从上架章节里的上架题目中抽；规则保存时校验题量是否够，抽题时题量不够就有多少抽多少。
 */
@Service
@RequiredArgsConstructor
public class PaperRuleService {

    private static final TypeReference<List<Long>> IDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<RuleItem>> ITEMS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Integer>> RATIO_TYPE = new TypeReference<>() {
    };
    private static final int STEM_PREVIEW_LENGTH = 80;

    private final PaperRuleMapper ruleMapper;
    private final QuestionBankMapper bankMapper;
    private final ChapterMapper chapterMapper;
    private final QuestionMapper questionMapper;
    private final PunchCampMapper campMapper;
    private final PunchScheduleMapper scheduleMapper;

    // ==================== 管理端 ====================

    public List<PaperRuleVO> list(Long bankId) {
        List<PaperRule> rules = ruleMapper.selectList(Wrappers.<PaperRule>lambdaQuery()
                .eq(bankId != null, PaperRule::getBankId, bankId)
                .orderByDesc(PaperRule::getId));
        Set<Long> bankIds = rules.stream().map(PaperRule::getBankId).collect(Collectors.toSet());
        Map<Long, String> bankNames = bankIds.isEmpty() ? Map.of() : bankMapper.selectByIds(bankIds).stream()
                .collect(Collectors.toMap(QuestionBank::getId, QuestionBank::getName));
        return rules.stream().map(r -> toVO(r, bankNames.get(r.getBankId()))).toList();
    }

    public PaperRuleVO get(Long id) {
        PaperRule r = mustGet(id);
        QuestionBank bank = bankMapper.selectById(r.getBankId());
        return toVO(r, bank == null ? null : bank.getName());
    }

    public Long create(PaperRuleSave req) {
        PaperRule r = new PaperRule();
        fill(r, req);
        ruleMapper.insert(r);
        return r.getId();
    }

    public void update(Long id, PaperRuleSave req) {
        PaperRule r = mustGet(id);
        fill(r, req);
        ruleMapper.updateById(r);
    }

    public void delete(Long id) {
        mustGet(id);
        BizException.throwIf(campMapper.exists(Wrappers.<PunchCamp>lambdaQuery().eq(PunchCamp::getPaperRuleId, id)),
                "有打卡活动正在使用该规则，不能删除");
        BizException.throwIf(scheduleMapper.exists(Wrappers.<PunchSchedule>lambdaQuery()
                .eq(PunchSchedule::getPaperRuleId, id)), "有打卡日程正在使用该规则，不能删除");
        ruleMapper.deleteById(id);
    }

    /** 抽题范围内各题型的可用题量（按难度细分） */
    public List<PoolStat> poolStats(Long bankId, List<Long> chapterIds) {
        List<Candidate> pool = loadPool(bankId, chapterIds == null || chapterIds.isEmpty() ? null : chapterIds);
        return java.util.Arrays.stream(QuestionType.values()).map(t -> {
            int[] byDifficulty = new int[3];
            pool.stream().filter(c -> c.type() == t.getCode())
                    .forEach(c -> byDifficulty[PaperPicker.level(c.difficulty())]++);
            return new PoolStat(t.getCode(), t.getLabel(), byDifficulty[0] + byDifficulty[1] + byDifficulty[2],
                    byDifficulty);
        }).toList();
    }

    /** 试抽一次，看看抽出来的题 */
    public List<PreviewQuestion> preview(Long id) {
        List<Long> ids = pick(id, System.nanoTime());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> questions = questionMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Set<Long> chapterIds = questions.values().stream().map(Question::getChapterId).collect(Collectors.toSet());
        Map<Long, String> chapterNames = chapterMapper.selectByIds(chapterIds).stream()
                .collect(Collectors.toMap(Chapter::getId, Chapter::getName));
        return ids.stream().map(questions::get).filter(Objects::nonNull).map(q -> {
            String text = HtmlUtil.htmlToText(q.getStem()).replaceAll("\\s+", " ");
            if (text.length() > STEM_PREVIEW_LENGTH) {
                text = text.substring(0, STEM_PREVIEW_LENGTH) + "…";
            }
            return new PreviewQuestion(q.getId(), q.getType(), q.getDifficulty(),
                    chapterNames.get(q.getChapterId()), text);
        }).toList();
    }

    // ==================== 抽题 ====================

    /**
     * 按规则抽题。同一个种子、题库内容不变时结果相同。
     *
     * @return 题目 id，按规则里题型的顺序
     */
    public List<Long> pick(Long ruleId, long seed) {
        PaperRule r = mustGet(ruleId);
        List<Candidate> pool = loadPool(r.getBankId(), chapterIds(r));
        return PaperPicker.pick(pool, items(r), ratio(r), seed);
    }

    /** 规则名称：id → 名称 */
    public Map<Long, String> names(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ruleMapper.selectByIds(ids).stream().collect(Collectors.toMap(PaperRule::getId, PaperRule::getName));
    }

    /** 每次抽题的题数：规则 id → 题数 */
    public Map<Long, Integer> questionCounts(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ruleMapper.selectByIds(ids).stream().collect(Collectors.toMap(PaperRule::getId,
                r -> items(r).stream().mapToInt(RuleItem::count).sum()));
    }

    public PaperRule mustGet(Long id) {
        return BizException.notNull(ruleMapper.selectById(id), "组卷规则不存在");
    }

    // ==================== 工具 ====================

    private void fill(PaperRule r, PaperRuleSave req) {
        BizException.notNull(bankMapper.selectById(req.bankId()), "题库不存在");
        List<Long> chapterIds = req.chapterIds() == null || req.chapterIds().isEmpty() ? null
                : req.chapterIds().stream().distinct().toList();
        if (chapterIds != null) {
            long valid = chapterMapper.selectCount(Wrappers.<Chapter>lambdaQuery()
                    .eq(Chapter::getBankId, req.bankId()).in(Chapter::getId, chapterIds));
            BizException.throwIf(valid != chapterIds.size(), "有章节不属于所选题库");
        }
        Set<Integer> types = new HashSet<>();
        for (RuleItem item : req.items()) {
            BizException.throwIf(!types.add(item.type()), "同一题型只能设置一次");
        }
        List<Integer> ratio = req.difficultyRatio();
        if (ratio != null) {
            BizException.throwIf(ratio.stream().mapToInt(Integer::intValue).sum() != 100, "难度比例合计需为 100%");
        }

        // 题量校验：每种题型的可用题量要够
        Map<Integer, PoolStat> stats = poolStats(req.bankId(), chapterIds).stream()
                .collect(Collectors.toMap(PoolStat::type, Function.identity()));
        for (RuleItem item : req.items()) {
            PoolStat s = stats.get(item.type());
            BizException.throwIf(s.total() < item.count(),
                    String.format("%s只有 %d 道，不够抽 %d 道", s.label(), s.total(), item.count()));
        }

        r.setBankId(req.bankId());
        r.setName(req.name().strip());
        r.setChapterIds(chapterIds == null ? null : JsonUtil.toJson(chapterIds));
        r.setItems(JsonUtil.toJson(req.items()));
        r.setDifficultyRatio(ratio == null ? null : JsonUtil.toJson(ratio));
        r.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }

    /** 抽题池：题库里上架章节的上架题；chapterIds 为 null 表示整个题库 */
    private List<Candidate> loadPool(Long bankId, List<Long> chapterIds) {
        List<Long> onChapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                        .select(Chapter::getId)
                        .eq(Chapter::getBankId, bankId)
                        .eq(Chapter::getStatus, Status.ON)
                        .in(chapterIds != null, Chapter::getId, chapterIds))
                .stream().map(Chapter::getId).toList();
        if (onChapters.isEmpty()) {
            return List.of();
        }
        return questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .select(Question::getId, Question::getType, Question::getDifficulty)
                        .in(Question::getChapterId, onChapters)
                        .eq(Question::getStatus, Status.ON))
                .stream()
                .map(q -> new Candidate(q.getId(), q.getType(), Objects.requireNonNullElse(q.getDifficulty(), 2)))
                .toList();
    }

    private PaperRuleVO toVO(PaperRule r, String bankName) {
        List<RuleItem> items = items(r);
        int count = items.stream().mapToInt(RuleItem::count).sum();
        return new PaperRuleVO(r.getId(), r.getBankId(), bankName, r.getName(), chapterIds(r), items, ratio(r),
                count, r.getStatus(), r.getUpdatedAt());
    }

    private static List<Long> chapterIds(PaperRule r) {
        return JsonUtil.parse(r.getChapterIds(), IDS_TYPE);
    }

    private static List<RuleItem> items(PaperRule r) {
        List<RuleItem> items = JsonUtil.parse(r.getItems(), ITEMS_TYPE);
        return items == null ? List.of() : items;
    }

    private static List<Integer> ratio(PaperRule r) {
        return JsonUtil.parse(r.getDifficultyRatio(), RATIO_TYPE);
    }
}
