package com.lemon.biz.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.collection.CollectionService;
import com.lemon.biz.favorite.entity.UserFavorite;
import com.lemon.biz.favorite.mapper.UserFavoriteMapper;
import com.lemon.biz.practice.entity.AnswerRecord;
import com.lemon.biz.practice.mapper.AnswerRecordMapper;
import com.lemon.biz.wrong.entity.UserWrongQuestion;
import com.lemon.biz.wrong.mapper.UserWrongQuestionMapper;
import com.lemon.common.Status;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * “我的”页统计
 */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    /** 连续打卡最多回溯的天数 */
    private static final int MAX_STREAK_LOOKBACK = 400;

    private final AnswerRecordMapper recordMapper;
    private final UserWrongQuestionMapper wrongMapper;
    private final UserFavoriteMapper favoriteMapper;

    /**
     * @param answerTotal   累计作答次数
     * @param questionTotal 做过的不同题目数
     * @param accuracy      正确率（%），按作答次数计
     * @param streakDays    连续打卡天数（今天或昨天有作答才算连续）
     * @param todayCount    今日作答数
     * @param wrongCount    错题集现有题数
     * @param favoriteCount 收藏题数
     */
    public record Stats(long answerTotal, long questionTotal, int accuracy, int streakDays, long todayCount,
                        long wrongCount, long favoriteCount) {
    }

    public Stats get(Long userId) {
        long total = recordMapper.selectCount(Wrappers.<AnswerRecord>lambdaQuery().eq(AnswerRecord::getUserId, userId));
        long correct = total == 0 ? 0 : recordMapper.selectCount(Wrappers.<AnswerRecord>lambdaQuery()
                .eq(AnswerRecord::getUserId, userId).eq(AnswerRecord::getIsCorrect, 1));
        LocalDate today = LocalDate.now();
        return new Stats(
                total,
                total == 0 ? 0 : recordMapper.countDistinctQuestions(userId),
                total == 0 ? 0 : (int) Math.round(correct * 100.0 / total),
                total == 0 ? 0 : streakDays(recordMapper.selectAnswerDates(userId, MAX_STREAK_LOOKBACK), today),
                recordMapper.selectCount(Wrappers.<AnswerRecord>lambdaQuery()
                        .eq(AnswerRecord::getUserId, userId).ge(AnswerRecord::getCreatedAt, today.atStartOfDay())),
                wrongMapper.selectCount(Wrappers.<UserWrongQuestion>lambdaQuery()
                        .eq(UserWrongQuestion::getUserId, userId).eq(UserWrongQuestion::getStatus, Status.ON)
                        .inSql(UserWrongQuestion::getQuestionId, CollectionService.EXISTING_QUESTIONS)),
                favoriteMapper.selectCount(Wrappers.<UserFavorite>lambdaQuery().eq(UserFavorite::getUserId, userId)
                        .inSql(UserFavorite::getQuestionId, CollectionService.EXISTING_QUESTIONS)));
    }

    /**
     * 连续打卡天数：从今天（今天没做题则从昨天）往前数连续有作答的天数。
     *
     * @param datesDesc 有作答的日期，倒序且去重
     */
    static int streakDays(List<LocalDate> datesDesc, LocalDate today) {
        if (datesDesc.isEmpty()) {
            return 0;
        }
        LocalDate expected = datesDesc.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate d : datesDesc) {
            if (d.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }
}
