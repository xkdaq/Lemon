package com.lemon.biz.collection;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.favorite.entity.UserFavorite;
import com.lemon.biz.favorite.mapper.UserFavoriteMapper;
import com.lemon.biz.practice.dto.PracticeDtos.CollectedQuestion;
import com.lemon.biz.practice.dto.PracticeDtos.Group;
import com.lemon.biz.practice.dto.PracticeDtos.Scope;
import com.lemon.biz.practice.dto.PracticeDtos.SessionVO;
import com.lemon.biz.practice.enums.PracticeMode;
import com.lemon.biz.practice.service.PracticeService;
import com.lemon.biz.wrong.entity.UserWrongQuestion;
import com.lemon.biz.wrong.mapper.UserWrongQuestionMapper;
import com.lemon.common.BizException;
import com.lemon.common.HtmlUtil;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏集与错题集：分组统计、列表、移除、按范围发起练习。
 * 两者结构相同（都带冗余的 module / bank / chapter），差异只在数据来源和排序。
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    /** 一次练习最多取的题数，避免会话过大 */
    public static final int MAX_PRACTICE = 200;
    private static final int STEM_PREVIEW = 80;

    public enum Kind { FAVORITE, WRONG }

    private final UserFavoriteMapper favoriteMapper;
    private final UserWrongQuestionMapper wrongMapper;
    private final QuestionMapper questionMapper;
    private final ExamModuleMapper moduleMapper;
    private final QuestionBankMapper bankMapper;
    private final ChapterMapper chapterMapper;
    private final PracticeService practiceService;

    /** 一条收藏 / 错题的公共视图 */
    private record Item(Long questionId, Long moduleId, Long bankId, Long chapterId, java.time.LocalDateTime time,
                        UserWrongQuestion wrong) {
    }

    // ==================== 分组 ====================

    public List<Group> groups(Long userId, Kind kind) {
        List<Map<String, Object>> rows = kind == Kind.FAVORITE
                ? favoriteMapper.selectMaps(groupQuery(new QueryWrapper<UserFavorite>().eq("user_id", userId)))
                : wrongMapper.selectMaps(groupQuery(new QueryWrapper<UserWrongQuestion>()
                .eq("user_id", userId).eq("status", Status.ON)));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> moduleIds = rows.stream().map(r -> toLong(r.get("module_id"))).collect(Collectors.toSet());
        Set<Long> bankIds = rows.stream().map(r -> toLong(r.get("bank_id"))).collect(Collectors.toSet());
        Set<Long> chapterIds = rows.stream().map(r -> toLong(r.get("chapter_id"))).collect(Collectors.toSet());
        Map<Long, ExamModule> modules = byId(moduleMapper.selectByIds(moduleIds), ExamModule::getId);
        Map<Long, QuestionBank> banks = byId(bankMapper.selectByIds(bankIds), QuestionBank::getId);
        Map<Long, Chapter> chapters = byId(chapterMapper.selectByIds(chapterIds), Chapter::getId);

        // module -> bank -> chapter -> count
        Map<Long, Map<Long, Map<Long, Long>>> tree = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            tree.computeIfAbsent(toLong(r.get("module_id")), k -> new LinkedHashMap<>())
                    .computeIfAbsent(toLong(r.get("bank_id")), k -> new LinkedHashMap<>())
                    .merge(toLong(r.get("chapter_id")), toLong(r.get("cnt")), Long::sum);
        }
        List<Group> result = new ArrayList<>();
        tree.forEach((moduleId, bankMap) -> {
            List<Group> bankGroups = new ArrayList<>();
            bankMap.forEach((bankId, chapterMap) -> {
                List<Group> chapterGroups = chapterMap.entrySet().stream()
                        .map(e -> new Group(e.getKey(), nameOf(chapters.get(e.getKey()), Chapter::getName), e.getValue(), null))
                        .sorted(Comparator.comparing(g -> sortOf(chapters.get(g.id()))))
                        .toList();
                bankGroups.add(new Group(bankId, nameOf(banks.get(bankId), QuestionBank::getName),
                        chapterGroups.stream().mapToLong(Group::count).sum(), chapterGroups));
            });
            result.add(new Group(moduleId, nameOf(modules.get(moduleId), ExamModule::getName),
                    bankGroups.stream().mapToLong(Group::count).sum(), bankGroups));
        });
        result.sort(Comparator.comparing(g -> modules.containsKey(g.id()) ? modules.get(g.id()).getSort() : Integer.MAX_VALUE));
        return result;
    }

    private static <T> QueryWrapper<T> groupQuery(QueryWrapper<T> w) {
        return w.select("module_id", "bank_id", "chapter_id", "COUNT(*) AS cnt").groupBy("module_id", "bank_id", "chapter_id");
    }

    // ==================== 列表 ====================

    public PageResult<CollectedQuestion> list(Long userId, Kind kind, Scope scope, long pageNum, long pageSize) {
        Page<Item> page;
        if (kind == Kind.FAVORITE) {
            var p = favoriteMapper.selectPage(Page.of(pageNum, pageSize), Wrappers.<UserFavorite>lambdaQuery()
                    .eq(UserFavorite::getUserId, userId)
                    .eq(scope.moduleId() != null, UserFavorite::getModuleId, scope.moduleId())
                    .eq(scope.bankId() != null, UserFavorite::getBankId, scope.bankId())
                    .eq(scope.chapterId() != null, UserFavorite::getChapterId, scope.chapterId())
                    .orderByDesc(UserFavorite::getId));
            page = convert(p, f -> new Item(f.getQuestionId(), f.getModuleId(), f.getBankId(), f.getChapterId(), f.getCreatedAt(), null));
        } else {
            var p = wrongMapper.selectPage(Page.of(pageNum, pageSize), wrongScope(userId, scope)
                    .orderByDesc(UserWrongQuestion::getLastWrongAt));
            page = convert(p, w -> new Item(w.getQuestionId(), w.getModuleId(), w.getBankId(), w.getChapterId(), w.getLastWrongAt(), w));
        }
        List<Item> items = page.getRecords();
        Map<Long, Question> questions = items.isEmpty() ? Map.of() : byId(questionMapper.selectByIds(
                items.stream().map(Item::questionId).toList()), Question::getId);
        Map<Long, ExamModule> modules = items.isEmpty() ? Map.of() : byId(moduleMapper.selectByIds(
                items.stream().map(Item::moduleId).collect(Collectors.toSet())), ExamModule::getId);
        Map<Long, QuestionBank> banks = items.isEmpty() ? Map.of() : byId(bankMapper.selectByIds(
                items.stream().map(Item::bankId).collect(Collectors.toSet())), QuestionBank::getId);
        Map<Long, Chapter> chapters = items.isEmpty() ? Map.of() : byId(chapterMapper.selectByIds(
                items.stream().map(Item::chapterId).collect(Collectors.toSet())), Chapter::getId);

        List<CollectedQuestion> list = items.stream().map(i -> {
            Question q = questions.get(i.questionId());
            UserWrongQuestion w = i.wrong();
            String stem = q == null ? "该题已被删除" : preview(q.getStem());
            return new CollectedQuestion(i.questionId(), q == null ? null : q.getType(), stem,
                    nameOf(modules.get(i.moduleId()), ExamModule::getName), nameOf(banks.get(i.bankId()), QuestionBank::getName),
                    nameOf(chapters.get(i.chapterId()), Chapter::getName), q != null && q.getStatus() == Status.ON,
                    w == null ? null : w.getWrongCount(), w == null ? null : w.getRightStreak(),
                    w == null ? null : w.getLastWrongAnswer(), i.time());
        }).toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private static <S> Page<Item> convert(Page<S> p, Function<S, Item> fn) {
        Page<Item> out = Page.of(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(p.getRecords().stream().map(fn).toList());
        return out;
    }

    private static String preview(String html) {
        String text = HtmlUtil.htmlToText(html).replaceAll("\\s+", " ").strip();
        return text.length() > STEM_PREVIEW ? text.substring(0, STEM_PREVIEW) + "…" : text;
    }

    // ==================== 移除 ====================

    public void removeWrong(Long userId, Long questionId) {
        wrongMapper.update(Wrappers.<UserWrongQuestion>lambdaUpdate()
                .set(UserWrongQuestion::getStatus, Status.OFF)
                .eq(UserWrongQuestion::getUserId, userId)
                .eq(UserWrongQuestion::getQuestionId, questionId));
    }

    // ==================== 练习 ====================

    /**
     * 按范围发起错题重练 / 收藏练习。只取仍上架的题；错题按最近答错倒序，收藏按收藏时间倒序。
     *
     * @param firstQuestionId 可选：从这道题开始（列表里点某题进入时），它会排到第一个
     */
    @Transactional
    public SessionVO practice(Long userId, Kind kind, Scope scope, Long firstQuestionId) {
        List<Long> ids = kind == Kind.FAVORITE
                ? favoriteMapper.selectList(Wrappers.<UserFavorite>lambdaQuery()
                        .select(UserFavorite::getQuestionId)
                        .eq(UserFavorite::getUserId, userId)
                        .eq(scope.moduleId() != null, UserFavorite::getModuleId, scope.moduleId())
                        .eq(scope.bankId() != null, UserFavorite::getBankId, scope.bankId())
                        .eq(scope.chapterId() != null, UserFavorite::getChapterId, scope.chapterId())
                        .orderByDesc(UserFavorite::getId))
                .stream().map(UserFavorite::getQuestionId).toList()
                : wrongMapper.selectList(wrongScope(userId, scope)
                        .select(UserWrongQuestion::getQuestionId)
                        .orderByDesc(UserWrongQuestion::getLastWrongAt))
                .stream().map(UserWrongQuestion::getQuestionId).toList();

        Set<Long> available = ids.isEmpty() ? Set.of() : questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .select(Question::getId).in(Question::getId, ids).eq(Question::getStatus, Status.ON))
                .stream().map(Question::getId).collect(Collectors.toSet());
        List<Long> ordered = new ArrayList<>(ids.stream().filter(available::contains).toList());
        if (firstQuestionId != null && ordered.remove(firstQuestionId)) {
            ordered.add(0, firstQuestionId);
        }
        BizException.throwIf(ordered.isEmpty(), kind == Kind.FAVORITE ? "收藏集里没有可练习的题目" : "错题集里没有可练习的题目");
        if (ordered.size() > MAX_PRACTICE) {
            ordered = ordered.subList(0, MAX_PRACTICE);
        }
        return practiceService.startScoped(userId, kind == Kind.FAVORITE ? PracticeMode.FAVORITE : PracticeMode.WRONG, ordered);
    }

    private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserWrongQuestion> wrongScope(Long userId, Scope scope) {
        return Wrappers.<UserWrongQuestion>lambdaQuery()
                .eq(UserWrongQuestion::getUserId, userId)
                .eq(UserWrongQuestion::getStatus, Status.ON)
                .eq(scope.moduleId() != null, UserWrongQuestion::getModuleId, scope.moduleId())
                .eq(scope.bankId() != null, UserWrongQuestion::getBankId, scope.bankId())
                .eq(scope.chapterId() != null, UserWrongQuestion::getChapterId, scope.chapterId());
    }

    // ==================== 工具 ====================

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static <T> String nameOf(T entity, Function<T, String> name) {
        return entity == null ? "（已删除）" : name.apply(entity);
    }

    private static int sortOf(Chapter c) {
        return c == null || c.getSort() == null ? Integer.MAX_VALUE : c.getSort();
    }
}
