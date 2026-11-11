package com.lemon.biz.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.dto.QuestionReqs.QuestionQuery;
import com.lemon.biz.content.dto.QuestionReqs.QuestionSave;
import com.lemon.biz.content.dto.QuestionVO;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.CourseMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.JsonUtil;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理端题目管理：增删改查、批量上下架 / 移动 / 删除，并维护章节与题库的冗余题量。
 * 题目的 bankId / moduleId 一律由所属章节推导，不信任前端传值。
 */
@Service
@RequiredArgsConstructor
public class QuestionAdminService {

    private static final int DEFAULT_DIFFICULTY = 2;

    private final QuestionMapper questionMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final QuestionBankMapper bankMapper;
    private final QuestionCountService countService;
    private final ScoreService scoreService;

    // ==================== 查询 ====================

    public PageResult<QuestionVO> page(QuestionQuery q) {
        LambdaQueryWrapper<Question> w = Wrappers.<Question>lambdaQuery()
                .eq(q.getModuleId() != null, Question::getModuleId, q.getModuleId())
                .eq(q.getBankId() != null, Question::getBankId, q.getBankId())
                .eq(q.getChapterId() != null, Question::getChapterId, q.getChapterId())
                .eq(q.getType() != null, Question::getType, q.getType())
                .eq(q.getStatus() != null, Question::getStatus, q.getStatus())
                .like(StringUtils.hasText(q.getKeyword()), Question::getStem, q.getKeyword())
                .orderByAsc(Question::getChapterId).orderByAsc(Question::getSort).orderByAsc(Question::getId);
        if (q.getCourseId() != null) {
            w.inSql(Question::getChapterId, "SELECT id FROM chapter WHERE deleted = 0 AND course_id = " + q.getCourseId());
        }
        PageResult<QuestionVO> result = PageResult.of(questionMapper.selectPage(q.toPage(), w), QuestionVO::of);
        fillNames(result.getList());
        return result;
    }

    public QuestionVO detail(Long id) {
        QuestionVO vo = QuestionVO.of(get(id));
        fillNames(List.of(vo));
        return vo;
    }

