package com.lemon.biz.practice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.biz.content.entity.Chapter;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.entity.QuestionBank;
import com.lemon.biz.content.enums.BankType;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.biz.content.mapper.ChapterMapper;
import com.lemon.biz.content.mapper.QuestionBankMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.biz.favorite.service.FavoriteService;
import com.lemon.biz.practice.dto.PracticeDtos.Ans;
import com.lemon.biz.practice.dto.PracticeDtos.AnswerReq;
import com.lemon.biz.practice.dto.PracticeDtos.AnswerResult;
import com.lemon.biz.practice.dto.PracticeDtos.LastSession;
import com.lemon.biz.practice.dto.PracticeDtos.QuestionItem;
import com.lemon.biz.practice.dto.PracticeDtos.ResultVO;
import com.lemon.biz.practice.dto.PracticeDtos.SessionVO;
import com.lemon.biz.practice.dto.PracticeDtos.StartReq;
import com.lemon.biz.practice.entity.PracticeSession;
import com.lemon.biz.practice.enums.PracticeMode;
import com.lemon.biz.practice.mapper.PracticeSessionMapper;
import com.lemon.common.BizException;
import com.lemon.common.ErrorCode;
import com.lemon.common.JsonUtil;
import com.lemon.common.Status;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import tools.jackson.core.type.TypeReference;

