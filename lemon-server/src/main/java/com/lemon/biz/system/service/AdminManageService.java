package com.lemon.biz.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.system.dto.AdminReqs.AdminCreate;
import com.lemon.biz.system.dto.AdminReqs.AdminQuery;
import com.lemon.biz.system.dto.AdminReqs.AdminUpdate;
import com.lemon.biz.system.dto.AdminVO;
import com.lemon.biz.system.entity.SysAdmin;
import com.lemon.biz.system.entity.SysRole;
import com.lemon.biz.system.mapper.SysAdminMapper;
import com.lemon.biz.system.mapper.SysAdminRoleMapper;
import com.lemon.biz.system.mapper.SysAdminRoleMapper.AdminRoleRow;
import com.lemon.biz.system.mapper.SysRoleMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageResult;
import com.lemon.common.Status;
import com.lemon.security.AdminRole;
import com.lemon.security.StpAdminUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理员账号管理（仅超级管理员可调用，由 WebConfig 路由鉴权保证）。
 * 保护规则：不能禁用 / 删除自己，不能去掉自己的超级管理员角色。
 */
@Service
@RequiredArgsConstructor
public class AdminManageService {

    private final SysAdminMapper adminMapper;
    private final SysRoleMapper roleMapper;
    private final SysAdminRoleMapper adminRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public List<SysRole> roles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getStatus, Status.ON).orderByAsc(SysRole::getId));
    }

    public PageResult<AdminVO> page(AdminQuery q) {
        var page = adminMapper.selectPage(q.toPage(), Wrappers.<SysAdmin>lambdaQuery()
                .and(StringUtils.hasText(q.getKeyword()), w -> w
                        .like(SysAdmin::getUsername, q.getKeyword())
                        .or().like(SysAdmin::getNickname, q.getKeyword()))
                .orderByAsc(SysAdmin::getId));
        Map<Long, List<String>> roles = page.getRecords().isEmpty() ? Map.of()
                : adminRoleMapper.selectRoleCodesByAdmins(page.getRecords().stream().map(SysAdmin::getId).toList())
                .stream().collect(Collectors.groupingBy(AdminRoleRow::adminId,
                        Collectors.mapping(AdminRoleRow::code, Collectors.toList())));
        return PageResult.of(page, a -> AdminVO.of(a, roles.getOrDefault(a.getId(), List.of())));
    }

    @Transactional
    public Long create(AdminCreate req) {
        BizException.throwIf(adminMapper.exists(Wrappers.<SysAdmin>lambdaQuery()
                .eq(SysAdmin::getUsername, req.username())), "用户名已存在");
        Set<String> roleCodes = validRoles(req.roles());
        SysAdmin admin = new SysAdmin();
        admin.setUsername(req.username());
        admin.setPassword(passwordEncoder.encode(req.password()));
        admin.setNickname(StringUtils.hasText(req.nickname()) ? req.nickname() : req.username());
        admin.setStatus(Status.ON);
        adminMapper.insert(admin);
        adminRoleMapper.insertByCodes(admin.getId(), roleCodes);
        return admin.getId();
    }

    @Transactional
    public void update(Long id, AdminUpdate req) {
        SysAdmin admin = get(id);
        Set<String> roleCodes = validRoles(req.roles());
        if (isSelf(id)) {
            BizException.throwIf(req.status() != Status.ON, "不能禁用自己");
            BizException.throwIf(adminRoleMapper.selectRoleCodes(id).contains(AdminRole.SUPER_ADMIN)
                    && !roleCodes.contains(AdminRole.SUPER_ADMIN), "不能去掉自己的超级管理员角色");
        }
        admin.setNickname(StringUtils.hasText(req.nickname()) ? req.nickname() : admin.getUsername());
        admin.setStatus(req.status());
        adminMapper.updateById(admin);
        adminRoleMapper.deleteByAdmin(id);
        adminRoleMapper.insertByCodes(id, roleCodes);
        if (req.status() != Status.ON) {
            // 禁用后立即下线
            StpAdminUtil.LOGIC.logout(id);
        }
    }

    public void resetPassword(Long id, String password) {
        SysAdmin admin = get(id);
        admin.setPassword(passwordEncoder.encode(password));
        adminMapper.updateById(admin);
        if (!isSelf(id)) {
            StpAdminUtil.LOGIC.logout(id);
        }
    }

    @Transactional
    public void delete(Long id) {
        SysAdmin admin = get(id);
        BizException.throwIf(isSelf(id), "不能删除自己");
        adminRoleMapper.deleteByAdmin(id);
        // 逻辑删除前改掉用户名，释放唯一索引，之后可以用同名重新创建
        admin.setUsername(admin.getUsername() + "#del" + id);
        adminMapper.updateById(admin);
        adminMapper.deleteById(id);
        StpAdminUtil.LOGIC.logout(id);
    }

    private Set<String> validRoles(List<String> codes) {
        Set<String> requested = new HashSet<>(codes);
        Set<String> existing = roles().stream().map(SysRole::getCode).collect(Collectors.toSet());
        List<String> unknown = new ArrayList<>(requested);
        unknown.removeAll(existing);
        BizException.throwIf(!unknown.isEmpty(), "未知角色：" + unknown);
        return requested;
    }

    private SysAdmin get(Long id) {
        return BizException.notNull(adminMapper.selectById(id), "管理员不存在");
    }

    private static boolean isSelf(Long id) {
        return Objects.equals(id, StpAdminUtil.adminId());
    }
}
