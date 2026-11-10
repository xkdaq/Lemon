package com.lemon.biz.punch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.punch.dto.PunchStat;
import com.lemon.biz.punch.entity.PunchRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PunchRecordMapper extends BaseMapper<PunchRecord> {

    /** 排行榜：按已打卡天数倒序，天数相同时最近一次先完成的靠前 */
    @Select("""
            SELECT user_id, COUNT(*) AS cnt, MAX(day) AS last_day, MAX(finished_at) AS last_finished_at
            FROM punch_record WHERE camp_id = #{campId} AND status = 1
            GROUP BY user_id ORDER BY cnt DESC, last_finished_at ASC LIMIT #{limit}
            """)
    List<PunchStat> rank(@Param("campId") Long campId, @Param("limit") int limit);

    /** 这些用户在活动里的已打卡天数和最近打卡日期 */
    @Select("""
            <script>
            SELECT user_id, COUNT(*) AS cnt, MAX(day) AS last_day, MAX(finished_at) AS last_finished_at
            FROM punch_record WHERE camp_id = #{campId} AND status = 1 AND user_id IN
            <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY user_id
            </script>
            """)
    List<PunchStat> doneByUsers(@Param("campId") Long campId, @Param("userIds") Collection<Long> userIds);

    /** 打卡天数比 days 多的人数（算我的名次用） */
    @Select("""
            SELECT COUNT(*) FROM (SELECT user_id FROM punch_record WHERE camp_id = #{campId} AND status = 1
            GROUP BY user_id HAVING COUNT(*) > #{days}) t
            """)
    long countAhead(@Param("campId") Long campId, @Param("days") long days);

    /** 日期范围内每天的打卡人数 */
    @Select("""
            SELECT day, COUNT(*) AS cnt FROM punch_record
            WHERE camp_id = #{campId} AND status = 1 AND day BETWEEN #{from} AND #{to}
            GROUP BY day
            """)
    List<PunchStat> doneByDay(@Param("campId") Long campId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 这些活动某天的打卡人数 */
    @Select("""
            <script>
            SELECT camp_id, COUNT(*) AS cnt FROM punch_record WHERE status = 1 AND day = #{day} AND camp_id IN
            <foreach collection="campIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY camp_id
            </script>
            """)
    List<PunchStat> doneOnDay(@Param("campIds") Collection<Long> campIds, @Param("day") LocalDate day);

    /** 这些活动的学员人数 */
    @Select("""
            <script>
            SELECT camp_id, COUNT(*) AS cnt FROM punch_member WHERE camp_id IN
            <foreach collection="campIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY camp_id
            </script>
            """)
    List<PunchStat> memberCounts(@Param("campIds") Collection<Long> campIds);
}