    /** 批量补充章节 / 科目 / 题库名称 */
    private void fillNames(List<QuestionVO> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, Chapter> chapters = byId(chapterMapper.selectByIds(
                list.stream().map(QuestionVO::getChapterId).collect(Collectors.toSet())), Chapter::getId);
        Set<Long> courseIds = chapters.values().stream().map(Chapter::getCourseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of() : byId(courseMapper.selectByIds(courseIds), Course::getId);
        Map<Long, QuestionBank> banks = byId(bankMapper.selectByIds(
                list.stream().map(QuestionVO::getBankId).collect(Collectors.toSet())), QuestionBank::getId);

        for (QuestionVO vo : list) {
            Chapter ch = chapters.get(vo.getChapterId());
            if (ch != null) {
                vo.setChapterName(ch.getName());
                vo.setCourseId(ch.getCourseId());
                Course course = ch.getCourseId() == null ? null : courses.get(ch.getCourseId());
                vo.setCourseName(course == null ? null : course.getName());
            }
            QuestionBank bank = banks.get(vo.getBankId());
            vo.setBankName(bank == null ? null : bank.getName());
        }
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> idGetter) {
        return items.stream().collect(Collectors.toMap(idGetter, Function.identity()));
    }

    // ==================== 新增 / 修改 ====================

    @Transactional
    public Long create(QuestionSave req) {
        Chapter chapter = getChapter(req.chapterId());
        Question q = new Question();
        apply(req, q, chapter);
        if (req.sort() == null) {
            q.setSort(nextSort(chapter.getId()));
        }
        questionMapper.insert(q);
        countService.refreshChapters(List.of(chapter.getId()));
        return q.getId();
    }

    /** 批量新增（Excel 导入用）：每题照常校验，题量最后统一重算一次 */
    @Transactional
    public int createBatch(List<QuestionSave> reqs) {
        Map<Long, Chapter> chapters = new java.util.HashMap<>();
        Map<Long, Integer> nextSorts = new java.util.HashMap<>();
        for (QuestionSave req : reqs) {
            Chapter chapter = chapters.computeIfAbsent(req.chapterId(), this::getChapter);
            Question q = new Question();
            apply(req, q, chapter);
            if (req.sort() == null) {
                int sort = nextSorts.computeIfAbsent(chapter.getId(), this::nextSort);
                q.setSort(sort);
                nextSorts.put(chapter.getId(), sort + 1);
            }
            questionMapper.insert(q);
        }
        countService.refreshChapters(chapters.keySet());
        return reqs.size();
    }

    @Transactional
    public void update(Long id, QuestionSave req) {
        Question q = get(id);
        Long oldChapterId = q.getChapterId();
        Chapter chapter = getChapter(req.chapterId());
        apply(req, q, chapter);
        if (req.sort() == null) {
            q.setSort(Objects.equals(oldChapterId, chapter.getId()) ? q.getSort() : nextSort(chapter.getId()));
        }
        questionMapper.updateById(q);
        countService.refreshChapters(new HashSet<>(List.of(oldChapterId, chapter.getId())));
    }

    /** 把请求体校验、规范化后写入实体 */
    private void apply(QuestionSave req, Question q, Chapter chapter) {
        String stem = HtmlUtil.sanitize(req.stem());
        String analysis = HtmlUtil.isBlank(req.analysis()) ? null : HtmlUtil.sanitize(req.analysis());
        BizException.throwIf(HtmlUtil.isBlank(stem), "题干不能为空");
        QuestionNormalizer.Result normalized =
                QuestionNormalizer.normalize(req.type(), req.options(), req.answer(), analysis);
        QuestionBank bank = BizException.notNull(bankMapper.selectById(chapter.getBankId()), "题库不存在");

        q.setChapterId(chapter.getId());
        q.setBankId(bank.getId());
        q.setModuleId(bank.getModuleId());
        q.setType(req.type());
        q.setStem(stem);
        q.setOptions(normalized.options().isEmpty() ? null : JsonUtil.toJson(normalized.options()));
        q.setAnswer(normalized.answer());
        q.setAnalysis(analysis);
        // 分值按模块规则计算（单选 / 多选每题分值在模块上设置，主观题 0 分），忽略传入的分值
        q.setScore(scoreService.scoreFor(bank.getModuleId(), req.type()));
        q.setDifficulty(Objects.requireNonNullElse(req.difficulty(), DEFAULT_DIFFICULTY));
        q.setSource(req.source());
        q.setSection(StringUtils.hasText(req.section()) ? req.section().strip() : null);
        if (req.sort() != null) {
            q.setSort(req.sort());
        }
        q.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }

    private int nextSort(Long chapterId) {
        Question last = questionMapper.selectOne(Wrappers.<Question>lambdaQuery()
                .select(Question::getSort)
                .eq(Question::getChapterId, chapterId)
                .orderByDesc(Question::getSort)
                .last("LIMIT 1"));
        return last == null || last.getSort() == null ? 1 : last.getSort() + 1;
    }

    // ==================== 批量操作 ====================

    @Transactional
    public void updateStatus(List<Long> ids, int status) {
        Set<Long> chapterIds = chapterIdsOf(ids);
        questionMapper.update(Wrappers.<Question>lambdaUpdate()
                .set(Question::getStatus, status)
                .in(Question::getId, ids));
        countService.refreshChapters(chapterIds);
    }

    @Transactional
    public void move(List<Long> ids, Long targetChapterId) {
        Chapter target = getChapter(targetChapterId);
        QuestionBank bank = BizException.notNull(bankMapper.selectById(target.getBankId()), "题库不存在");
        Set<Long> chapterIds = new HashSet<>(chapterIdsOf(ids));
        chapterIds.add(target.getId());

        // 按原顺序追加到目标章节末尾
        List<Question> questions = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .select(Question::getId, Question::getType)
                .in(Question::getId, ids)
                .orderByAsc(Question::getChapterId).orderByAsc(Question::getSort).orderByAsc(Question::getId));
        int sort = nextSort(target.getId());
        Map<Integer, BigDecimal> scores = new java.util.HashMap<>();
        for (Question q : questions) {
            questionMapper.update(Wrappers.<Question>lambdaUpdate()
                    .set(Question::getChapterId, target.getId())
                    .set(Question::getBankId, bank.getId())
                    .set(Question::getModuleId, bank.getModuleId())
                    // 移到别的模块时分值规则可能不同
                    .set(Question::getScore, scores.computeIfAbsent(q.getType(), t -> scoreService.scoreFor(bank.getModuleId(), t)))
                    .set(Question::getSort, sort++)
                    .eq(Question::getId, q.getId()));
        }
        countService.refreshChapters(chapterIds);
    }

    @Transactional
    public void delete(List<Long> ids) {
        Set<Long> chapterIds = chapterIdsOf(ids);
        questionMapper.deleteByIds(ids);
        countService.refreshChapters(chapterIds);
    }

    /** 这些题目当前所在的章节；有不存在的 id 时报错，避免静默忽略 */
    private Set<Long> chapterIdsOf(List<Long> ids) {
        List<Question> questions = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .select(Question::getId, Question::getChapterId)
                .in(Question::getId, ids));
        Set<Long> found = questions.stream().map(Question::getId).collect(Collectors.toSet());
        List<Long> missing = new ArrayList<>(ids);
        missing.removeAll(found);
        BizException.throwIf(!missing.isEmpty(), "题目不存在：" + missing);
        return questions.stream().map(Question::getChapterId).collect(Collectors.toSet());
    }

    private Question get(Long id) {
        return BizException.notNull(questionMapper.selectById(id), "题目不存在");
    }

    private Chapter getChapter(Long id) {
        return BizException.notNull(chapterMapper.selectById(id), "章节不存在");
    }
}
