package com.lemon.biz.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import com.lemon.security.StpAppUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端：用户端用户列表与禁用。做题数据统计在 M4 接入。
 */
@Service
@RequiredArgsConstructor
public class AppUserAdminService {

    private final AppUserMapper mapper;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserQuery extends PageQuery {
        /** 匹配手机号或昵称 */
        private String keyword;
        private Integer status;
    }

    public PageResult<AppUser> page(UserQuery q) {
        return PageResult.of(mapper.selectPage(q.toPage(), Wrappers.<AppUser>lambdaQuery()
                .and(StringUtils.hasText(q.getKeyword()), w -> w
                        .like(AppUser::getPhone, q.getKeyword()).or().like(AppUser::getNickname, q.getKeyword()))
                .eq(q.getStatus() != null, AppUser::getStatus, q.getStatus())
                .orderByDesc(AppUser::getId)));
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
