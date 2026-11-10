package com.lemon.biz.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.content.entity.Question;
import com.lemon.biz.practice.dto.ChapterSectionStat;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 题目所属小节：填了 section 用 section，否则按题型。与 {@link com.lemon.biz.content.enums.QuestionType#sectionLabel()} 一致
     */
    String SECTION_EXPR = "COALESCE(NULLIF(TRIM(section), ''), ELT(type, '单选题', '多选题', '判断题', '分析题'))";
    String Q_SECTION_EXPR = "COALESCE(NULLIF(TRIM(q.section), ''), ELT(q.type, '单选题', '多选题', '判断题', '分析题'))";

    /** 各章节、各小节的上架题量 */
    @Select("""
            <script>
            SELECT chapter_id, """ + SECTION_EXPR + """
             AS section_name, COUNT(*) AS cnt, 0 AS correct, MIN(sort) AS ord, MIN(id) AS first_id FROM question
            WHERE deleted = 0 AND status = 1 AND chapter_id IN
            <foreach collection="chapterIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY chapter_id, section_name
            </script>
            """)
    List<ChapterSectionStat> countBySection(@Param("chapterIds") Collection<Long> chapterIds);

    /** 用户在各章节、各小节做过的题（上架题、去重）及最近一次作答答对的题数 */
    @Select("""
            <script>
            SELECT q.chapter_id, """ + Q_SECTION_EXPR + """
             AS section_name, COUNT(*) AS cnt, COALESCE(SUM(ar.is_correct), 0) AS correct
            FROM answer_record ar
            JOIN (SELECT question_id, MAX(id) AS mid FROM answer_record
                  WHERE user_id = #{userId} AND chapter_id IN
                  <foreach collection="chapterIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                  GROUP BY question_id) latest ON ar.id = latest.mid
            JOIN question q ON q.id = ar.question_id AND q.deleted = 0 AND q.status = 1
            GROUP BY q.chapter_id, section_name
            </script>
            """)
    List<ChapterSectionStat> doneBySection(@Param("userId") Long userId, @Param("chapterIds") Collection<Long> chapterIds);
}
