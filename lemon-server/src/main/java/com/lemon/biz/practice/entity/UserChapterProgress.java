package com.lemon.biz.practice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户章节进度汇总
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user_chapter_progress`")
public class UserChapterProgress extends BaseEntity {

    /** 用户 */
    private Long userId;

    /** 章节 */
    private Long chapterId;

    /** 已做题数（去重） */
    private Integer doneCount;

    /** 答对题数 */
    private Integer correctCount;
}