/**
 * 用户端做题会话。
 * <ul>
 *   <li>开始：同一章节同一模式有未完成会话时直接续做（除非 restart）；题目顺序在开始时固定</li>
 *   <li>练习：每题提交即判题、公布答案并记录；同一会话内每题只记一次</li>
 *   <li>背题：直接给答案，不记录</li>
 *   <li>考试：作答只存草稿，交卷时统一判分并记录；超时后任何访问都会触发自动交卷</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    static final int IN_PROGRESS = 0;
    static final int FINISHED = 1;
    static final int ABANDONED = 2;
    /** 考试超时宽限：给网络延迟留余量 */
    private static final int EXAM_GRACE_SECONDS = 60;
    private static final int MAX_FETCH = 50;

    private static final TypeReference<LinkedHashMap<String, Ans>> ANSWERS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> IDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<OptionItem>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private final PracticeSessionMapper sessionMapper;
    private final QuestionMapper questionMapper;
    private final ChapterMapper chapterMapper;
    private final QuestionBankMapper bankMapper;
    private final AnswerRecorder recorder;
    private final FavoriteService favoriteService;

    // ==================== 开始 / 查询 ====================

    @Transactional
    public SessionVO start(Long userId, StartReq req) {
        PracticeMode mode = PracticeMode.of(req.mode());
        Chapter chapter = chapterMapper.selectById(req.chapterId());
        BizException.throwIf(chapter == null || chapter.getStatus() != Status.ON, "章节不存在或已下架");
        QuestionBank bank = bankMapper.selectById(chapter.getBankId());
        BizException.throwIf(bank == null || bank.getStatus() != Status.ON, "题库不存在或已下架");
        BizException.throwIf(mode == PracticeMode.EXAM && BankType.of(bank.getType()) != BankType.PAPER,
                "只有模拟卷可以考试");
        Integer qType = req.questionType();
        BizException.throwIf(mode == PracticeMode.EXAM && qType != null, "考试需要做整套试卷");

        PracticeSession existing = sessionMapper.selectOne(Wrappers.<PracticeSession>lambdaQuery()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getChapterId, chapter.getId())
                .eq(PracticeSession::getMode, mode.getCode())
                .eq(qType != null, PracticeSession::getQuestionType, qType)
                .isNull(qType == null, PracticeSession::getQuestionType)
                .eq(PracticeSession::getStatus, IN_PROGRESS)
                .orderByDesc(PracticeSession::getId).last("LIMIT 1"));
        if (existing != null) {
            if (Boolean.TRUE.equals(req.restart())) {
                existing.setStatus(ABANDONED);
                sessionMapper.updateById(existing);
            } else if (mode == PracticeMode.EXAM && examExpired(existing)) {
                finish(existing);
            } else {
                return toVO(existing, chapter, bank);
            }
        }

        List<Long> ids = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .select(Question::getId)
                        .eq(Question::getChapterId, chapter.getId())
                        .eq(qType != null, Question::getType, qType)
                        .eq(Question::getStatus, Status.ON)
                        .orderByAsc(Question::getSort).orderByAsc(Question::getId))
                .stream().map(Question::getId).toList();
        BizException.throwIf(ids.isEmpty(), qType != null ? "该章节暂时没有这类题目" : "该章节暂时没有题目");

        PracticeSession s = new PracticeSession();
        s.setUserId(userId);
        s.setChapterId(chapter.getId());
        s.setMode(mode.getCode());
        s.setQuestionType(qType);
        s.setStatus(IN_PROGRESS);
        s.setQuestionIds(JsonUtil.toJson(ids));
        s.setAnswers("{}");
        s.setCurrentIndex(0);
        s.setCorrectCount(0);
        s.setAnsweredCount(0);
        s.setTimeLimit(mode == PracticeMode.EXAM ? chapter.getTimeLimit() : null);
        s.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return toVO(s, chapter, bank);
    }

    /**
     * 错题重练 / 收藏练习：题目由调用方给定（已过滤掉下架和删除的题），不绑定章节。
     * 每次都新建会话，同模式未完成的旧会话标记放弃（范围可能已不同，续做意义不大）。
     */
    @Transactional
    public SessionVO startScoped(Long userId, PracticeMode mode, List<Long> questionIds) {
        BizException.throwIf(mode != PracticeMode.WRONG && mode != PracticeMode.FAVORITE, "不支持的模式");
        BizException.throwIf(questionIds.isEmpty(), "没有可以练习的题目");
        sessionMapper.update(Wrappers.<PracticeSession>lambdaUpdate()
                .set(PracticeSession::getStatus, ABANDONED)
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getMode, mode.getCode())
                .eq(PracticeSession::getStatus, IN_PROGRESS));
        PracticeSession s = new PracticeSession();
        s.setUserId(userId);
        s.setMode(mode.getCode());
        s.setStatus(IN_PROGRESS);
        s.setQuestionIds(JsonUtil.toJson(questionIds));
        s.setAnswers("{}");
        s.setCurrentIndex(0);
        s.setCorrectCount(0);
        s.setAnsweredCount(0);
        s.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return toVO(s, null, null);
    }

    @Transactional
    public SessionVO get(Long userId, Long sessionId) {
        PracticeSession s = owned(userId, sessionId, false);
        if (isExam(s) && s.getStatus() == IN_PROGRESS && examExpired(s)) {
            finish(s);
        }
        return toVO(s);
    }

    /** 拉题目详情；是否附带答案由模式和作答状态决定 */
    public List<QuestionItem> questions(Long userId, Long sessionId, List<Long> ids) {
        BizException.throwIf(ids.size() > MAX_FETCH, "一次最多获取 " + MAX_FETCH + " 题");
        PracticeSession s = owned(userId, sessionId, false);
        Set<Long> inSession = new HashSet<>(questionIds(s));
        BizException.throwIf(!inSession.containsAll(ids), "题目不属于本次练习");
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Ans> answers = answers(s);
        PracticeMode mode = PracticeMode.of(s.getMode());
        boolean finished = s.getStatus() != IN_PROGRESS;
        Map<Long, Question> questions = questionMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Set<Long> favorited = favoriteService.favoritedIn(userId, ids);

        List<QuestionItem> items = new ArrayList<>();
        for (Long id : ids) {
            Question q = questions.get(id);
            if (q == null) {
                items.add(new QuestionItem(id, null, null, null, null, null, null, false, true));
                continue;
            }
            // 练习模式：作答后公布；主观题没有可“泄露”的标准答案，先看参考答案再自评，所以直接给
            boolean reveal = finished || mode == PracticeMode.RECITE
                    || (mode.judgesImmediately()
                        && (answers.containsKey(id.toString()) || q.getType() == QuestionType.SUBJECTIVE.getCode()));
            List<OptionItem> options = JsonUtil.parse(q.getOptions(), OPTIONS_TYPE);
            items.add(new QuestionItem(q.getId(), q.getType(), q.getStem(), options == null ? List.of() : options,
                    q.getScore(), reveal ? q.getAnswer() : null, reveal ? q.getAnalysis() : null,
                    favorited.contains(id), null));
        }
        return items;
    }

    // ==================== 作答 ====================

    @Transactional
    public AnswerResult answer(Long userId, Long sessionId, AnswerReq req) {
        PracticeSession s = owned(userId, sessionId, true);
        PracticeMode mode = PracticeMode.of(s.getMode());
        BizException.throwIf(mode == PracticeMode.RECITE, "背题模式无需作答");
        if (isExam(s) && examExpired(s)) {
            finish(s);
            throw new BizException("考试时间已到，已自动交卷");
        }
        BizException.throwIf(s.getStatus() != IN_PROGRESS, "本次练习已结束");
        BizException.throwIf(!questionIds(s).contains(req.questionId()), "题目不属于本次练习");
        Map<String, Ans> answers = answers(s);
        String key = req.questionId().toString();

        if (mode == PracticeMode.EXAM) {
            // 考试：只存草稿，不判题、不公布答案；空答案表示清除
            String normalized = AnswerJudge.normalize(req.answer());
            if (normalized.isEmpty()) {
                answers.remove(key);
            } else {
                answers.put(key, new Ans(normalized, null));
            }
            s.setAnswers(JsonUtil.toJson(answers));
            s.setAnsweredCount(answers.size());
            sessionMapper.updateById(s);
            return new AnswerResult(null, null, null, s.getAnsweredCount(), s.getCorrectCount());
        }

        Question q = BizException.notNull(questionMapper.selectById(req.questionId()), "题目已被删除");
        Ans existing = answers.get(key);
        if (existing != null) {
            // 同一会话每题只判一次，重复提交返回原结果（防止连点）
            return new AnswerResult(existing.c() == 1, q.getAnswer(), q.getAnalysis(), s.getAnsweredCount(), s.getCorrectCount());
        }
        boolean correct = AnswerJudge.judge(q.getType(), q.getAnswer(), req.answer());
        String stored = q.getType() == QuestionType.SUBJECTIVE.getCode() ? req.answer() : AnswerJudge.normalize(req.answer());
        answers.put(key, new Ans(stored, correct ? 1 : 0));
        s.setAnswers(JsonUtil.toJson(answers));
        s.setAnsweredCount(answers.size());
        s.setCorrectCount(s.getCorrectCount() + (correct ? 1 : 0));
        sessionMapper.updateById(s);

        recorder.record(userId, q, stored, correct, s.getId(), req.costMs());
        recorder.refreshProgress(userId, Set.of(q.getChapterId()));
        return new AnswerResult(correct, q.getAnswer(), q.getAnalysis(), s.getAnsweredCount(), s.getCorrectCount());
    }

    public void saveProgress(Long userId, Long sessionId, int index) {
        PracticeSession s = owned(userId, sessionId, false);
        if (s.getStatus() != IN_PROGRESS) {
            return;
        }
        PracticeSession update = new PracticeSession();
        update.setId(s.getId());
        update.setCurrentIndex(Math.min(index, questionIds(s).size() - 1));
        sessionMapper.updateById(update);
    }

    // ==================== 交卷 / 结果 ====================

    @Transactional
    public ResultVO submit(Long userId, Long sessionId) {
        PracticeSession s = owned(userId, sessionId, true);
        if (s.getStatus() == IN_PROGRESS) {
            finish(s);
        }
        BizException.throwIf(s.getStatus() == ABANDONED, "本次练习已放弃");
        return result(s);
    }

    public ResultVO result(Long userId, Long sessionId) {
        PracticeSession s = owned(userId, sessionId, false);
        BizException.throwIf(s.getStatus() != FINISHED, "还没有交卷");
        return result(s);
    }

    /** 结束会话；考试在这里统一判分并记录 */
    private void finish(PracticeSession s) {
        if (isExam(s)) {
            Map<String, Ans> answers = answers(s);
            List<Long> ids = questionIds(s);
            Map<Long, Question> questions = ids.isEmpty() ? Map.of() : questionMapper.selectByIds(ids).stream()
                    .collect(Collectors.toMap(Question::getId, Function.identity()));
            BigDecimal score = BigDecimal.ZERO;
            int correctCount = 0;
            Set<Long> chapters = new HashSet<>();
            for (Long id : ids) {
                Question q = questions.get(id);
                Ans a = answers.get(id.toString());
                // 主观题不自动判分；未作答的题不计入错题集
                if (q == null || a == null || q.getType() == QuestionType.SUBJECTIVE.getCode()) {
                    continue;
                }
                boolean correct = AnswerJudge.normalize(q.getAnswer()).equals(a.a());
                answers.put(id.toString(), new Ans(a.a(), correct ? 1 : 0));
                if (correct) {
                    correctCount++;
                    score = score.add(Objects.requireNonNullElse(q.getScore(), BigDecimal.ZERO));
                }
                chapters.add(recorder.record(s.getUserId(), q, a.a(), correct, s.getId(), null));
            }
            recorder.refreshProgress(s.getUserId(), chapters);
            s.setAnswers(JsonUtil.toJson(answers));
            s.setCorrectCount(correctCount);
            s.setScore(score);
        }
        s.setStatus(FINISHED);
        s.setFinishedAt(LocalDateTime.now());
        sessionMapper.updateById(s);
    }

    private ResultVO result(PracticeSession s) {
        Map<String, Ans> answers = answers(s);
        List<Long> ids = questionIds(s);
        Chapter chapter = s.getChapterId() == null ? null : chapterMapper.selectById(s.getChapterId());
        Map<Long, Question> questions = ids.isEmpty() ? Map.of() : questionMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        int subjective = (int) questions.values().stream()
                .filter(q -> q.getType() == QuestionType.SUBJECTIVE.getCode()).count();
        List<Long> wrong = ids.stream().filter(id -> {
            Ans a = answers.get(id.toString());
            return a != null && Integer.valueOf(0).equals(a.c());
        }).toList();
        Integer totalScore = null;
        if (isExam(s)) {
            totalScore = chapter != null && chapter.getTotalScore() != null ? chapter.getTotalScore()
                    : questions.values().stream().map(q -> Objects.requireNonNullElse(q.getScore(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).intValue();
        }
        long cost = s.getStartedAt() == null || s.getFinishedAt() == null ? 0
                : Duration.between(s.getStartedAt(), s.getFinishedAt()).toSeconds();
        return new ResultVO(s.getId(), s.getChapterId(), chapter == null ? null : chapter.getName(), s.getMode(),
                ids.size(), answers.size(), s.getCorrectCount(), subjective, s.getScore(), totalScore, cost, wrong);
    }

    // ==================== 首页 / 列表用 ====================

    /** 最近一次未完成的练习或考试 */
    public LastSession last(Long userId) {
        PracticeSession s = sessionMapper.selectOne(Wrappers.<PracticeSession>lambdaQuery()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getStatus, IN_PROGRESS)
                .in(PracticeSession::getMode, PracticeMode.PRACTICE.getCode(), PracticeMode.RECITE.getCode(),
                        PracticeMode.EXAM.getCode())
                .orderByDesc(PracticeSession::getUpdatedAt).last("LIMIT 1"));
        if (s == null || (isExam(s) && examExpired(s))) {
            return null;
        }
        Chapter chapter = chapterMapper.selectById(s.getChapterId());
        if (chapter == null || chapter.getStatus() != Status.ON) {
            return null;
        }
        QuestionBank bank = bankMapper.selectById(chapter.getBankId());
        return new LastSession(s.getId(), chapter.getId(), chapter.getName(), chapter.getBankId(),
                bank == null ? null : bank.getName(), s.getMode(), s.getCurrentIndex(), questionIds(s).size(),
                s.getAnsweredCount());
    }

    /** 这些章节里未完成的整章会话：章节 id → 模式集合 */
    public Map<Long, Set<Integer>> inProgress(Long userId, List<Long> chapterIds) {
        if (chapterIds.isEmpty()) {
            return Map.of();
        }
        return sessionMapper.selectList(Wrappers.<PracticeSession>lambdaQuery()
                        .select(PracticeSession::getChapterId, PracticeSession::getMode)
                        .eq(PracticeSession::getUserId, userId)
                        .eq(PracticeSession::getStatus, IN_PROGRESS)
                        .isNull(PracticeSession::getQuestionType)
                        .in(PracticeSession::getChapterId, chapterIds))
                .stream().collect(Collectors.groupingBy(PracticeSession::getChapterId,
                        Collectors.mapping(PracticeSession::getMode, Collectors.toSet())));
    }

    /** 这些章节里未完成的按题型练习：章节 id → 题型集合 */
    public Map<Long, Set<Integer>> typesInProgress(Long userId, List<Long> chapterIds) {
        if (chapterIds.isEmpty()) {
            return Map.of();
        }
        return sessionMapper.selectList(Wrappers.<PracticeSession>lambdaQuery()
                        .select(PracticeSession::getChapterId, PracticeSession::getQuestionType)
                        .eq(PracticeSession::getUserId, userId)
                        .eq(PracticeSession::getStatus, IN_PROGRESS)
                        .eq(PracticeSession::getMode, PracticeMode.PRACTICE.getCode())
                        .isNotNull(PracticeSession::getQuestionType)
                        .in(PracticeSession::getChapterId, chapterIds))
                .stream().collect(Collectors.groupingBy(PracticeSession::getChapterId,
                        Collectors.mapping(PracticeSession::getQuestionType, Collectors.toSet())));
    }

    /** 这些试卷最近一次考试成绩：章节 id → 分数 */
    public Map<Long, BigDecimal> lastExamScores(Long userId, List<Long> chapterIds) {
        if (chapterIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        sessionMapper.selectList(Wrappers.<PracticeSession>lambdaQuery()
                        .select(PracticeSession::getChapterId, PracticeSession::getScore)
                        .eq(PracticeSession::getUserId, userId)
                        .eq(PracticeSession::getMode, PracticeMode.EXAM.getCode())
                        .eq(PracticeSession::getStatus, FINISHED)
                        .in(PracticeSession::getChapterId, chapterIds)
                        .orderByDesc(PracticeSession::getFinishedAt))
                .forEach(s -> result.putIfAbsent(s.getChapterId(), s.getScore()));
        return result;
    }

    // ==================== 工具 ====================

    private PracticeSession owned(Long userId, Long sessionId, boolean lock) {
        PracticeSession s = lock ? sessionMapper.selectForUpdate(sessionId) : sessionMapper.selectById(sessionId);
        if (s == null || !Objects.equals(s.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "练习记录不存在");
        }
        return s;
    }

    private static boolean isExam(PracticeSession s) {
        return s.getMode() == PracticeMode.EXAM.getCode();
    }

    private static boolean examExpired(PracticeSession s) {
        return remainingSeconds(s) != null && remainingSeconds(s) <= -EXAM_GRACE_SECONDS;
    }

    /** 考试剩余秒数（可为负，表示已超时）；不限时返回 null */
    private static Long remainingSeconds(PracticeSession s) {
        if (!isExam(s) || s.getTimeLimit() == null || s.getStartedAt() == null) {
            return null;
        }
        return s.getTimeLimit() * 60L - Duration.between(s.getStartedAt(), LocalDateTime.now()).toSeconds();
    }

    private static List<Long> questionIds(PracticeSession s) {
        List<Long> ids = JsonUtil.parse(s.getQuestionIds(), IDS_TYPE);
        return ids == null ? List.of() : ids;
    }

    private static Map<String, Ans> answers(PracticeSession s) {
        Map<String, Ans> a = JsonUtil.parse(s.getAnswers(), ANSWERS_TYPE);
        return a == null ? new LinkedHashMap<>() : a;
    }

    private SessionVO toVO(PracticeSession s) {
        Chapter chapter = s.getChapterId() == null ? null : chapterMapper.selectById(s.getChapterId());
        QuestionBank bank = chapter == null ? null : bankMapper.selectById(chapter.getBankId());
        return toVO(s, chapter, bank);
    }

    private SessionVO toVO(PracticeSession s, Chapter chapter, QuestionBank bank) {
        boolean exam = isExam(s);
        boolean inProgress = s.getStatus() == IN_PROGRESS;
        Map<String, Ans> answers = answers(s);
        Long remaining = inProgress ? remainingSeconds(s) : null;
        String chapterName = chapter == null ? null
                : s.getQuestionType() == null ? chapter.getName()
                : chapter.getName() + " · " + QuestionType.of(s.getQuestionType()).sectionLabel();
        return new SessionVO(s.getId(), s.getChapterId(), chapterName,
                bank == null ? null : bank.getId(), bank == null ? null : bank.getName(),
                s.getMode(), s.getQuestionType(), s.getStatus(), questionIds(s), s.getCurrentIndex(),
                s.getAnsweredCount(), s.getCorrectCount(), s.getTimeLimit(),
                remaining == null ? null : Math.max(0, remaining),
                exam && !inProgress ? s.getScore() : null,
                exam && chapter != null ? chapter.getTotalScore() : null,
                s.getStartedAt(), answers);
    }
}
