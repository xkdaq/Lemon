package com.lemon.security;

import cn.dev33.satoken.stp.StpInterface;
import com.lemon.biz.system.mapper.SysAdminRoleMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 角色来源：只有管理端（loginType=admin）有角色，用户端返回空。
 * 每次校验实时查库，角色调整后立即生效；管理端访问量小，无需缓存。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysAdminRoleMapper adminRoleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!StpAdminUtil.TYPE.equals(loginType)) {
            return List.of();
        }
        return adminRoleMapper.selectRoleCodes(Long.valueOf(loginId.toString()));
    }
}
