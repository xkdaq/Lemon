package com.lemon.biz.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.practice.entity.AnswerRecord;
import java.time.LocalDate;
import java.util.List;
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
}
