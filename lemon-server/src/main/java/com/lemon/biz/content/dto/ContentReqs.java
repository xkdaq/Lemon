package com.lemon.biz.content.dto;

import com.lemon.common.PageQuery;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容管理（管理端）请求体
 */
public final class ContentReqs {

    private ContentReqs() {
    }

    public record ModuleSave(
            @NotBlank @Size(max = 32) String name,
            @Size(max = 255) String icon,
            Integer sort,
            @Min(0) @Max(1) Integer status,
            /** 1 免费 0 收费（激活码激活），不传为收费 */
            @Min(0) @Max(1) Integer free,
            /** 单选 / 判断每题分值，不传为 2 */
            @DecimalMin("0") @DecimalMax("100") java.math.BigDecimal singleScore,
            /** 多选每题分值，不传为 2 */
            @DecimalMin("0") @DecimalMax("100") java.math.BigDecimal multiScore) {
    }

    public record BankSave(
            @NotNull Long moduleId,
            /** 所属分类，可为空（未分类）；必须属于同一模块 */
            Long categoryId,
            @NotBlank @Size(max = 64) String name,
            /** 1 科目型 2 试卷型；创建后不可修改 */
            @NotNull @Min(1) @Max(2) Integer type,
            @Size(max = 255) String cover,
            @Size(max = 500) String intro,
            Integer sort,
            @Min(0) @Max(1) Integer status) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BankQuery extends PageQuery {
        private Long moduleId;
        /** 分类；传 0 表示“未分类” */
        private Long categoryId;
        private Integer type;
        private String keyword;
    }

    public record CourseSave(
            @NotNull Long bankId,
            @NotBlank @Size(max = 64) String name,
            Integer sort,
            @Min(0) @Max(1) Integer status) {
    }

    public record ChapterSave(
            @NotNull Long bankId,
            /** 科目型必填，试卷型必须为空 */
            Long courseId,
            @NotBlank @Size(max = 128) String name,
            Integer sort,
            @Min(0) @Max(1) Integer status,
            /** 试卷型：考试时长（分钟） */
            @Min(1) Integer timeLimit,
            /** 试卷型：总分 */
            @Min(1) Integer totalScore) {
    }

    public record CategorySave(
            @NotNull Long moduleId,
            @NotBlank @Size(max = 32) String name,
            Integer sort,
            @Min(0) @Max(1) Integer status) {
    }

    public record StatusUpdate(@NotNull @Min(0) @Max(1) Integer status) {
    }
}
