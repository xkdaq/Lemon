package com.lemon.biz.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lemon.biz.operation.entity.SysConfig;
import com.lemon.biz.operation.mapper.SysConfigMapper;
import com.lemon.common.BizException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统参数。参数项由迁移脚本初始化，管理端只能改值，不能增删；每个参数在 RULES 里登记取值校验。
 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    public static final String WRONG_REMOVE_STREAK = "wrong.remove.streak";

    private record Rule(Predicate<String> check, String hint) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            WRONG_REMOVE_STREAK, new Rule(v -> v.matches("^([1-9]|10)$"), "请输入 1~10 的整数"));

    private final SysConfigMapper mapper;

    public List<SysConfig> list() {
        return mapper.selectList(Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getId));
    }

    public void update(String key, String value) {
        SysConfig config = BizException.notNull(mapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key)), "参数不存在");
        String v = value == null ? "" : value.trim();
        Rule rule = RULES.get(key);
        BizException.throwIf(rule != null && !rule.check().test(v), rule == null ? "" : rule.hint());
        config.setConfigValue(v);
        mapper.updateById(config);
    }

    public int getInt(String key, int defaultValue) {
        SysConfig config = mapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        try {
            return config == null ? defaultValue : Integer.parseInt(config.getConfigValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
