package com.lemon.biz.access.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lemon.biz.access.entity.ActivationCode;
import com.lemon.biz.access.entity.ModuleAccessLog;
import com.lemon.biz.access.entity.UserModuleAccess;
import com.lemon.biz.access.mapper.ActivationCodeMapper;
import com.lemon.biz.access.mapper.ModuleAccessLogMapper;
import com.lemon.biz.access.mapper.UserModuleAccessMapper;
import com.lemon.biz.content.entity.ExamModule;
import com.lemon.biz.content.mapper.ExamModuleMapper;
import com.lemon.common.BizException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 激活码兑换、生成，以及后台给用户开通 / 取消模块权限。
 * <p>
 * 到期规则：
 * <ul>
 *   <li>按天数的码：从「现在」和「当前到期时间」里较晚的那个往后加天数（没到期再激活就是顺延）</li>
 *   <li>按固定日期的码：到期时间取「码的到期日」和「当前到期时间」里较晚的那个</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ActivationService {

    /** 激活码字符：去掉容易看错的 0 O 1 I L */
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;
    private static final int MAX_FAILS = 10;
    private static final Duration FAIL_WINDOW = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivationCodeMapper codeMapper;
    private final UserModuleAccessMapper accessMapper;
    private final ModuleAccessLogMapper logMapper;
    private final ExamModuleMapper moduleMapper;
    private final StringRedisTemplate redis;

    /** 激活结果 */
    public record Activated(Long moduleId, String moduleName,
                            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expireAt, boolean extended) {
    }

    // ==================== 用户激活 ====================

    @Transactional(rollbackFor = Exception.class)
    public Activated redeem(Long userId, String rawCode) {
        String failKey = "activate:fail:" + userId;
        String fails = redis.opsForValue().get(failKey);
        BizException.throwIf(fails != null && Integer.parseInt(fails) >= MAX_FAILS, "尝试次数过多，请 1 小时后再试");

        String code = normalize(rawCode);
        ActivationCode c = code.length() < 8 ? null : codeMapper.selectOne(Wrappers.<ActivationCode>lambdaQuery()
                .eq(ActivationCode::getCode, code).last("FOR UPDATE"));
        if (c == null || c.getStatus() == ActivationCode.VOIDED) {
            Long n = redis.opsForValue().increment(failKey);
            if (n != null && n == 1) {
                redis.expire(failKey, FAIL_WINDOW);
            }
            throw new BizException("激活码不存在，请核对后再输入");
        }
        BizException.throwIf(c.getStatus() == ActivationCode.USED,
                userId.equals(c.getUsedBy()) ? "这个激活码你已经用过了" : "这个激活码已经被使用");
        BizException.throwIf(c.getValidityType() == ActivationCode.TYPE_DATE && !c.getExpireAt().isAfter(LocalDateTime.now()),
                "这个激活码已过期");
        ExamModule module = BizException.notNull(moduleMapper.selectById(c.getModuleId()), "激活码对应的模块不存在");

        UserModuleAccess access = lockAccess(userId, c.getModuleId());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime current = access == null ? null : access.getExpireAt();
        boolean active = current != null && current.isAfter(now);
        LocalDateTime newExpire = c.getValidityType() == ActivationCode.TYPE_DAYS
                ? (active ? current : now).plusDays(c.getValidDays())
                : (active && current.isAfter(c.getExpireAt()) ? current : c.getExpireAt());
        saveAccess(access, userId, c.getModuleId(), newExpire);

        c.setStatus(ActivationCode.USED);
        c.setUsedBy(userId);
        c.setUsedAt(now);
        c.setAccessUntil(newExpire);
        codeMapper.updateById(c);
        log(userId, c.getModuleId(), ModuleAccessLog.SOURCE_CODE, c.getId(), null, current, newExpire, null);
        return new Activated(module.getId(), module.getName(), newExpire, active);
    }

    // ==================== 后台：生成 / 作废激活码 ====================

    /**
     * 批量生成激活码
     *
     * @param validDays  validityType=1 时必填
     * @param expireDate validityType=2 时必填，到这一天 23:59:59 为止
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> generate(Long adminId, Long moduleId, int count, int validityType, Integer validDays,
                                 LocalDate expireDate, String batchName) {
        BizException.notNull(moduleMapper.selectById(moduleId), "模块不存在");
        BizException.throwIf(count < 1 || count > 1000, "一次最多生成 1000 个");
        LocalDateTime expireAt = null;
        if (validityType == ActivationCode.TYPE_DAYS) {
            BizException.throwIf(validDays == null || validDays < 1 || validDays > 3650, "有效天数需在 1~3650 之间");
        } else if (validityType == ActivationCode.TYPE_DATE) {
            BizException.throwIf(expireDate == null || !expireDate.isAfter(LocalDate.now().minusDays(1)), "到期日期不能早于今天");
            expireAt = expireDate.atTime(LocalTime.of(23, 59, 59));
            validDays = null;
        } else {
            throw new BizException("有效期类型不正确");
        }
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ActivationCode c = new ActivationCode();
            c.setModuleId(moduleId);
            c.setBatchName(batchName == null || batchName.isBlank() ? null : batchName.trim());
            c.setValidityType(validityType);
            c.setValidDays(validDays);
            c.setExpireAt(expireAt);
            c.setStatus(ActivationCode.UNUSED);
            c.setCreatedBy(adminId);
            for (int tries = 0; ; tries++) {
                c.setCode(randomCode());
                try {
                    codeMapper.insert(c);
                    break;
                } catch (DuplicateKeyException e) {
                    BizException.throwIf(tries >= 5, "生成激活码失败，请重试");
                    c.setId(null);
                }
            }
            codes.add(c.getCode());
        }
        return codes;
    }

    /** 作废：只能作废还没使用的码 */
    public void voidCode(Long id) {
        ActivationCode c = BizException.notNull(codeMapper.selectById(id), "激活码不存在");
        BizException.throwIf(c.getStatus() != ActivationCode.UNUSED, "只能作废未使用的激活码");
        c.setStatus(ActivationCode.VOIDED);
        codeMapper.updateById(c);
    }

    // ==================== 后台：给用户开通 / 取消 ====================

    /**
     * 开通或延长：days 和 until 二选一。days 从「现在」和「当前到期时间」较晚的那个往后加；until 直接设为该日 23:59:59。
     */
    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime grant(Long adminId, Long userId, Long moduleId, Integer days, LocalDate until, String remark) {
        BizException.notNull(moduleMapper.selectById(moduleId), "模块不存在");
        BizException.throwIf((days == null) == (until == null), "请填写延长天数或到期日期（二选一）");
        UserModuleAccess access = lockAccess(userId, moduleId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime current = access == null ? null : access.getExpireAt();
        LocalDateTime newExpire;
        if (days != null) {
            BizException.throwIf(days < 1 || days > 3650, "天数需在 1~3650 之间");
            newExpire = (current != null && current.isAfter(now) ? current : now).plusDays(days);
        } else {
            BizException.throwIf(until.isBefore(LocalDate.now()), "到期日期不能早于今天");
            newExpire = until.atTime(LocalTime.of(23, 59, 59));
        }
        saveAccess(access, userId, moduleId, newExpire);
        log(userId, moduleId, ModuleAccessLog.SOURCE_ADMIN_GRANT, null, adminId, current, newExpire, remark);
        return newExpire;
    }

    /** 取消：立即到期 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long adminId, Long userId, Long moduleId, String remark) {
        UserModuleAccess access = lockAccess(userId, moduleId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        BizException.throwIf(access == null || !access.getExpireAt().isAfter(now), "该用户的这个模块本来就没有激活");
        LocalDateTime before = access.getExpireAt();
        saveAccess(access, userId, moduleId, now);
        log(userId, moduleId, ModuleAccessLog.SOURCE_ADMIN_REVOKE, null, adminId, before, now, remark);
    }

    // ==================== 工具 ====================

    /** 去掉空格、横线等分隔符，字母统一大写 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    /** 展示格式：XXXX-XXXX-XXXX */
    public static String format(String code) {
        return code == null ? null : code.replaceAll("(.{4})(?!$)", "$1-");
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private UserModuleAccess lockAccess(Long userId, Long moduleId) {
        return accessMapper.selectOne(Wrappers.<UserModuleAccess>lambdaQuery()
                .eq(UserModuleAccess::getUserId, userId).eq(UserModuleAccess::getModuleId, moduleId).last("FOR UPDATE"));
    }

    private void saveAccess(UserModuleAccess access, Long userId, Long moduleId, LocalDateTime expireAt) {
        if (access == null) {
            UserModuleAccess a = new UserModuleAccess();
            a.setUserId(userId);
            a.setModuleId(moduleId);
            a.setExpireAt(expireAt);
            accessMapper.insert(a);
        } else {
            access.setExpireAt(expireAt);
            accessMapper.updateById(access);
        }
    }

    private void log(Long userId, Long moduleId, int source, Long codeId, Long adminId,
                     LocalDateTime before, LocalDateTime after, String remark) {
        ModuleAccessLog l = new ModuleAccessLog();
        l.setUserId(userId);
        l.setModuleId(moduleId);
        l.setSource(source);
        l.setCodeId(codeId);
        l.setAdminId(adminId);
        l.setBeforeExpire(before);
        l.setAfterExpire(after);
        l.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        logMapper.insert(l);
    }
}
