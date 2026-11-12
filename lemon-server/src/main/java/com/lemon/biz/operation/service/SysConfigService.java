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
    /** 用户端主题配色：预设名（与用户端 utils/theme.ts 一致）或自定义主色 #RRGGBB */
    public static final String APP_THEME = "app.theme";
    public static final String DEFAULT_THEME = "paper";
    /** 客服微信号、客服说明（如服务时间） */
    public static final String SERVICE_WECHAT = "app.service_wechat";
    public static final String SERVICE_NOTE = "app.service_note";

    private record Rule(Predicate<String> check, String hint) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            WRONG_REMOVE_STREAK, new Rule(v -> v.matches("^([1-9]|10)$"), "请输入 1~10 的整数"),
            "app.trial_questions", new Rule(v -> v.matches("^([0-9]|[1-9][0-9]|100)$"), "请输入 0~100 的整数"),
            SERVICE_WECHAT, new Rule(v -> v.matches("^[A-Za-z0-9_-]{0,40}$"), "微信号只能包含字母、数字、下划线和横线"),
            SERVICE_NOTE, new Rule(v -> v.length() <= 100, "最多 100 字"),
            "app.register_gift_days", new Rule(v -> v.matches("^\\d{1,4}$") && Integer.parseInt(v) <= 3650, "请输入 0~3650 的整数"),
            APP_THEME, new Rule(v -> v.matches("^(paper|indigo|teal|amber|rose|violet|coffee|graphite)$|^#[0-9a-fA-F]{6}$"),
                    "请选择预设主题，或填写 #RRGGBB 格式的主色"));

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

    public String getString(String key, String defaultValue) {
        SysConfig config = mapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        return config == null || config.getConfigValue().isBlank() ? defaultValue : config.getConfigValue();
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
