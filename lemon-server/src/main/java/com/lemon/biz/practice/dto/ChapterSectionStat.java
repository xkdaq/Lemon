package com.lemon.biz.practice.dto;

import lombok.Data;

/** 章节内某一小节的题量 / 用户进度（统计查询结果） */
@Data
public class ChapterSectionStat {
    private Long chapterId;
    private String sectionName;
    private Integer cnt;
    private Integer correct;
    /** 小节在章节内的位置（小节第一题的排序），用于按试卷顺序展示 */
    private Integer ord;
    private Long firstId;
}
