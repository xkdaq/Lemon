package com.lemon.biz.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.system.entity.SysAdmin;
import java.time.LocalDateTime;
import java.util.List;

public record AdminVO(
        Long id,
        String username,
        String nickname,
        Integer status,
        List<String> roles,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastLoginAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {

    public static AdminVO of(SysAdmin a, List<String> roles) {
        return new AdminVO(a.getId(), a.getUsername(), a.getNickname(), a.getStatus(), roles,
                a.getLastLoginAt(), a.getCreatedAt());
    }
}
