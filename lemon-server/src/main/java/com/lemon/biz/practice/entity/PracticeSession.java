package com.lemon.biz.practice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.common.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 练习 / 考试会话
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`practice_session`")
public class PracticeSession extends BaseEntity {

    /** 用户 */
    private Long userId;

    /** 章节，错题/收藏练习时为 null */
    private Long chapterId;

    /** 1 练习 2 背题 3 考试 4 错题重练 5 收藏练习 */
    private Integer mode;

    /** 按小节练习时的小节名（如 论述题、材料分析题），null 为整章 */
    private String section;

    /** 0 进行中 1 已完成 2 已放弃（重新开始时） */
    private Integer status;

    /** 本次题目 id 列表 JSON */
    private String questionIds;

    /** 本次作答 JSON：{"题目id":{"a":"用户答案","c":1/0/null}} */
    private String answers;

    /** 当前题目下标 */
    private Integer currentIndex;

    /** 答对数 */
    private Integer correctCount;

    /** 已答数 */
    private Integer answeredCount;

    /** 得分（考试模式） */
    private BigDecimal score;

    /** 考试模式：时长（分钟），开考时从章节复制 */
    private Integer timeLimit;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
