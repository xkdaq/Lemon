package com.lemon.biz.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.practice.entity.PracticeSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PracticeSessionMapper extends BaseMapper<PracticeSession> {

    /** 行锁读取，防止同一会话的并发作答互相覆盖 answers */
    @Select("SELECT * FROM practice_session WHERE id = #{id} FOR UPDATE")
    PracticeSession selectForUpdate(@Param("id") Long id);
}
