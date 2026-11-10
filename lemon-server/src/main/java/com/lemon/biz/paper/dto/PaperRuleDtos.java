package com.lemon.biz.paper.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组卷规则的请求与响应
 */
public final class PaperRuleDtos {

    private PaperRuleDtos() {
    }

    /**
     * 一种题型抽多少道
     *
     * @param type 题型 1 单选 2 多选 3 判断 4 主观
     */
    public record RuleItem(@NotNull @Min(1) @Max(4) Integer type, @NotNull @Min(1) @Max(200) Integer count) {
    }

    /**
     * 新建 / 修改组卷规则
     *
     * @param chapterIds      抽题范围，为空表示整个题库
     * @param difficultyRatio 难度比例 [易,中,难]（合计 100），为空不限难度
     */
    public record PaperRuleSave(
            @NotNull Long bankId,
            @NotBlank @Size(max = 64) String name,
            @Size(max = 1000) List<Long> chapterIds,
            @NotEmpty @Size(max = 4) List<@Valid RuleItem> items,
            @Size(min = 3, max = 3) List<@NotNull @Min(0) @Max(100) Integer> difficultyRatio,
            @Min(0) @Max(1) Integer status) {
    }

    /**
     * 组卷规则
     *
     * @param chapterIds    抽题范围，null 为整个题库
     * @param questionCount 每次抽题总数
     */
    public record PaperRuleVO(
            Long id, Long bankId, String bankName, String name, List<Long> chapterIds, List<RuleItem> items,
            List<Integer> difficultyRatio, int questionCount, Integer status,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {
    }

    /**
     * 抽题范围内某题型的可用题量
     *
     * @param byDifficulty 按难度 [易,中,难] 的题量
     */
    public record PoolStat(int type, String label, int total, int[] byDifficulty) {
    }

    /** 抽题预览里的一道题 */
    public record PreviewQuestion(Long id, int type, int difficulty, String chapterName, String stemText) {
    }
}
