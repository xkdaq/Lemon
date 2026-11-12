package com.lemon.biz.access.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.access.entity.ActivationCode;
import com.lemon.biz.access.mapper.ActivationCodeMapper;
import com.lemon.biz.access.mapper.UserModuleAccessMapper;
import com.lemon.biz.access.mapper.UserModuleAccessMapper.ModuleUserRow;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.biz.system.entity.SysAdmin;
import com.lemon.biz.system.mapper.SysAdminMapper;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端「模块用户」：每个模块开通了哪些用户、怎么开通的、什么时候开通、什么时候到期
 */
@Service
@RequiredArgsConstructor
public class ModuleUserAdminService {

    /** 「快到期」：7 天内 */
    public static final int EXPIRING_DAYS = 7;
    private static final int EXPORT_LIMIT = 20000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserModuleAccessMapper accessMapper;
    private final ActivationCodeMapper codeMapper;
    private final ExamModuleMapper moduleMapper;
    private final SysAdminMapper adminMapper;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageQuery {
        /** 手机号 / 用户名 / 昵称 / 激活码 */
        private String keyword;
        private Long moduleId;
        /** 1 有效 2 7 天内到期 3 已过期 */
        private Integer status;
        /** 最近一次开通方式：1 激活码 2 后台开通 4 老系统赠送 5 注册赠送 */
        private Integer source;
        /** 最近一次激活时间范围 */
        private LocalDate activatedFrom;
        private LocalDate activatedTo;
        /** 排序：lastAt（默认）/ expireAt / createdAt */
        private String orderBy;
        /** asc / desc（默认 desc） */
        private String order;
    }

    public record ModuleUserVO(Long id, Long userId, String username, String phone, String nickname,
                               Long moduleId, String moduleName,
                               Integer source, String code, String adminName,
                               @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime firstAt,
                               @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastAt,
                               Integer grantCount,
                               @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expireAt,
                               boolean active,
                               /** 剩余天数（不足 1 天算 1 天）；已过期为 null */
                               Long remainDays) {
    }

    public record ModuleStat(Long moduleId, String moduleName, boolean free, long active, long expiring, long expired) {
    }

    public PageResult<ModuleUserVO> page(Query q) {
        LocalDateTime now = LocalDateTime.now();
        var page = accessMapper.pageModuleUsers(q.toPage(), wrapper(q, now));
        Converter c = converter(page.getRecords(), now);
        return PageResult.of(page, c::toVO);
    }

