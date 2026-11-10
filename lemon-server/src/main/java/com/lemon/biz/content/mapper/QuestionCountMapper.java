package com.lemon.biz.content.mapper;

import java.util.Collection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 维护冗余题量：
 * chapter.question_count = 该章节上架且未删除的题目数；
 * question_bank.question_count = 该题库上架章节的题量之和。
 */
@Mapper
public interface QuestionCountMapper {

    @Update("""
            <script>
            UPDATE chapter c SET question_count =
              (SELECT COUNT(*) FROM question q WHERE q.chapter_id = c.id AND q.status = 1 AND q.deleted = 0)
            WHERE c.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int refreshChapters(@Param("ids") Collection<Long> chapterIds);

    @Update("""
            <script>
            UPDATE question_bank b SET question_count =
              (SELECT COALESCE(SUM(c.question_count), 0) FROM chapter c
               WHERE c.bank_id = b.id AND c.status = 1 AND c.deleted = 0)
            WHERE b.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int refreshBanks(@Param("ids") Collection<Long> bankIds);
}
