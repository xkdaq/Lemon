package com.lemon.biz.content.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.biz.content.entity.Question;
import com.lemon.common.JsonUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import tools.jackson.core.type.TypeReference;

/**
 * 管理端题目视图：options 解析为数组，并带上章节 / 科目 / 题库名方便列表展示。
 */
@Data
public class QuestionVO {

    private static final TypeReference<List<OptionItem>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private Long id;
    private Long moduleId;
    private Long bankId;
    private String bankName;
    private Long courseId;
    private String courseName;
    private Long chapterId;
    private String chapterName;
    private Integer type;
    private String stem;
    private List<OptionItem> options;
    private String answer;
    private String analysis;
    private BigDecimal score;
    private Integer difficulty;
    private String source;
    private Integer sort;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public static QuestionVO of(Question q) {
        QuestionVO vo = new QuestionVO();
        vo.id = q.getId();
        vo.moduleId = q.getModuleId();
        vo.bankId = q.getBankId();
        vo.chapterId = q.getChapterId();
        vo.type = q.getType();
        vo.stem = q.getStem();
        List<OptionItem> opts = JsonUtil.parse(q.getOptions(), OPTIONS_TYPE);
        vo.options = opts == null ? List.of() : opts;
        vo.answer = q.getAnswer();
        vo.analysis = q.getAnalysis();
        vo.score = q.getScore();
        vo.difficulty = q.getDifficulty();
        vo.source = q.getSource();
        vo.sort = q.getSort();
        vo.status = q.getStatus();
        vo.createdAt = q.getCreatedAt();
        vo.updatedAt = q.getUpdatedAt();
        return vo;
    }
}
