package com.lemon.biz.practice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.practice.entity.AnswerRecord;
import com.lemon.biz.practice.entity.PracticeSession;
import com.lemon.biz.practice.entity.UserChapterProgress;
import com.lemon.biz.practice.mapper.AnswerRecordMapper;
import com.lemon.biz.practice.mapper.PracticeSessionMapper;
import com.lemon.biz.practice.mapper.UserChapterProgressMapper;
import com.lemon.biz.wrong.entity.UserWrongQuestion;
import com.lemon.biz.wrong.mapper.UserWrongQuestionMapper;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户按题库查看 / 清除自己的做题记录（设置页用）。
 * 清除：答题明细、章节进度、该题库章节的做题会话；可选连同该题库的错题。收藏不动。
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private final UserChapterProgressMapper progressMapper;
    private final AnswerRecordMapper recordMapper;
    private final PracticeSessionMapper sessionMapper;
    private final UserWrongQuestionMapper wrongMapper;
    private final QuestionBankMapper bankMapper;
    private final ExamModuleMapper moduleMapper;

    /**
     * 有记录的题库
     *
     * @param doneCount  做过的题数（各章节进度之和）
     * @param wrongCount 错题集里的题数
     */
    public record BankRecord(Long bankId, String bankName, String moduleName, long doneCount, long wrongCount) {
    }

    public List<BankRecord> banks(Long userId) {
        Map<Long, Long> done = new LinkedHashMap<>();
        progressMapper.selectMaps(Wrappers.<UserChapterProgress>query()
                        .select("(SELECT bank_id FROM chapter WHERE chapter.id = chapter_id) AS bank_id", "SUM(done_count) AS cnt")
                        .eq("user_id", userId).gt("done_count", 0)
                        .groupBy("bank_id"))
                .forEach(r -> done.merge(toLong(r.get("bank_id")), toLong(r.get("cnt")), Long::sum));
        Map<Long, Long> wrong = new LinkedHashMap<>();
        wrongMapper.selectMaps(Wrappers.<UserWrongQuestion>query()
                        .select("bank_id", "COUNT(*) AS cnt")
                        .eq("user_id", userId).eq("status", Status.ON)
                        .inSql("question_id", "SELECT id FROM question WHERE deleted = 0")
                        .groupBy("bank_id"))
                .forEach(r -> wrong.put(toLong(r.get("bank_id")), toLong(r.get("cnt"))));
        Set<Long> ids = new HashSet<>(done.keySet());
        ids.addAll(wrong.keySet());
        ids.remove(null);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, QuestionBank> banks = bankMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(QuestionBank::getId, Function.identity()));
        Map<Long, String> modules = moduleMapper.selectList(null).stream()
                .collect(Collectors.toMap(ExamModule::getId, ExamModule::getName));
        return banks.values().stream()
                .sorted(java.util.Comparator.comparing((QuestionBank b) -> b.getModuleId())
                        .thenComparing(b -> Objects.requireNonNullElse(b.getSort(), 0)))
                .map(b -> new BankRecord(b.getId(), b.getName(), modules.get(b.getModuleId()),
                        done.getOrDefault(b.getId(), 0L), wrong.getOrDefault(b.getId(), 0L)))
                .toList();
    }

    /** 清除某个题库的做题记录；includeWrong 时连同该题库的错题 */
    @Transactional
    public void clear(Long userId, Long bankId, boolean includeWrong) {
        BizException.notNull(bankMapper.selectById(bankId), "题库不存在");
        String chapters = "SELECT id FROM chapter WHERE bank_id = " + bankId;
        recordMapper.delete(Wrappers.<AnswerRecord>lambdaQuery()
                .eq(AnswerRecord::getUserId, userId).inSql(AnswerRecord::getChapterId, chapters));
        progressMapper.delete(Wrappers.<UserChapterProgress>lambdaQuery()
                .eq(UserChapterProgress::getUserId, userId).inSql(UserChapterProgress::getChapterId, chapters));
        sessionMapper.delete(Wrappers.<PracticeSession>lambdaQuery()
                .eq(PracticeSession::getUserId, userId).inSql(PracticeSession::getChapterId, chapters));
        if (includeWrong) {
            wrongMapper.delete(Wrappers.<UserWrongQuestion>lambdaQuery()
                    .eq(UserWrongQuestion::getUserId, userId).eq(UserWrongQuestion::getBankId, bankId));
        }
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
