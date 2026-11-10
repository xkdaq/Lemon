package com.lemon.biz.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.practice.dto.ChapterTypeStat;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /** 各章节、各题型的上架题量 */
    @Select("""
            <script>
            SELECT chapter_id, type, COUNT(*) AS cnt, 0 AS correct FROM question
            WHERE deleted = 0 AND status = 1 AND chapter_id IN
            <foreach collection="chapterIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY chapter_id, type
            </script>
            """)
    List<ChapterTypeStat> countByType(@Param("chapterIds") Collection<Long> chapterIds);

    /** 用户在各章节、各题型做过的题（上架题、去重）及最近一次作答答对的题数 */
    @Select("""
            <script>
            SELECT q.chapter_id, q.type, COUNT(*) AS cnt, COALESCE(SUM(ar.is_correct), 0) AS correct
            FROM answer_record ar
            JOIN (SELECT question_id, MAX(id) AS mid FROM answer_record
                  WHERE user_id = #{userId} AND chapter_id IN
                  <foreach collection="chapterIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                  GROUP BY question_id) latest ON ar.id = latest.mid
            JOIN question q ON q.id = ar.question_id AND q.deleted = 0 AND q.status = 1
            GROUP BY q.chapter_id, q.type
            </script>
            """)
    List<ChapterTypeStat> doneByType(@Param("userId") Long userId, @Param("chapterIds") Collection<Long> chapterIds);
}
