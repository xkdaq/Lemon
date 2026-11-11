package com.lemon.biz.practice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户端做题相关的请求与响应
 */
public final class PracticeDtos {

    private PracticeDtos() {
    }

    // ==================== 请求 ====================

    /**
     * 开始做题
     *
     * @param mode    1 练习 2 背题 3 考试
     * @param restart      true：放弃未完成的会话，重新开始
     * @param section      只练该小节（如 单选题、论述题、材料分析题）；为空练整章。考试不支持
     */
    public record StartReq(@NotNull Long chapterId, @NotNull @Min(1) @Max(3) Integer mode, Boolean restart,
                           @Size(max = 32) String section) {
    }

    /**
     * 提交一题
     *
     * @param answer 客观题为字母（多选如 "ABD"）；主观题为自评 "1" 会 / "0" 不会；考试模式传空表示清除作答
     */
    public record AnswerReq(@NotNull Long questionId, @Size(max = 26) String answer, @Min(0) Integer costMs) {
    }

    public record ProgressReq(@NotNull @Min(0) Integer currentIndex) {
    }

    /**
     * 收藏集 / 错题集的范围，都不传表示全部
     *
     * @param courseId 科目（科目型题库）：按章节所属科目筛选
     */
    public record Scope(Long moduleId, Long bankId, Long courseId, Long chapterId) {
    }

    // ==================== 响应 ====================

    /**
     * 一道题本次的作答状态
     *
     * @param a 用户答案
     * @param c 1 对 0 错 null 未判（考试未交卷）
     */
    public record Ans(String a, Integer c) {
    }

    /**
     * 会话
     *
     * @param remainingSeconds 考试剩余秒数，非考试为 null
     * @param answers          已作答：题目 id → 作答
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionVO(
            Long id, Long chapterId, String chapterName, Long bankId, String bankName,
            int mode, String section, int status, List<Long> questionIds, int currentIndex,
            int answeredCount, int correctCount, Integer timeLimit, Long remainingSeconds,
            BigDecimal score, Integer totalScore,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startedAt,
            Map<String, Ans> answers,
            /** 模块未激活的试做会话（只有章节前几题），bank 的模块 id 用于引导激活 */
            boolean trial, Long moduleId) {
    }

    /**
     * 用户端题目。answer / analysis 只在允许查看时返回（背题、练习已作答、考试已交卷）。
     *
     * @param deleted 题目已被删除，只返回 id
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionItem(
            Long id, Integer type, String stem, List<OptionItem> options, BigDecimal score,
            String answer, String analysis, boolean favorited, Boolean deleted) {
    }

    /** 练习模式提交一题的结果 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerResult(Boolean correct, String answer, String analysis, int answeredCount, int correctCount) {
    }

    /**
     * 本次做题结果
     *
     * @param subjectiveCount 主观题数量（考试不计分，需要对照参考答案）
     */
    public record ResultVO(
            Long sessionId, Long chapterId, String chapterName, int mode, int total, int answered, int correct,
            int subjectiveCount, BigDecimal score, Integer totalScore, long costSeconds, List<Long> wrongQuestionIds) {
    }

    /**
     * 收藏集 / 错题集里的一道题（列表展示用，不含答案）
     *
     * @param available  题目仍上架可练习（下架或删除为 false）
     * @param wrongCount 仅错题集：累计答错次数
     * @param rightStreak 仅错题集：当前连续答对次数
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CollectedQuestion(
            Long questionId, Integer type, String stemText, String moduleName, String bankName, String chapterName,
            boolean available, Integer wrongCount, Integer rightStreak, String lastWrongAnswer,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime time) {
    }

    /**
     * 分组：模块 → 题库 →（科目）→ 章节，count 为题数
     *
     * @param level module / bank / course / chapter；题库下只有一个科目时省略科目这一层
     */
    public record Group(String level, Long id, String name, long count, List<Group> children) {
    }

    /** 首页“继续上次练习” */
    public record LastSession(Long sessionId, Long chapterId, String chapterName, Long bankId, String bankName,
                              int mode, int currentIndex, int total, int answeredCount) {
    }
}
