package com.lemon.biz.practice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.operation.service.SysConfigService;
import com.lemon.biz.practice.entity.AnswerRecord;
import com.lemon.biz.practice.mapper.AnswerRecordMapper;
import com.lemon.biz.practice.mapper.UserChapterProgressMapper;
import com.lemon.biz.wrong.entity.UserWrongQuestion;
import com.lemon.biz.wrong.mapper.UserWrongQuestionMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 一次有效作答（练习模式提交、考试交卷）的全部副作用：
 * 1. 写答题明细；2. 维护错题集；3. 重算章节进度。背题模式不调用。
 */
@Service
@RequiredArgsConstructor
public class AnswerRecorder {

    private static final int ON = 1;
    private static final int OFF = 0;
    private static final int DEFAULT_REMOVE_STREAK = 2;

    private final AnswerRecordMapper recordMapper;
    private final UserWrongQuestionMapper wrongMapper;
    private final UserChapterProgressMapper progressMapper;
    private final SysConfigService configService;

    /** 记录一题；调用方负责事务。返回需要重算进度的章节，便于批量时合并 */
    public Long record(Long userId, Question q, String userAnswer, boolean correct, Long sessionId, Integer costMs) {
        AnswerRecord r = new AnswerRecord();
        r.setUserId(userId);
        r.setQuestionId(q.getId());
        r.setChapterId(q.getChapterId());
        r.setSessionId(sessionId);
        r.setUserAnswer(userAnswer);
        r.setIsCorrect(correct ? 1 : 0);
        r.setCostMs(costMs);
        recordMapper.insert(r);
        updateWrongBook(userId, q, userAnswer, correct);
        return q.getChapterId();
    }

    public void refreshProgress(Long userId, Set<Long> chapterIds) {
        new HashSet<>(chapterIds).forEach(chapterId -> progressMapper.refresh(userId, chapterId));
    }

    /**
     * 答错：加入错题集（已有则错误次数 +1、连对清零、重新放回错题集）；
     * 答对：若该题在错题集中，连对 +1，达到系统参数 wrong.remove.streak 后自动移出。
     */
    void updateWrongBook(Long userId, Question q, String userAnswer, boolean correct) {
        UserWrongQuestion w = wrongMapper.selectOne(Wrappers.<UserWrongQuestion>lambdaQuery()
                .eq(UserWrongQuestion::getUserId, userId).eq(UserWrongQuestion::getQuestionId, q.getId()));
        if (!correct) {
            if (w == null) {
                w = new UserWrongQuestion();
                w.setUserId(userId);
                w.setQuestionId(q.getId());
                w.setWrongCount(1);
            } else {
                w.setWrongCount(w.getWrongCount() + 1);
            }
            w.setModuleId(q.getModuleId());
            w.setBankId(q.getBankId());
            w.setChapterId(q.getChapterId());
            w.setRightStreak(0);
            w.setLastWrongAnswer(userAnswer);
            w.setLastWrongAt(LocalDateTime.now());
            w.setStatus(ON);
            if (w.getId() == null) {
                wrongMapper.insert(w);
            } else {
                wrongMapper.updateById(w);
            }
            return;
        }
        if (w != null && w.getStatus() == ON) {
            int streak = w.getRightStreak() + 1;
            w.setRightStreak(streak);
            if (streak >= configService.getInt(SysConfigService.WRONG_REMOVE_STREAK, DEFAULT_REMOVE_STREAK)) {
                w.setStatus(OFF);
            }
            wrongMapper.updateById(w);
        }
    }
}
