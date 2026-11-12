package com.lemon.biz.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.practice.entity.AnswerRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    /** 做过的不同题目数 */
    @Select("SELECT COUNT(DISTINCT question_id) FROM answer_record WHERE user_id = #{userId}")
    long countDistinctQuestions(@Param("userId") Long userId);

    /** 有答题记录的日期，倒序（用于计算连续打卡天数） */
    @Select("SELECT DISTINCT DATE(created_at) AS d FROM answer_record WHERE user_id = #{userId} ORDER BY d DESC LIMIT #{limit}")
    List<LocalDate> selectAnswerDates(@Param("userId") Long userId, @Param("limit") int limit);

    /** 一道题的全站作答统计 */
    @Data
    class QuestionCount {
        private Long questionId;
        private long total;
        private long rightCount;
    }

    /** 这些题目的全站作答次数、答对次数 */
    @Select("""
            <script>
            SELECT question_id, COUNT(*) AS total, COALESCE(SUM(is_correct = 1), 0) AS right_count
            FROM answer_record WHERE question_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY question_id
            </script>
            """)
    List<QuestionCount> countByQuestions(@Param("ids") Collection<Long> ids);
}
