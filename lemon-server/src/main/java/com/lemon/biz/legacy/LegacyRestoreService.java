package com.lemon.biz.legacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.access.service.ActivationService;
import com.lemon.biz.legacy.mapper.LegacyMapper;
import com.lemon.biz.legacy.mapper.LegacyMapper.LegacyUser;
import com.lemon.common.BizException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 老系统错题恢复：用户输入当时的激活码或订单号，找到老账号，把老错题恢复进错题集。
 * <ul>
 *   <li>恢复成功后赠送「政治」「教育学」两个模块到 2027-01-01（老系统班级的有效期）</li>
 *   <li>一个老账号只能被一个新账号认领；同一个新账号可以认领多个老账号（用不同的激活码买过不同的东西）</li>
 *   <li>查不到的尝试按用户计数，超过上限暂时锁定，防止枚举订单号</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LegacyRestoreService {

    private static final int MAX_FAILS = 10;
    private static final Duration FAIL_WINDOW = Duration.ofHours(1);

    private final LegacyMapper mapper;
    private final ActivationService activationService;
    private final StringRedisTemplate redis;

    /**
     * 查询结果
     *
     * @param state      available 可以恢复 / mine 本账号已恢复过 / taken 已被其他账号恢复
     * @param wrongTotal 老错题题目数
     * @param restorable 能恢复到现在题库的题目数
     */
    public record Preview(String state, String nickname, int wrongTotal, int restorable) {
    }

    /**
     * @param grantedModules 同时赠送开通的模块（政治、教育学，到 2027-01-01）
     * @param grantedUntil   赠送的到期时间
     */
    public record Result(int restored, int wrongTotal, List<String> grantedModules,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime grantedUntil) {
    }

    public Preview lookup(Long userId, String rawCode) {
        LegacyUser u = find(userId, rawCode);
        String state = u.claimedBy() == null ? "available" : Objects.equals(u.claimedBy(), userId) ? "mine" : "taken";
        return new Preview(state, u.nickname(), u.wrongTotal(), mapper.countRestorable(u.oldUserId()));
    }

    @Transactional
    public Result claim(Long userId, String rawCode) {
        LegacyUser found = find(userId, rawCode);
        LegacyUser u = mapper.lockUser(found.oldUserId());
        BizException.throwIf(Objects.equals(u.claimedBy(), userId), "这个账号的错题已经恢复过了，可以在错题集里查看");
        BizException.throwIf(u.claimedBy() != null, "这个账号的错题已被其他账号恢复，如有疑问请联系客服");
        int restorable = mapper.countRestorable(u.oldUserId());
        mapper.restore(userId, u.oldUserId());
        mapper.markClaimed(u.oldUserId(), userId);
        // 老系统的付费用户：赠送政治、教育学
        List<String> granted = activationService.grantLegacy(userId);
        return new Result(restorable, u.wrongTotal(), granted, granted.isEmpty() ? null : ActivationService.LEGACY_UNTIL);
    }

    private LegacyUser find(Long userId, String rawCode) {
        String failKey = "legacy:fail:" + userId;
        String fails = redis.opsForValue().get(failKey);
        BizException.throwIf(fails != null && Integer.parseInt(fails) >= MAX_FAILS, "尝试次数过多，请 1 小时后再试");
        String code = normalize(rawCode);
        BizException.throwIf(code.length() < 6, "请输入完整的激活码或订单号");
        LegacyUser u = mapper.findByKey(code);
        if (u == null) {
            Long n = redis.opsForValue().increment(failKey);
            if (n != null && n == 1) {
                redis.expire(failKey, FAIL_WINDOW);
            }
            throw new BizException("没有找到对应的记录，请核对激活码或订单号");
        }
        return u;
    }

    /** 去掉空白，字母统一大写（激活码不区分大小写） */
    static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
