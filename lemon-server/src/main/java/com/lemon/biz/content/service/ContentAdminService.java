package com.lemon.biz.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.dto.ContentReqs.BankQuery;
import com.lemon.biz.content.dto.ContentReqs.BankSave;
import com.lemon.biz.content.dto.ContentReqs.ChapterSave;
import com.lemon.biz.content.dto.ContentReqs.CourseSave;
import com.lemon.biz.content.dto.ContentReqs.ModuleSave;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.BankType;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.CourseMapper;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端：模块 / 题库 / 科目 / 章节的增删改查，负责维护层级规则：
 * <ul>
 *   <li>科目型题库：章节必须挂在本题库的某个科目下</li>
 *   <li>试卷型题库：不能建科目，章节 courseId 为空</li>
 *   <li>有下级数据时不允许删除上级</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContentAdminService {

    private final ExamModuleMapper moduleMapper;
    private final QuestionBankMapper bankMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final QuestionMapper questionMapper;
    private final QuestionCountService countService;

    // ==================== 目录树 ====================

    /**
     * 题库 → 科目 → 章节（试卷型为 题库 → 章节）整棵树，供管理端级联选择器使用。
     * 数据量是“题库/章节”级别（几百个节点），一次性返回即可。
     */
    public List<TreeNode> tree() {
        Map<Long, String> moduleNames = listModules().stream()
                .collect(Collectors.toMap(ExamModule::getId, ExamModule::getName));
        List<QuestionBank> banks = bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .orderByAsc(QuestionBank::getModuleId).orderByAsc(QuestionBank::getSort).orderByDesc(QuestionBank::getId));
        Map<Long, List<Course>> coursesByBank = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                        .orderByAsc(Course::getSort).orderByAsc(Course::getId))
                .stream().collect(Collectors.groupingBy(Course::getBankId));
        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .orderByAsc(Chapter::getSort).orderByAsc(Chapter::getId));
        Map<Long, List<Chapter>> chaptersByBank = chapters.stream()
                .filter(c -> c.getCourseId() == null).collect(Collectors.groupingBy(Chapter::getBankId));
        Map<Long, List<Chapter>> chaptersByCourse = chapters.stream()
                .filter(c -> c.getCourseId() != null).collect(Collectors.groupingBy(Chapter::getCourseId));

        return banks.stream().map(b -> {
            List<TreeNode> children = BankType.of(b.getType()) == BankType.COURSE
                    ? coursesByBank.getOrDefault(b.getId(), List.of()).stream()
                    .map(c -> new TreeNode("course", c.getId(), c.getName(), c.getStatus(), null,
                            chaptersByCourse.getOrDefault(c.getId(), List.of()).stream().map(ContentAdminService::chapterNode).toList()))
                    .toList()
                    : chaptersByBank.getOrDefault(b.getId(), List.of()).stream().map(ContentAdminService::chapterNode).toList();
            String label = moduleNames.getOrDefault(b.getModuleId(), "?") + " / " + b.getName();
            return new TreeNode("bank", b.getId(), label, b.getStatus(), b.getType(), children);
        }).toList();
    }

    private static TreeNode chapterNode(Chapter c) {
        return new TreeNode("chapter", c.getId(), c.getName(), c.getStatus(), null, null);
    }

    /**
     * 目录树节点
     *
     * @param kind     bank / course / chapter
     * @param bankType 仅 bank 节点有值：1 科目型 2 试卷型
     * @param children 章节节点为 null（叶子）
     */
    public record TreeNode(String kind, Long id, String label, Integer status, Integer bankType, List<TreeNode> children) {
    }

    // ==================== 模块 ====================

    public List<ExamModule> listModules() {
        return moduleMapper.selectList(Wrappers.<ExamModule>lambdaQuery()
                .orderByAsc(ExamModule::getSort).orderByAsc(ExamModule::getId));
    }

    public Long createModule(ModuleSave req) {
        ExamModule m = new ExamModule();
        copy(req, m);
        moduleMapper.insert(m);
        return m.getId();
    }

    public void updateModule(Long id, ModuleSave req) {
        ExamModule m = BizException.notNull(moduleMapper.selectById(id), "模块不存在");
        copy(req, m);
        moduleMapper.updateById(m);
    }

    public void deleteModule(Long id) {
        BizException.throwIf(bankMapper.exists(Wrappers.<QuestionBank>lambdaQuery().eq(QuestionBank::getModuleId, id)),
                "该模块下还有题库，不能删除");
        moduleMapper.deleteById(id);
    }

    private static void copy(ModuleSave req, ExamModule m) {
        m.setName(req.name());
        m.setIcon(req.icon());
        m.setSort(Objects.requireNonNullElse(req.sort(), 0));
        m.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }

    // ==================== 题库 ====================

    public PageResult<QuestionBank> pageBanks(BankQuery q) {
        return PageResult.of(bankMapper.selectPage(q.toPage(), Wrappers.<QuestionBank>lambdaQuery()
                .eq(q.getModuleId() != null, QuestionBank::getModuleId, q.getModuleId())
                .eq(q.getType() != null, QuestionBank::getType, q.getType())
                .like(StringUtils.hasText(q.getKeyword()), QuestionBank::getName, q.getKeyword())
                .orderByAsc(QuestionBank::getSort).orderByDesc(QuestionBank::getId)));
    }

    public QuestionBank getBank(Long id) {
        return BizException.notNull(bankMapper.selectById(id), "题库不存在");
    }

    public Long createBank(BankSave req) {
        BizException.notNull(moduleMapper.selectById(req.moduleId()), "模块不存在");
        BankType.of(req.type());
        QuestionBank b = new QuestionBank();
        b.setType(req.type());
        b.setQuestionCount(0);
        copy(req, b);
        bankMapper.insert(b);
        return b.getId();
    }

    public void updateBank(Long id, BankSave req) {
        QuestionBank b = getBank(id);
        BizException.throwIf(!Objects.equals(b.getType(), req.type()), "题库类型创建后不能修改");
        BizException.throwIf(!Objects.equals(b.getModuleId(), req.moduleId())
                && moduleMapper.selectById(req.moduleId()) == null, "模块不存在");
        copy(req, b);
        bankMapper.updateById(b);
    }

    public void updateBankStatus(Long id, int status) {
        QuestionBank b = getBank(id);
        b.setStatus(status);
        bankMapper.updateById(b);
    }

    public void deleteBank(Long id) {
        BizException.throwIf(chapterMapper.exists(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getBankId, id))
                || courseMapper.exists(Wrappers.<Course>lambdaQuery().eq(Course::getBankId, id)),
                "该题库下还有科目或章节，不能删除");
        bankMapper.deleteById(id);
    }

    private static void copy(BankSave req, QuestionBank b) {
        b.setModuleId(req.moduleId());
        b.setName(req.name());
        b.setCover(req.cover());
        b.setIntro(req.intro());
        b.setSort(Objects.requireNonNullElse(req.sort(), 0));
        b.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }

    // ==================== 科目 ====================

    public List<Course> listCourses(Long bankId) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getBankId, bankId)
                .orderByAsc(Course::getSort).orderByAsc(Course::getId));
    }

    public Long createCourse(CourseSave req) {
        QuestionBank bank = getBank(req.bankId());
        BizException.throwIf(BankType.of(bank.getType()) != BankType.COURSE, "试卷型题库不能创建科目");
        Course c = new Course();
        copy(req, c);
        courseMapper.insert(c);
        return c.getId();
    }

    public void updateCourse(Long id, CourseSave req) {
        Course c = BizException.notNull(courseMapper.selectById(id), "科目不存在");
        BizException.throwIf(!Objects.equals(c.getBankId(), req.bankId()), "科目不能移动到其他题库");
        copy(req, c);
        courseMapper.updateById(c);
    }

    public void deleteCourse(Long id) {
        BizException.throwIf(chapterMapper.exists(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getCourseId, id)),
                "该科目下还有章节，不能删除");
        courseMapper.deleteById(id);
    }

    private static void copy(CourseSave req, Course c) {
        c.setBankId(req.bankId());
        c.setName(req.name());
        c.setSort(Objects.requireNonNullElse(req.sort(), 0));
        c.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
    }

    // ==================== 章节 ====================

    public List<Chapter> listChapters(Long bankId, Long courseId) {
        return chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getBankId, bankId)
                .eq(courseId != null, Chapter::getCourseId, courseId)
                .orderByAsc(Chapter::getSort).orderByAsc(Chapter::getId));
    }

    public Long createChapter(ChapterSave req) {
        validateChapter(req);
        Chapter c = new Chapter();
        c.setQuestionCount(0);
        copy(req, c);
        chapterMapper.insert(c);
        return c.getId();
    }

    public void updateChapter(Long id, ChapterSave req) {
        Chapter c = BizException.notNull(chapterMapper.selectById(id), "章节不存在");
        BizException.throwIf(!Objects.equals(c.getBankId(), req.bankId()), "章节不能移动到其他题库");
        validateChapter(req);
        boolean statusChanged = !Objects.equals(c.getStatus(), req.status() == null ? Status.ON : req.status());
        copy(req, c);
        chapterMapper.updateById(c);
        if (statusChanged) {
            // 章节上下架影响题库题量
            countService.refreshBanks(List.of(c.getBankId()));
        }
    }

    public void deleteChapter(Long id) {
        Chapter c = BizException.notNull(chapterMapper.selectById(id), "章节不存在");
        BizException.throwIf(questionMapper.exists(Wrappers.<Question>lambdaQuery().eq(Question::getChapterId, id)),
                "该章节下还有题目，不能删除");
        chapterMapper.deleteById(id);
        countService.refreshBanks(List.of(c.getBankId()));
    }

    private void validateChapter(ChapterSave req) {
        QuestionBank bank = getBank(req.bankId());
        if (BankType.of(bank.getType()) == BankType.COURSE) {
            BizException.throwIf(req.courseId() == null, "科目型题库的章节必须选择科目");
            Course course = BizException.notNull(courseMapper.selectById(req.courseId()), "科目不存在");
            BizException.throwIf(!Objects.equals(course.getBankId(), bank.getId()), "科目不属于该题库");
        } else {
            BizException.throwIf(req.courseId() != null, "试卷型题库的章节不能选择科目");
        }
    }

    private static void copy(ChapterSave req, Chapter c) {
        c.setBankId(req.bankId());
        c.setCourseId(req.courseId());
        c.setName(req.name());
        c.setSort(Objects.requireNonNullElse(req.sort(), 0));
        c.setStatus(Objects.requireNonNullElse(req.status(), Status.ON));
        c.setTimeLimit(req.timeLimit());
        c.setTotalScore(req.totalScore());
    }
}
