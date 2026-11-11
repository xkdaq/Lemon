package com.lemon.biz.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.content.mapper.QuestionMapper;
import com.lemon.common.Status;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 分值规则：按模块设置单选（含判断）、多选每题分值，主观题不计分（0 分）。
 * 模拟考试只考选择题，试卷满分 = 该试卷上架的选择题分值之和。
 * <p>
 * 题目的 score 字段始终按规则写入（新增 / 编辑 / 导入 / 移动 / 改模块分值时同步），读分值的地方直接用它。
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

    /** 参与模拟考试、计分的题型：单选、多选、判断 */
    public static final List<Integer> OBJECTIVE_TYPES = List.of(
            QuestionType.SINGLE.getCode(), QuestionType.MULTIPLE.getCode(), QuestionType.JUDGE.getCode());

    private final ExamModuleMapper moduleMapper;
    private final QuestionMapper questionMapper;

    public static boolean isObjective(Integer type) {
        return type != null && OBJECTIVE_TYPES.contains(type);
    }

    /** 某模块某题型的分值 */
    public BigDecimal scoreFor(Long moduleId, Integer type) {
        ExamModule m = moduleId == null ? null : moduleMapper.selectById(moduleId);
        return scoreFor(m, type);
    }

    public static BigDecimal scoreFor(ExamModule m, Integer type) {
        if (!isObjective(type)) {
            return BigDecimal.ZERO;
        }
        BigDecimal single = m == null || m.getSingleScore() == null ? BigDecimal.valueOf(2) : m.getSingleScore();
        BigDecimal multi = m == null || m.getMultiScore() == null ? BigDecimal.valueOf(2) : m.getMultiScore();
        return type == QuestionType.MULTIPLE.getCode() ? multi : single;
    }

    /** 按模块规则重算该模块下所有题目的分值（修改模块分值后调用） */
    public void applyModule(Long moduleId) {
        ExamModule m = moduleMapper.selectById(moduleId);
        if (m == null) {
            return;
        }
        for (Integer type : List.of(QuestionType.SINGLE.getCode(), QuestionType.JUDGE.getCode(),
                QuestionType.MULTIPLE.getCode(), QuestionType.SUBJECTIVE.getCode())) {
            questionMapper.update(Wrappers.<Question>lambdaUpdate()
                    .set(Question::getScore, scoreFor(m, type))
                    .eq(Question::getModuleId, moduleId).eq(Question::getType, type));
        }
    }

    /** 试卷满分：章节 id → 上架的选择题分值之和 */
    public Map<Long, BigDecimal> objectiveTotals(Collection<Long> chapterIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (chapterIds == null || chapterIds.isEmpty()) {
            return result;
        }
        questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .select(Question::getChapterId, Question::getScore)
                        .in(Question::getChapterId, chapterIds)
                        .in(Question::getType, OBJECTIVE_TYPES)
                        .eq(Question::getStatus, Status.ON))
                .forEach(q -> result.merge(q.getChapterId(),
                        q.getScore() == null ? BigDecimal.ZERO : q.getScore(), BigDecimal::add));
        return result;
    }

    /** 分值显示：50.0 → 50，50.5 → 50.5 */
    public static Number display(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() <= 0 ? s.intValue() : s;
    }
}
