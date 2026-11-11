package com.lemon.biz.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.biz.user.sms.SmsCodeService;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import com.lemon.security.StpAppUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户端登录注册。
 * <p>
 * 账号模型：<b>手机号是唯一身份</b>，一个手机号只对应一个账号；密码、微信 openid 都只是这个账号的登录方式。
 * <ul>
 *   <li>Web：手机号 + 短信验证码登录（没注册过自动注册）；设置过密码的也可以手机号 + 密码登录</li>
 *   <li>小程序（二期）：wx.login 拿 openid + 手机号快速验证拿手机号，按手机号找到已有账号就绑定 openid，
 *       没有就新建，所以同一个人在两端是同一个账号、数据互通。详见 docs/账号与登录.md</li>
 *   <li>早期「手机号 + 密码」注册时没验证手机号（phone_verified = 0），可能被人抢注；
 *       这类账号第一次通过验证码 / 微信验证手机号时，旧密码作废、其他设备下线，账号归验证过的人</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppAuthService {

    /** Web 注册来源 */
    private static final int FROM_WEB = 1;

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsCodeService smsCodeService;

    /** 验证码登录结果；passwordCleared = 这次验证让旧密码失效了（前端提示用户重新设置） */
    public record SmsLoginResult(String token, boolean passwordCleared) {
    }

    public void sendLoginCode(String phone, String ip) {
        smsCodeService.send(phone, ip);
    }

    /** 手机号 + 验证码登录，没注册过自动注册 */
    @Transactional(rollbackFor = Exception.class)
    public SmsLoginResult smsLogin(String phone, String code) {
        smsCodeService.verify(phone, code);
        AppUser user = findByPhone(phone);
        boolean cleared = false;
        if (user == null) {
            user = new AppUser();
            user.setPhone(phone);
            user.setPhoneVerified(1);
            user.setNickname(defaultNickname(phone));
            user.setRegisterFrom(FROM_WEB);
            user.setStatus(Status.ON);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            BizException.throwIf(user.getStatus() != Status.ON, "账号已被禁用");
            cleared = markPhoneVerified(user, null);
            touchLogin(user.getId());
        }
        StpAppUtil.login(user.getId());
        return new SmsLoginResult(StpAppUtil.token(), cleared);
    }

    /** 手机号 + 密码登录（需要先设置过密码） */
    public String login(String phone, String password) {
        AppUser user = findByPhone(phone);
        if (user == null || user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(user != null && user.getPassword() == null
                    ? "该账号还没有设置密码，请用验证码登录" : "手机号或密码错误");
        }
        BizException.throwIf(user.getStatus() != Status.ON, "账号已被禁用");
        touchLogin(user.getId());
        StpAppUtil.login(user.getId());
        return StpAppUtil.token();
    }

    /** 给当前账号发验证码（设置 / 修改密码用），发到账号绑定的手机号 */
    public void sendSelfCode(String ip) {
        smsCodeService.send(current().getPhone(), ip);
    }

    /** 设置 / 修改密码：验证码验证本人手机号，同时也算完成了手机号验证 */
    @Transactional(rollbackFor = Exception.class)
    public void setPassword(String code, String password) {
        AppUser user = current();
        smsCodeService.verify(user.getPhone(), code);
        markPhoneVerified(user, StpAppUtil.token());
        AppUser update = new AppUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(update);
    }

    public AppUser current() {
        AppUser user = BizException.notNull(userMapper.selectById(StpAppUtil.userId()), "用户不存在");
        user.setHasPassword(user.getPassword() != null);
        return user;
    }

    /**
     * 标记手机号已验证。之前没验证过且设置过密码的（可能是别人抢注的），旧密码作废、其他设备下线。
     *
     * @param keepToken 保留在线的 token（当前正在操作的这台设备），没有传 null
     * @return 是否清掉了旧密码
     */
    private boolean markPhoneVerified(AppUser user, String keepToken) {
        if (user.getPhoneVerified() != null && user.getPhoneVerified() == 1) {
            return false;
        }
        boolean hadPassword = user.getPassword() != null;
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .set(AppUser::getPhoneVerified, 1)
                .set(hadPassword, AppUser::getPassword, null)
                .eq(AppUser::getId, user.getId()));
        if (hadPassword) {
            for (String token : StpAppUtil.LOGIC.getTokenValueListByLoginId(user.getId())) {
                if (!token.equals(keepToken)) {
                    StpAppUtil.LOGIC.logoutByTokenValue(token);
                }
            }
        }
        user.setPhoneVerified(1);
        user.setPassword(null);
        return hadPassword;
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
