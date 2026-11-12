package com.lemon.biz.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.practice.entity.AnswerRecord;
import com.lemon.biz.practice.mapper.AnswerRecordMapper;
import com.lemon.biz.risk.service.RiskAlertAdminService;
import com.lemon.biz.stats.service.StatsService;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.Status;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作台统计。数据量上来后可改为定时汇总到统计表，这里先实时 COUNT。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppUserMapper userMapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankMapper bankMapper;
    private final ExamModuleMapper moduleMapper;
    private final AnswerRecordMapper answerMapper;
    private final RiskAlertAdminService riskService;
    private final StatsService statsService;

    public record ModuleStat(Long moduleId, String name, long bankCount, long questionCount) {
    }

    /**
     * @param userTotal     注册用户数
     * @param userToday     今日新增用户
     * @param activeToday   今日活跃（今天用过用户端的登录用户）
     * @param answerToday   今日答题数
     * @param questionTotal 上架题目数
     * @param draftTotal    下架题目数（待完善）
     * @param riskPending   待处理的风险告警数
     */
    public record Dashboard(long userTotal, long userToday, long activeToday, long answerToday,
                            long questionTotal, long draftTotal, long riskPending, List<ModuleStat> modules) {
    }

    public Dashboard get() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        List<ModuleStat> modules = moduleMapper.selectList(Wrappers.<ExamModule>lambdaQuery()
                        .orderByAsc(ExamModule::getSort).orderByAsc(ExamModule::getId))
                .stream().map(m -> new ModuleStat(m.getId(), m.getName(),
                        bankMapper.selectCount(Wrappers.<QuestionBank>lambdaQuery().eq(QuestionBank::getModuleId, m.getId())),
                        questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                                .eq(Question::getModuleId, m.getId()).eq(Question::getStatus, Status.ON))))
                .toList();
        return new Dashboard(
                userMapper.selectCount(null),
                userMapper.selectCount(Wrappers.<AppUser>lambdaQuery().ge(AppUser::getCreatedAt, today)),
                statsService.activeToday(),
                answerMapper.selectCount(Wrappers.<AnswerRecord>lambdaQuery().ge(AnswerRecord::getCreatedAt, today)),
                questionMapper.selectCount(Wrappers.<Question>lambdaQuery().eq(Question::getStatus, Status.ON)),
                questionMapper.selectCount(Wrappers.<Question>lambdaQuery().eq(Question::getStatus, Status.OFF)),
                riskService.pendingCount(),
                modules);
    }
}
