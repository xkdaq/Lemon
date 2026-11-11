package com.lemon.biz.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.user.sms.SmsLog;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SmsLogMapper extends BaseMapper<SmsLog> {

    /** 某天起按天统计：发送数、已提交数、失败数 */
    record DayCount(String day, long total, long ok, long fail) {
    }

    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS total,
                   SUM(status = 1) AS ok, SUM(status = 0) AS fail
            FROM sms_log WHERE created_at >= #{since}
            GROUP BY day ORDER BY day
            """)
    List<DayCount> countByDay(@Param("since") LocalDateTime since);
}
