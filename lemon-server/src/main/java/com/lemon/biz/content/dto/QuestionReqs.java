package com.lemon.biz.content.dto;

import com.lemon.common.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目管理（管理端）请求体
 */
public final class QuestionReqs {

    private QuestionReqs() {
    }

    /** 选项。key 为空时按顺序自动补 A/B/C… */
    public record OptionItem(@Size(max = 2) String key, @Size(max = 2000) String content) {
    }

    public record QuestionSave(
            @NotNull Long chapterId,
            /** 1 单选 2 多选 3 判断 4 主观 */
            @NotNull @Min(1) @Max(4) Integer type,
            @NotBlank @Size(max = 20000) String stem,
            @Size(max = 10) List<@Valid OptionItem> options,
            /** 客观题答案，如 A / ABD / “A,B,D”；判断题还可填 对/错/√/× */
            @Size(max = 500) String answer,
            /** 解析；主观题即参考答案，不填为背诵题（题干即背诵内容） */
            @Size(max = 20000) String analysis,
            @DecimalMin("0") @DecimalMax("999.9") BigDecimal score,
            @Min(1) @Max(3) Integer difficulty,
            @Size(max = 64) String source,
            Integer sort,
            @Min(0) @Max(1) Integer status,
            /** 小节，如 论述题、材料分析题；不填按题型分组 */
            @Size(max = 32) String section) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class QuestionQuery extends PageQuery {
        private Long moduleId;
        private Long bankId;
        private Long courseId;
        private Long chapterId;
        private Integer type;
        private Integer status;
        /** 匹配题干 */
        private String keyword;
    }

    public record BatchIds(@NotEmpty @Size(max = 500) List<Long> ids) {
    }

    public record BatchStatus(@NotEmpty @Size(max = 500) List<Long> ids, @NotNull @Min(0) @Max(1) Integer status) {
    }

    public record BatchMove(@NotEmpty @Size(max = 500) List<Long> ids, @NotNull Long targetChapterId) {
    }
}
