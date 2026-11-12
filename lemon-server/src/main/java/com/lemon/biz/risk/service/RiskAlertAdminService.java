package com.lemon.biz.risk.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.risk.entity.RiskAlert;
import com.lemon.biz.risk.mapper.RiskAlertMapper;
import com.lemon.biz.system.entity.SysAdmin;
import com.lemon.biz.system.mapper.SysAdminMapper;
import com.lemon.biz.user.entity.AppUser;
import com.lemon.biz.user.mapper.AppUserMapper;
import com.lemon.common.BizException;
import com.lemon.common.PageQuery;
import com.lemon.common.PageResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端「风险告警」：列表、标记已处理；工作台显示待处理数
 */
@Service
@RequiredArgsConstructor
public class RiskAlertAdminService {

    private final RiskAlertMapper mapper;
    private final AppUserMapper userMapper;
    private final SysAdminMapper adminMapper;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageQuery {
        /** 0 待处理 1 已处理 */
        private Integer status;
        private String alertType;
    }

    public record AlertVO(Long id, Long userId, String username, String phone, String nickname, Integer userStatus,
                          String alertType, @JsonFormat(pattern = "yyyy-MM-dd") LocalDate alertDate,
                          Integer metric, Integer threshold, String ip, Integer status,
                          String handledBy, @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime handledAt, String remark,
                          @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
                          @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {
    }

    public PageResult<AlertVO> page(Query q) {
        var page = mapper.selectPage(q.toPage(), Wrappers.<RiskAlert>lambdaQuery()
                .eq(q.getStatus() != null, RiskAlert::getStatus, q.getStatus())
                .eq(q.getAlertType() != null && !q.getAlertType().isBlank(), RiskAlert::getAlertType, q.getAlertType())
                .orderByAsc(RiskAlert::getStatus).orderByDesc(RiskAlert::getUpdatedAt).orderByDesc(RiskAlert::getId));
        var rows = page.getRecords();
        Map<Long, AppUser> users = rows.isEmpty() ? Map.of() : userMapper.selectByIds(
                        rows.stream().map(RiskAlert::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        var adminIds = rows.stream().map(RiskAlert::getHandledBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> admins = adminIds.isEmpty() ? Map.of() : adminMapper.selectByIds(adminIds).stream()
                .collect(Collectors.toMap(SysAdmin::getId, a -> a.getNickname() != null ? a.getNickname() : a.getUsername()));
        return PageResult.of(page, a -> {
            AppUser u = users.get(a.getUserId());
            return new AlertVO(a.getId(), a.getUserId(), u == null ? null : u.getUsername(), u == null ? null : u.getPhone(),
                    u == null ? null : u.getNickname(), u == null ? null : u.getStatus(),
                    a.getAlertType(), a.getAlertDate(), a.getMetric(), a.getThreshold(), a.getIp(), a.getStatus(),
                    a.getHandledBy() == null ? null : admins.get(a.getHandledBy()), a.getHandledAt(), a.getRemark(),
                    a.getCreatedAt(), a.getUpdatedAt());
        });
    }

    public void handle(Long adminId, Long id, String remark) {
        RiskAlert a = BizException.notNull(mapper.selectById(id), "告警不存在");
        a.setStatus(1);
        a.setHandledBy(adminId);
        a.setHandledAt(LocalDateTime.now().withNano(0));
        a.setRemark(remark == null ? null : remark.strip());
        mapper.updateById(a);
    }

    public long pendingCount() {
        return mapper.selectCount(Wrappers.<RiskAlert>lambdaQuery().eq(RiskAlert::getStatus, 0));
    }
}
