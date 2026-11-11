package com.lemon.biz.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import com.lemon.security.StpAppUtil;
import java.security.SecureRandom;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端：用户端用户列表、禁用、重置密码。做题数据统计在 M4 接入。
 */
@Service
@RequiredArgsConstructor
public class AppUserAdminService {

    /** 临时密码字符：去掉容易看错的 0 O o 1 l I */
    private static final String TEMP_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserQuery extends PageQuery {
        /** 匹配手机号、用户名或昵称 */
        private String keyword;
        private Integer status;
    }

    public PageResult<AppUser> page(UserQuery q) {
        return PageResult.of(mapper.selectPage(q.toPage(), Wrappers.<AppUser>lambdaQuery()
                .and(StringUtils.hasText(q.getKeyword()), w -> w
                        .like(AppUser::getPhone, q.getKeyword()).or().like(AppUser::getNickname, q.getKeyword())
                        .or().like(AppUser::getUsername, q.getKeyword()))
                .eq(q.getStatus() != null, AppUser::getStatus, q.getStatus())
                .orderByDesc(AppUser::getId)));
    }

    /**
     * 重置密码：生成 8 位临时密码（只返回这一次，由管理员转告用户），所有设备下线，
     * 用户用临时密码登录后会被提示修改密码。
     */
    public String resetPassword(Long id) {
        BizException.notNull(mapper.selectById(id), "用户不存在");
        StringBuilder pwd = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            pwd.append(TEMP_CHARS.charAt(RANDOM.nextInt(TEMP_CHARS.length())));
        }
        mapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .set(AppUser::getPassword, passwordEncoder.encode(pwd))
                .set(AppUser::getMustChangePassword, 1)
                .eq(AppUser::getId, id));
        StpAppUtil.LOGIC.logout(id);
        return pwd.toString();
    }

    public void updateStatus(Long id, int status) {
        AppUser user = BizException.notNull(mapper.selectById(id), "用户不存在");
        user.setStatus(status);
        mapper.updateById(user);
        if (status != Status.ON) {
            // 禁用后立即下线
            StpAppUtil.LOGIC.logout(id);
        }
    }
}
