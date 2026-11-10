package com.lemon.biz.favorite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏集
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user_favorite`")
public class UserFavorite extends BaseEntity {

    /** 用户 */
    private Long userId;

    /** 题目 */
    private Long questionId;

    /** 模块（冗余） */
    private Long moduleId;

    /** 题库（冗余） */
    private Long bankId;

    /** 章节（冗余） */
    private Long chapterId;
}
