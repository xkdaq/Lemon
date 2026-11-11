package com.lemon.biz.access.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.access.entity.ActivationCode;
import com.lemon.biz.access.entity.ModuleAccessLog;
import com.lemon.biz.access.entity.UserModuleAccess;
import com.lemon.biz.access.mapper.ActivationCodeMapper;
import com.lemon.biz.access.mapper.ModuleAccessLogMapper;
import com.lemon.biz.access.mapper.UserModuleAccessMapper;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.system.entity.SysAdmin;
import com.lemon.biz.system.mapper.SysAdminMapper;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端：激活码列表 / 导出，用户的模块权限与变更记录
 */
@Service
@RequiredArgsConstructor
public class AccessAdminService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EXPORT_LIMIT = 20000;

    private final ActivationCodeMapper codeMapper;
    private final UserModuleAccessMapper accessMapper;
    private final ModuleAccessLogMapper logMapper;
    private final ExamModuleMapper moduleMapper;
    private final AppUserMapper userMapper;
    private final SysAdminMapper adminMapper;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CodeQuery extends PageQuery {
        /** 激活码（可带横线）、使用者手机号或用户名 */
        private String keyword;
        private Long moduleId;
        private Integer status;
        private String batchName;
    }

    /** 激活码列表行：带模块名和使用者 */
    public record CodeVO(Long id, String code, Long moduleId, String moduleName, String batchName,
                         Integer validityType, Integer validDays,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expireAt,
                         Integer status, UserBrief user,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime usedAt,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime accessUntil,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
    }

    public record UserBrief(Long id, String username, String phone, String nickname) {
    }

    /** 用户某个模块的权限 */
    public record ModuleAccessVO(Long moduleId, String moduleName,
                                 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expireAt, boolean active,
                                 boolean free) {
    }

    public record LogVO(Long id, String moduleName, Integer source, String code, String adminName,
                        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beforeExpire,
                        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime afterExpire,
                        String remark,
                        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
    }

    public record UserAccess(UserBrief user, List<ModuleAccessVO> modules, List<LogVO> logs) {
    }

    public PageResult<CodeVO> pageCodes(CodeQuery q) {
        var page = codeMapper.selectPage(q.toPage(), codeWrapper(q));
        Map<Long, String> modules = moduleNames();
        Map<Long, AppUser> users = usersOf(page.getRecords().stream().map(ActivationCode::getUsedBy).collect(Collectors.toSet()));
        return PageResult.of(page, c -> toVO(c, modules, users));
    }

    /** 导出 CSV（带 BOM，Excel 直接打开不乱码），最多 2 万行 */
    public byte[] exportCodes(CodeQuery q) {
        List<ActivationCode> rows = codeMapper.selectList(codeWrapper(q).last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> modules = moduleNames();
        Map<Long, AppUser> users = usersOf(rows.stream().map(ActivationCode::getUsedBy).collect(Collectors.toSet()));
        StringBuilder sb = new StringBuilder("﻿激活码,模块,批次,有效期,状态,使用者用户名,使用者手机号,使用时间,模块到期时间,生成时间\n");
        for (ActivationCode c : rows) {
            AppUser u = c.getUsedBy() == null ? null : users.get(c.getUsedBy());
            sb.append(String.join(",",
                    ActivationService.format(c.getCode()),
                    csv(modules.get(c.getModuleId())),
                    csv(c.getBatchName()),
                    csv(validityText(c)),
                    statusText(c.getStatus()),
                    csv(u == null ? null : u.getUsername()),
                    csv(u == null ? null : u.getPhone()),
                    time(c.getUsedAt()),
                    time(c.getAccessUntil()),
                    time(c.getCreatedAt()))).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 已有的批次名（生成时下拉选择、列表筛选用） */
    public List<String> batchNames() {
        return codeMapper.selectObjs(Wrappers.<ActivationCode>query()
                        .select("DISTINCT batch_name").isNotNull("batch_name").orderByDesc("batch_name"))
                .stream().map(String::valueOf).toList();
    }

    public UserAccess userAccess(Long userId) {
        AppUser u = BizException.notNull(userMapper.selectById(userId), "用户不存在");
        LocalDateTime now = LocalDateTime.now();
        Map<Long, UserModuleAccess> access = accessMapper.selectList(Wrappers.<UserModuleAccess>lambdaQuery()
                        .eq(UserModuleAccess::getUserId, userId))
                .stream().collect(Collectors.toMap(UserModuleAccess::getModuleId, Function.identity()));
        List<ModuleAccessVO> modules = moduleMapper.selectList(Wrappers.<ExamModule>lambdaQuery()
                        .orderByAsc(ExamModule::getSort).orderByAsc(ExamModule::getId))
                .stream().map(m -> {
                    UserModuleAccess a = access.get(m.getId());
                    LocalDateTime exp = a == null ? null : a.getExpireAt();
                    boolean free = m.getFree() != null && m.getFree() == 1;
                    return new ModuleAccessVO(m.getId(), m.getName(), exp, free || (exp != null && exp.isAfter(now)), free);
                }).toList();

        List<ModuleAccessLog> logs = logMapper.selectList(Wrappers.<ModuleAccessLog>lambdaQuery()
                .eq(ModuleAccessLog::getUserId, userId).orderByDesc(ModuleAccessLog::getId).last("LIMIT 100"));
        Map<Long, String> names = moduleNames();
        Set<Long> codeIds = logs.stream().map(ModuleAccessLog::getCodeId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> codes = codeIds.isEmpty() ? Map.of() : codeMapper.selectByIds(codeIds).stream()
                .collect(Collectors.toMap(ActivationCode::getId, c -> ActivationService.format(c.getCode())));
        Set<Long> adminIds = logs.stream().map(ModuleAccessLog::getAdminId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> admins = adminIds.isEmpty() ? Map.of() : adminMapper.selectByIds(adminIds).stream()
                .collect(Collectors.toMap(SysAdmin::getId, a -> a.getNickname() != null ? a.getNickname() : a.getUsername()));
        List<LogVO> logVOs = logs.stream().map(l -> new LogVO(l.getId(), names.get(l.getModuleId()), l.getSource(),
                codes.get(l.getCodeId()), admins.get(l.getAdminId()), l.getBeforeExpire(), l.getAfterExpire(),
                l.getRemark(), l.getCreatedAt())).toList();
        return new UserAccess(brief(u), modules, logVOs);
    }

    // ==================== 工具 ====================

    private LambdaQueryWrapper<ActivationCode> codeWrapper(CodeQuery q) {
        LambdaQueryWrapper<ActivationCode> w = Wrappers.<ActivationCode>lambdaQuery()
                .eq(q.getModuleId() != null, ActivationCode::getModuleId, q.getModuleId())
                .eq(q.getStatus() != null, ActivationCode::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getBatchName()), ActivationCode::getBatchName, q.getBatchName())
                .orderByDesc(ActivationCode::getId);
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            String code = ActivationService.normalize(kw);
            List<Long> userIds = userMapper.selectList(Wrappers.<AppUser>lambdaQuery().select(AppUser::getId)
                            .eq(AppUser::getPhone, kw).or().eq(AppUser::getUsername, kw.toLowerCase()))
                    .stream().map(AppUser::getId).toList();
            w.and(x -> {
                x.like(!code.isEmpty(), ActivationCode::getCode, code);
                if (!userIds.isEmpty()) {
                    x.or().in(ActivationCode::getUsedBy, userIds);
                }
                if (code.isEmpty() && userIds.isEmpty()) {
                    x.apply("1 = 0");
                }
            });
        }
        return w;
    }

    private CodeVO toVO(ActivationCode c, Map<Long, String> modules, Map<Long, AppUser> users) {
        AppUser u = c.getUsedBy() == null ? null : users.get(c.getUsedBy());
        return new CodeVO(c.getId(), ActivationService.format(c.getCode()), c.getModuleId(), modules.get(c.getModuleId()),
                c.getBatchName(), c.getValidityType(), c.getValidDays(), c.getExpireAt(), c.getStatus(),
                u == null ? null : brief(u), c.getUsedAt(), c.getAccessUntil(), c.getCreatedAt());
    }

    private Map<Long, String> moduleNames() {
        return moduleMapper.selectList(null).stream().collect(Collectors.toMap(ExamModule::getId, ExamModule::getName));
    }

    private Map<Long, AppUser> usersOf(Collection<Long> ids) {
        Set<Long> nonNull = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return nonNull.isEmpty() ? Map.of() : userMapper.selectByIds(nonNull).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private static UserBrief brief(AppUser u) {
        return new UserBrief(u.getId(), u.getUsername(), u.getPhone(), u.getNickname());
    }

    private static String validityText(ActivationCode c) {
        return c.getValidityType() == ActivationCode.TYPE_DAYS
                ? "激活后 " + c.getValidDays() + " 天"
                : "至 " + c.getExpireAt().toLocalDate();
    }

    private static String statusText(Integer s) {
        return s == ActivationCode.USED ? "已使用" : s == ActivationCode.VOIDED ? "已作废" : "未使用";
    }

    private static String time(LocalDateTime t) {
        return t == null ? "" : t.format(TIME);
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"");
        // 防止 Excel 把 = + - @ 开头的内容当公式
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return s.contains(",") || s.contains("\n") || s.contains("\"") ? "\"" + s + "\"" : s;
    }
}
