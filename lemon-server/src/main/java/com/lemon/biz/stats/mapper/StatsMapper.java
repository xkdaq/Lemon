package com.lemon.biz.stats.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据统计查询：按天汇总、留存、内容热度。时间范围都是 [from, to)。
 */
@Mapper
public interface StatsMapper {

    /** 按天的计数；u / right / judged 只有答题统计用到 */
    @Data
    class DayRow {
        private LocalDate d;
        private long c;
        private long u;
        private long rightCount;
        private long judged;
    }

    @Data
    class RetentionRow {
        private LocalDate d;
        private long total;
        private long d1;
        private long d3;
        private long d7;
    }

    @Data
    class RankRow {
        private Long id;
        private long answers;
        private long users;
        private long wrong;
    }

    @Insert("INSERT IGNORE INTO user_active_day (user_id, active_date) VALUES (#{userId}, #{day})")
    void markActive(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Select("SELECT COUNT(*) FROM user_active_day WHERE active_date = #{day}")
    long activeOn(@Param("day") LocalDate day);

    @Select("""
            SELECT DATE(created_at) AS d, COUNT(*) AS c FROM app_user
            WHERE deleted = 0 AND created_at >= #{from} AND created_at < #{to} GROUP BY DATE(created_at)
            """)
    List<DayRow> newUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT active_date AS d, COUNT(*) AS c FROM user_active_day
            WHERE active_date >= #{from} AND active_date < #{to} GROUP BY active_date
            """)
    List<DayRow> activeUsers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("""
            SELECT DATE(created_at) AS d, COUNT(*) AS c, COUNT(DISTINCT user_id) AS u,
                   COALESCE(SUM(is_correct = 1), 0) AS right_count, COALESCE(SUM(is_correct IS NOT NULL), 0) AS judged
            FROM answer_record WHERE created_at >= #{from} AND created_at < #{to} GROUP BY DATE(created_at)
            """)
    List<DayRow> answers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 激活码激活次数（module_access_log.source = 1） */
    @Select("""
            SELECT DATE(created_at) AS d, COUNT(*) AS c FROM module_access_log
            WHERE source = 1 AND created_at >= #{from} AND created_at < #{to} GROUP BY DATE(created_at)
            """)
    List<DayRow> activations(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT DATE(created_at) AS d, COUNT(*) AS c FROM question_note
            WHERE created_at >= #{from} AND created_at < #{to} GROUP BY DATE(created_at)
            """)
    List<DayRow> notes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 按注册日分组：注册后第 1 / 3 / 7 天仍活跃的人数 */
    @Select("""
            SELECT DATE(u.created_at) AS d, COUNT(*) AS total,
              COALESCE(SUM(EXISTS (SELECT 1 FROM user_active_day a WHERE a.user_id = u.id
                     AND a.active_date = DATE(u.created_at) + INTERVAL 1 DAY)), 0) AS d1,
              COALESCE(SUM(EXISTS (SELECT 1 FROM user_active_day a WHERE a.user_id = u.id
                     AND a.active_date = DATE(u.created_at) + INTERVAL 3 DAY)), 0) AS d3,
              COALESCE(SUM(EXISTS (SELECT 1 FROM user_active_day a WHERE a.user_id = u.id
                     AND a.active_date = DATE(u.created_at) + INTERVAL 7 DAY)), 0) AS d7
            FROM app_user u
            WHERE u.deleted = 0 AND u.created_at >= #{from} AND u.created_at < #{to}
            GROUP BY DATE(u.created_at)
            """)
    List<RetentionRow> retention(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT c.bank_id AS id, COUNT(*) AS answers, COUNT(DISTINCT a.user_id) AS users,
                   COALESCE(SUM(a.is_correct = 0), 0) AS wrong
            FROM answer_record a JOIN chapter c ON c.id = a.chapter_id
            WHERE a.created_at >= #{from} AND a.created_at < #{to} GROUP BY c.bank_id ORDER BY answers DESC LIMIT #{limit}
            """)
    List<RankRow> hotBanks(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("limit") int limit);

    @Select("""
            SELECT chapter_id AS id, COUNT(*) AS answers, COUNT(DISTINCT user_id) AS users,
                   COALESCE(SUM(is_correct = 0), 0) AS wrong
            FROM answer_record WHERE created_at >= #{from} AND created_at < #{to} GROUP BY chapter_id ORDER BY answers DESC LIMIT #{limit}
            """)
    List<RankRow> hotChapters(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("limit") int limit);

    /** 错误率最高的题（至少 minAttempts 次判题作答） */
    @Select("""
            SELECT question_id AS id, COUNT(*) AS answers, COUNT(DISTINCT user_id) AS users, SUM(is_correct = 0) AS wrong
            FROM answer_record WHERE created_at >= #{from} AND created_at < #{to} AND is_correct IS NOT NULL
            GROUP BY question_id HAVING COUNT(*) >= #{minAttempts}
            ORDER BY SUM(is_correct = 0) / COUNT(*) DESC, COUNT(*) DESC LIMIT #{limit}
            """)
    List<RankRow> hardQuestions(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("minAttempts") int minAttempts, @Param("limit") int limit);
}
