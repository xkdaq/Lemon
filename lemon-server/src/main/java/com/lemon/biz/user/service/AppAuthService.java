package com.lemon.biz.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import com.lemon.security.StpAppUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户端登录注册。
 * 一期：手机号 + 密码（短信签名审核通过后补充 sms-login）；二期：小程序 wx-login，按手机号合并账号。
 */
@Service
@RequiredArgsConstructor
public class AppAuthService {

    /** Web 注册来源 */
    private static final int FROM_WEB = 1;

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public String register(String phone, String password, String nickname) {
        BizException.throwIf(findByPhone(phone) != null, "该手机号已注册，请直接登录");
        AppUser user = new AppUser();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null && !nickname.isBlank() ? nickname : defaultNickname(phone));
        user.setRegisterFrom(FROM_WEB);
        user.setStatus(Status.ON);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);
        StpAppUtil.login(user.getId());
        return StpAppUtil.token();
    }

    public String login(String phone, String password) {
        AppUser user = findByPhone(phone);
        if (user == null || user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("手机号或密码错误");
        }
        BizException.throwIf(user.getStatus() != Status.ON, "账号已被禁用");

        AppUser update = new AppUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        StpAppUtil.login(user.getId());
        return StpAppUtil.token();
    }

    public AppUser current() {
        return BizException.notNull(userMapper.selectById(StpAppUtil.userId()), "用户不存在");
    }

    private AppUser findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<AppUser>lambdaQuery().eq(AppUser::getPhone, phone));
    }

    private static String defaultNickname(String phone) {
        return "考生" + phone.substring(phone.length() - 4);
    }
}
