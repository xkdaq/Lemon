package com.lemon.biz.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.system.dto.AdminVO;
import com.lemon.biz.system.entity.SysAdmin;
import com.lemon.biz.system.mapper.SysAdminMapper;
import com.lemon.biz.system.mapper.SysAdminRoleMapper;
import com.lemon.common.BizException;
import com.lemon.common.Status;
import com.lemon.security.AdminRole;
import com.lemon.security.StpAdminUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService implements ApplicationRunner {

    private final SysAdminMapper adminMapper;
    private final SysAdminRoleMapper adminRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    private static final String DEFAULT_INIT_PASSWORD = "admin123";

    @Value("${lemon.admin.init-username}")
    private String initUsername;

    @Value("${lemon.admin.init-password}")
    private String initPassword;

    /** 登录成功返回 token */
    public String login(String username, String password) {
        SysAdmin admin = adminMapper.selectOne(Wrappers.<SysAdmin>lambdaQuery().eq(SysAdmin::getUsername, username));
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        BizException.throwIf(admin.getStatus() != Status.ON, "账号已被禁用");

        SysAdmin update = new SysAdmin();
        update.setId(admin.getId());
        update.setLastLoginAt(LocalDateTime.now());
        adminMapper.updateById(update);

        StpAdminUtil.login(admin.getId());
        return StpAdminUtil.token();
    }

    /** 当前管理员信息（含角色，前端据此控制菜单） */
    public AdminVO current() {
        SysAdmin admin = BizException.notNull(adminMapper.selectById(StpAdminUtil.adminId()), "管理员不存在");
        return AdminVO.of(admin, adminRoleMapper.selectRoleCodes(admin.getId()));
    }

    public void changePassword(String oldPassword, String newPassword) {
        SysAdmin admin = BizException.notNull(adminMapper.selectById(StpAdminUtil.adminId()), "管理员不存在");
        BizException.throwIf(!passwordEncoder.matches(oldPassword, admin.getPassword()), "原密码错误");
        SysAdmin update = new SysAdmin();
        update.setId(admin.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        adminMapper.updateById(update);
    }

    /**
     * 启动时保证至少有一个启用的超级管理员：
     * 没有管理员则创建初始账号；有管理员但没人是超级管理员（如旧数据），把最早的管理员设为超级管理员。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRoleMapper.countActiveAdminsWithRole(AdminRole.SUPER_ADMIN) > 0) {
            return;
        }
        SysAdmin first = adminMapper.selectOne(Wrappers.<SysAdmin>lambdaQuery()
                .eq(SysAdmin::getStatus, Status.ON).orderByAsc(SysAdmin::getId).last("LIMIT 1"));
        if (first == null) {
            // 生产环境不允许用默认密码创建初始管理员
            BizException.throwIf(environment.matchesProfiles("prod") && DEFAULT_INIT_PASSWORD.equals(initPassword),
                    "生产环境请通过环境变量 ADMIN_INIT_PASSWORD 设置初始管理员密码");
            first = new SysAdmin();
            first.setUsername(initUsername);
            first.setPassword(passwordEncoder.encode(initPassword));
            first.setNickname("超级管理员");
            first.setStatus(Status.ON);
            adminMapper.insert(first);
            log.warn("已创建初始管理员账号 [{}]，请尽快修改密码", initUsername);
        }
        adminRoleMapper.deleteByAdmin(first.getId());
        adminRoleMapper.insertByCodes(first.getId(), List.of(AdminRole.SUPER_ADMIN));
        log.warn("已将管理员 [{}] 设为超级管理员", first.getUsername());
    }
}
