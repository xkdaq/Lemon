package com.lemon.biz.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.practice.entity.UserChapterProgress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserChapterProgressMapper extends BaseMapper<UserChapterProgress> {

    /**
     * 按答题记录重算某用户某章节的进度：
     * 已做 = 做过的不同题目数；答对 = 最近一次作答为正确的题目数。
     */
    @Insert("""
            INSERT INTO user_chapter_progress (user_id, chapter_id, done_count, correct_count)
            SELECT * FROM (
              SELECT #{userId} AS u, #{chapterId} AS c, COUNT(*) AS done, COALESCE(SUM(ar.is_correct), 0) AS correct
              FROM answer_record ar
              JOIN (SELECT question_id, MAX(id) AS mid FROM answer_record
                    WHERE user_id = #{userId} AND chapter_id = #{chapterId} GROUP BY question_id) latest
                ON ar.id = latest.mid
            ) AS s
            ON DUPLICATE KEY UPDATE done_count = s.done, correct_count = s.correct
            """)
    int refresh(@Param("userId") Long userId, @Param("chapterId") Long chapterId);
}
