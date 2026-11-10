package com.lemon.biz.punch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lemon.biz.punch.entity.PunchMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PunchMemberMapper extends BaseMapper<PunchMember> {
}
