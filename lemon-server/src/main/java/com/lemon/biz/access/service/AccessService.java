package com.lemon.biz.access.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.access.entity.UserModuleAccess;
import com.lemon.biz.access.mapper.UserModuleAccessMapper;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.operation.service.SysConfigService;
import com.lemon.common.BizException;
import com.lemon.common.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模块权限判断：题库按模块收费，激活某个模块后，模块下所有题库都能刷。
 * 没激活的模块可以浏览目录，每个章节免费试做前 N 题（系统参数 app.trial_questions）。
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    public static final String TRIAL_QUESTIONS = "app.trial_questions";
    private static final int DEFAULT_TRIAL = 5;

    private final UserModuleAccessMapper accessMapper;
    private final ExamModuleMapper moduleMapper;
    private final QuestionBankMapper bankMapper;
    private final ChapterMapper chapterMapper;
    private final SysConfigService configService;

    /** 需要激活时抛给前端的数据 */
    public record NeedActivation(Long moduleId, String moduleName) {
    }

    /** 未激活模块每个章节可以试做的题数 */
    public int trialQuestions() {
        return Math.max(0, configService.getInt(TRIAL_QUESTIONS, DEFAULT_TRIAL));
    }

    /** 用户当前有效的模块权限：模块 id → 到期时间 */
    public Map<Long, LocalDateTime> activeModules(Long userId) {
        return accessMapper.selectList(Wrappers.<UserModuleAccess>lambdaQuery()
                        .eq(UserModuleAccess::getUserId, userId)
                        .gt(UserModuleAccess::getExpireAt, LocalDateTime.now()))
                .stream().collect(Collectors.toMap(UserModuleAccess::getModuleId, UserModuleAccess::getExpireAt));
    }

    public boolean hasAccess(Long userId, Long moduleId) {
        return userId != null && moduleId != null && accessMapper.exists(Wrappers.<UserModuleAccess>lambdaQuery()
                .eq(UserModuleAccess::getUserId, userId)
                .eq(UserModuleAccess::getModuleId, moduleId)
                .gt(UserModuleAccess::getExpireAt, LocalDateTime.now()));
    }

    /** 没有权限就抛「需要激活」（带模块信息，前端弹激活框） */
    public void requireAccess(Long userId, Long moduleId) {
        if (!hasAccess(userId, moduleId)) {
            throw needActivation(moduleId);
        }
    }

    public BizException needActivation(Long moduleId) {
        ExamModule m = moduleId == null ? null : moduleMapper.selectById(moduleId);
        String name = m == null ? "该模块" : m.getName();
        return new BizException(ErrorCode.NEED_ACTIVATION.getCode(), "「" + name + "」需要激活后才能使用",
                new NeedActivation(moduleId, m == null ? null : m.getName()));
    }

    public Long moduleOfBank(Long bankId) {
        QuestionBank bank = bankId == null ? null : bankMapper.selectById(bankId);
        return bank == null ? null : bank.getModuleId();
    }

    public Long moduleOfChapter(Long chapterId) {
        Chapter chapter = chapterId == null ? null : chapterMapper.selectById(chapterId);
        return chapter == null ? null : moduleOfBank(chapter.getBankId());
    }
}
