package com.lemon.biz.paper.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组卷规则：在题库（可限定章节）里按题型数量、难度比例随机抽题
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`paper_rule`")
public class PaperRule extends BaseEntity {

    /** 抽题题库 */
    private Long bankId;

    /** 名称 */
    private String name;

    /** 抽题范围：章节 id 列表 JSON，null 为整个题库 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String chapterIds;

    /** 各题型抽题数 JSON：[{"type":1,"count":10}] */
    private String items;

    /** 难度比例 JSON：[易,中,难]，null 不限 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String difficultyRatio;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
