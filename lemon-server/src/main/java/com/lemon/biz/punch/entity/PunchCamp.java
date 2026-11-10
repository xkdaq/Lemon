package com.lemon.biz.punch.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemon.common.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡活动：在日期范围内每天按组卷规则抽一套题，做完即完成当天打卡
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`punch_camp`")
public class PunchCamp extends BaseEntity {

    /** 名称 */
    private String name;

    /** 封面 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cover;

    /** 介绍 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String intro;

    /** 默认组卷规则 */
    private Long paperRuleId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期（含） */
    private LocalDate endDate;

    /** 休息日：周一到周日为 1-7，逗号分隔；null 表示不休息 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String restWeekdays;

    /** 每人可补卡次数，0 不允许补卡 */
    private Integer makeupLimit;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
