package com.lemon.biz.practice.dto;

import lombok.Data;

/** 章节内某一题型的题量 / 用户进度（统计查询结果） */
@Data
public class ChapterTypeStat {
    private Long chapterId;
    private Integer type;
    private Integer cnt;
    private Integer correct;
}