    /** 各模块人数（收费模块，以及有开通记录的免费模块） */
    public List<ModuleStat> stats() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, UserModuleAccessMapper.ModuleCount> counts = accessMapper.countByModule(now, now.plusDays(EXPIRING_DAYS))
                .stream().collect(Collectors.toMap(UserModuleAccessMapper.ModuleCount::getModuleId, Function.identity()));
        return moduleMapper.selectList(Wrappers.<ExamModule>lambdaQuery()
                        .orderByAsc(ExamModule::getSort).orderByAsc(ExamModule::getId))
                .stream()
                .filter(m -> !isFree(m) || counts.containsKey(m.getId()))
                .map(m -> {
                    var cnt = counts.get(m.getId());
                    return new ModuleStat(m.getId(), m.getName(), isFree(m),
                            cnt == null ? 0 : n(cnt.getActive()), cnt == null ? 0 : n(cnt.getExpiring()),
                            cnt == null ? 0 : n(cnt.getExpired()));
                }).toList();
    }

    /** 导出 CSV（带 BOM），最多 2 万行 */
    public byte[] export(Query q) {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<ModuleUserRow> w = wrapper(q, now);
        w.last("LIMIT " + EXPORT_LIMIT);
        List<ModuleUserRow> rows = accessMapper.listModuleUsers(w);
        Converter c = converter(rows, now);
        StringBuilder sb = new StringBuilder("﻿用户名,昵称,手机号,模块,状态,到期时间,剩余天数,开通方式,激活码,操作管理员,最近激活时间,首次开通时间,开通次数\n");
        for (ModuleUserRow r : rows) {
            ModuleUserVO v = c.toVO(r);
            sb.append(String.join(",",
                    csv(v.username()), csv(v.nickname()), csv(v.phone()), csv(v.moduleName()),
                    v.active() ? "有效" : "已过期", time(v.expireAt()),
                    v.remainDays() == null ? "" : String.valueOf(v.remainDays()),
                    sourceText(v.source()), csv(v.code()), csv(v.adminName()),
                    time(v.lastAt()), time(v.firstAt()), String.valueOf(v.grantCount()))).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 查询条件 ====================

    private QueryWrapper<ModuleUserRow> wrapper(Query q, LocalDateTime now) {
        QueryWrapper<ModuleUserRow> w = Wrappers.query();
        w.eq(q.getModuleId() != null, "a.module_id", q.getModuleId());
        if (q.getStatus() != null) {
            switch (q.getStatus()) {
                case 1 -> w.gt("a.expire_at", now);
                case 2 -> w.gt("a.expire_at", now).le("a.expire_at", now.plusDays(EXPIRING_DAYS));
                case 3 -> w.le("a.expire_at", now);
                default -> { }
            }
        }
        w.eq(q.getSource() != null, "l.source", q.getSource());
        w.ge(q.getActivatedFrom() != null, "l.created_at", q.getActivatedFrom() == null ? null : q.getActivatedFrom().atStartOfDay());
        w.lt(q.getActivatedTo() != null, "l.created_at", q.getActivatedTo() == null ? null : q.getActivatedTo().plusDays(1).atStartOfDay());
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            String code = ActivationService.normalize(kw);
            w.and(x -> {
                x.like("u.phone", kw).or().like("u.username", kw.toLowerCase()).or().like("u.nickname", kw);
                // 激活码至少 4 位才按激活码搜，避免短数字把所有用过码的人都搜出来
                if (code.length() >= 4) {
                    x.or().exists("SELECT 1 FROM activation_code ac WHERE ac.used_by = a.user_id "
                            + "AND ac.module_id = a.module_id AND ac.code LIKE {0}", "%" + code + "%");
                }
            });
        }
        boolean asc = "asc".equalsIgnoreCase(q.getOrder());
        String col = switch (Objects.requireNonNullElse(q.getOrderBy(), "lastAt")) {
            case "expireAt" -> "a.expire_at";
            case "createdAt" -> "a.created_at";
            default -> "last_at";
        };
        w.orderBy(true, asc, col).orderByDesc("a.id");
        return w;
    }

    // ==================== 转换 ====================

    private record Converter(Map<Long, String> modules, Map<Long, String> codes, Map<Long, String> admins,
                             LocalDateTime now) {
        ModuleUserVO toVO(ModuleUserRow r) {
            boolean active = r.getExpireAt() != null && r.getExpireAt().isAfter(now);
            Long remain = null;
            if (active) {
                long minutes = Duration.between(now, r.getExpireAt()).toMinutes();
                remain = Math.max(1, (minutes + 24 * 60 - 1) / (24 * 60));
            }
            return new ModuleUserVO(r.getId(), r.getUserId(), r.getUsername(), r.getPhone(), r.getNickname(),
                    r.getModuleId(), modules.get(r.getModuleId()),
                    r.getLastSource(), get(codes, r.getLastCodeId()), get(admins, r.getLastAdminId()),
                    r.getCreatedAt(), r.getLastAt(), r.getGrantCount() == null ? 0 : r.getGrantCount(),
                    r.getExpireAt(), active, remain);
        }

        private static String get(Map<Long, String> m, Long id) {
            return id == null ? null : m.get(id);
        }
    }

    private Converter converter(Collection<ModuleUserRow> rows, LocalDateTime now) {
        Map<Long, String> modules = moduleMapper.selectList(null).stream()
                .collect(Collectors.toMap(ExamModule::getId, ExamModule::getName));
        Set<Long> codeIds = rows.stream().map(ModuleUserRow::getLastCodeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> codes = codeIds.isEmpty() ? Map.of() : codeMapper.selectByIds(codeIds).stream()
                .collect(Collectors.toMap(ActivationCode::getId, c -> ActivationService.format(c.getCode())));
        Set<Long> adminIds = rows.stream().map(ModuleUserRow::getLastAdminId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> admins = adminIds.isEmpty() ? Map.of() : adminMapper.selectByIds(adminIds).stream()
                .collect(Collectors.toMap(SysAdmin::getId, a -> a.getNickname() != null ? a.getNickname() : a.getUsername()));
        return new Converter(modules, codes, admins, now);
    }

    private static boolean isFree(ExamModule m) {
        return m.getFree() != null && m.getFree() == 1;
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    private static String sourceText(Integer s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case 1 -> "激活码";
            case 2 -> "后台开通";
            case 4 -> "老系统赠送";
            case 5 -> "注册赠送";
            default -> "";
        };
    }

    private static String time(LocalDateTime t) {
        return t == null ? "" : t.format(TIME);
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return s.contains(",") || s.contains("\n") || s.contains("\"") ? "\"" + s + "\"" : s;
    }
}
