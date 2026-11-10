package com.lemon.biz.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.BankCategory;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Course;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.BankCategoryMapper;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.CourseMapper;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.operation.entity.Notice;
import com.lemon.biz.operation.mapper.NoticeMapper;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户端：内容目录浏览（只返回上架数据）。
 * TODO M3：列表接口附带登录用户的进度 / 正确率；目录数据加 Redis 缓存。
 */
@Service
@RequiredArgsConstructor
public class ContentQueryService {

    private final ExamModuleMapper moduleMapper;
    private final QuestionBankMapper bankMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final NoticeMapper noticeMapper;
    private final BankCategoryMapper categoryMapper;

    public List<ExamModule> modules() {
        return moduleMapper.selectList(Wrappers.<ExamModule>lambdaQuery()
                .eq(ExamModule::getStatus, Status.ON)
                .orderByAsc(ExamModule::getSort).orderByAsc(ExamModule::getId));
    }

    public List<Notice> notices() {
        return noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, Status.ON)
                .orderByAsc(Notice::getSort).orderByDesc(Notice::getId)
                .last("LIMIT 5"));
    }

    /** 模块下上架的分类 */
    public List<BankCategory> categories(Long moduleId) {
        return categoryMapper.selectList(Wrappers.<BankCategory>lambdaQuery()
                .eq(BankCategory::getModuleId, moduleId)
                .eq(BankCategory::getStatus, Status.ON)
                .orderByAsc(BankCategory::getSort).orderByAsc(BankCategory::getId));
    }

    /** 模块下上架的题库；所属分类下架或已删除的题库不显示，未分类的照常显示 */
    public List<QuestionBank> banks(Long moduleId) {
        return bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .eq(QuestionBank::getModuleId, moduleId)
                .eq(QuestionBank::getStatus, Status.ON)
                .and(w -> w.isNull(QuestionBank::getCategoryId).or()
                        .inSql(QuestionBank::getCategoryId, "SELECT id FROM bank_category WHERE status = 1 AND deleted = 0"))
                .orderByAsc(QuestionBank::getSort).orderByDesc(QuestionBank::getId));
    }

    public QuestionBank bank(Long bankId) {
        QuestionBank bank = bankMapper.selectById(bankId);
        BizException.throwIf(bank == null || bank.getStatus() != Status.ON, "题库不存在或已下架");
        return bank;
    }

    public List<Course> courses(Long bankId) {
        bank(bankId);
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getBankId, bankId)
                .eq(Course::getStatus, Status.ON)
                .orderByAsc(Course::getSort).orderByAsc(Course::getId));
    }

    public List<Chapter> chapters(Long bankId, Long courseId) {
        bank(bankId);
        return chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getBankId, bankId)
                .eq(courseId != null, Chapter::getCourseId, courseId)
                .eq(Chapter::getStatus, Status.ON)
                .orderByAsc(Chapter::getSort).orderByAsc(Chapter::getId));
    }
}
