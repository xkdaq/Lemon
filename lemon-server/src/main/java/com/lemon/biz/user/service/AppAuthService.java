package com.lemon.biz.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.access.service.ActivationService;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.biz.user.sms.SmsCodeService;
import com.lemon.biz.user.sms.SmsLog;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import com.lemon.security.StpAppUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户端登录注册。主推「账号 + 密码」登录，短信只在注册和找回密码时发（省短信费）。
 * <p>
 * 账号模型：<b>手机号是唯一身份</b>，一个手机号只对应一个账号；用户名（自动分配、不可改）、密码、微信 openid
 * 都只是这个账号的登录方式。
 * <ul>
 *   <li>Web：注册 = 手机号 + 短信验证码 + 设置密码；登录 = 手机号或用户名 + 密码；
 *       忘记密码用短信验证码重置，也可以找后台重置</li>
 *   <li>小程序（二期）：授权手机号直接登录（没有账号自动注册、分配用户名），按手机号并入 Web 端同一个账号。
 *       详见 docs/账号与登录.md</li>
 *   <li>早期「手机号 + 密码」注册时没验证手机号（phone_verified = 0），可能被人抢注；
 *       真正的机主用「忘记密码」验证手机号后，密码被重置、其他设备全部下线，账号归验证过的人</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppAuthService {

    /** Web 注册来源 */
    private static final int FROM_WEB = 1;
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsCodeService smsCodeService;
    private final ActivationService activationService;

    /** 发验证码。register：注册（手机号不能已注册）；reset：找回密码（手机号必须已注册） */
    public void sendCode(String phone, String purpose, String ip) {
        AppUser user = findByPhone(phone);
        if ("register".equals(purpose)) {
            BizException.throwIf(user != null, "该手机号已注册，请直接登录");
            smsCodeService.send(phone, SmsLog.SCENE_REGISTER, ip);
        } else {
            BizException.throwIf(user == null, "该手机号还没有注册");
            smsCodeService.send(phone, SmsLog.SCENE_RESET, ip);
        }
    }

    /** 注册：手机号 + 验证码 + 密码，注册后自动登录；赠送「政治」「教育学」（系统参数 app.register_gift_days，默认 365 天） */
    @Transactional(rollbackFor = Exception.class)
    public String register(String phone, String code, String password, String nickname) {
        BizException.throwIf(findByPhone(phone) != null, "该手机号已注册，请直接登录");
        smsCodeService.verify(phone, code);
        AppUser user = new AppUser();
        user.setPhone(phone);
        user.setPhoneVerified(1);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null && !nickname.isBlank() ? nickname.trim() : defaultNickname(phone));
        user.setRegisterFrom(FROM_WEB);
        user.setStatus(Status.ON);
        user.setLastLoginAt(LocalDateTime.now());
        insertWithUsername(user);
        activationService.grantRegister(user.getId());
        StpAppUtil.login(user.getId());
        return StpAppUtil.token();
    }

    /** 找回密码：手机号 + 验证码 + 新密码；其他设备全部下线，当前设备直接登录 */
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(String phone, String code, String password) {
        AppUser user = BizException.notNull(findByPhone(phone), "该手机号还没有注册");
        smsCodeService.verify(phone, code);
        BizException.throwIf(user.getStatus() != Status.ON, "账号已被禁用");
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .set(AppUser::getPassword, passwordEncoder.encode(password))
                .set(AppUser::getPhoneVerified, 1)
                .set(AppUser::getMustChangePassword, 0)
                .set(AppUser::getLastLoginAt, LocalDateTime.now())
                .eq(AppUser::getId, user.getId()));
        StpAppUtil.LOGIC.logout(user.getId());
        StpAppUtil.login(user.getId());
        return StpAppUtil.token();
    }

    /** 账号密码登录：账号可以是手机号或用户名 */
    public String login(String account, String password) {
        String a = account.trim();
        AppUser user = a.matches(PHONE_REGEX) ? findByPhone(a)
                : userMapper.selectOne(Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUsername, a.toLowerCase()));
        if (user == null || user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(user != null && user.getPassword() == null
                    ? "该账号还没有设置密码，请点「忘记密码」设置" : "账号或密码错误");
        }
        BizException.throwIf(user.getStatus() != Status.ON, "账号已被禁用");
        touchLogin(user.getId());
        StpAppUtil.login(user.getId());
        return StpAppUtil.token();
    }

    /**
     * 登录状态下设置 / 修改密码：已有密码要验证原密码；还没有密码的（小程序授权登录的用户）直接设置。
     * 改完其他设备下线，当前设备保持登录。
     */
    public void changePassword(String oldPassword, String newPassword) {
        AppUser user = current();
        if (user.getPassword() != null) {
            BizException.throwIf(oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword()),
                    "原密码不正确");
        }
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .set(AppUser::getPassword, passwordEncoder.encode(newPassword))
                .set(AppUser::getMustChangePassword, 0)
                .eq(AppUser::getId, user.getId()));
        kickOthers(user.getId(), StpAppUtil.token());
    }

    public void updateNickname(String nickname) {
        String n = nickname == null ? "" : nickname.trim();
        BizException.throwIf(n.isEmpty(), "昵称不能为空");
        AppUser update = new AppUser();
        update.setId(StpAppUtil.userId());
        update.setNickname(n);
        userMapper.updateById(update);
    }

    public AppUser current() {
        AppUser user = BizException.notNull(userMapper.selectById(StpAppUtil.userId()), "用户不存在");
        user.setHasPassword(user.getPassword() != null);
        return user;
    }

    /**
     * 插入新用户并分配用户名（lemon + 6 位随机数字，不会和补发的 lemon0000xx 撞），撞号就换一个重试。
     * 小程序首次授权登录建账号时也用这个方法。
     */
    void insertWithUsername(AppUser user) {
        for (int i = 0; ; i++) {
            user.setUsername("lemon" + (100000 + RANDOM.nextInt(900000)));
            try {
                userMapper.insert(user);
                return;
            } catch (DuplicateKeyException e) {
                // 手机号重复（并发注册）直接报错；用户名重复换一个再试
                BizException.throwIf(findByPhone(user.getPhone()) != null, "该手机号已注册，请直接登录");
                BizException.throwIf(i >= 9, "注册人数太多，请稍后再试");
                user.setId(null);
            }
        }
    }

    private void kickOthers(Long userId, String keepToken) {
        for (String token : StpAppUtil.LOGIC.getTokenValueListByLoginId(userId)) {
            if (!token.equals(keepToken)) {
                StpAppUtil.LOGIC.logoutByTokenValue(token);
            }
        }
    }

    private void touchLogin(Long userId) {
        AppUser update = new AppUser();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
    }

    private AppUser findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<AppUser>lambdaQuery().eq(AppUser::getPhone, phone));
    }

    private static String defaultNickname(String phone) {
        return "考生" + phone.substring(phone.length() - 4);
    }
}
