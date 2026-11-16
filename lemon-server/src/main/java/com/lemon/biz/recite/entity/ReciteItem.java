package com.lemon.biz.recite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lemon.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 背诵条目：一道题的背诵要点。JSON 字段原样存，读取时再解析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`recite_item`")
public class ReciteItem extends BaseEntity {

    private Long bookId;

    private Integer setNo;

    private Integer no;

    /** 1 单选 2 多选 */
    private Integer type;

    private String subject;

    private String topic;

    private String qtype;

    private String stem;

    private String options;

    private String answer;

    private String core;

    private String cards;

    private String traps;

    private String dists;

    private String tip;

    private String mnemonic;

    /** 对照上的题库题目（只读引用，可为空） */
    private Long questionId;

    private Long chapterId;
}
