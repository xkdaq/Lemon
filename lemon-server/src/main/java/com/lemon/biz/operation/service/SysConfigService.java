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
    /** 小程序是否显示激活码入口：1 显示 / 0 隐藏（网页端始终显示） */
    public static final String MP_ACTIVATION = "app.mp_activation";
    /** 小程序是否显示「恢复老系统错题」入口：1 显示 / 0 隐藏（网页端始终显示） */
    public static final String MP_RESTORE = "app.mp_restore";
    /** 微信小程序 AppID / AppSecret：留空时用服务器环境变量（见 WxMiniClient） */
    public static final String WX_APPID = "wx.appid";
    public static final String WX_SECRET = "wx.secret";
    /** 密钥类参数：列表里只返回后 4 位，管理端只能覆盖、不能读出原值 */
    private static final java.util.Set<String> SECRET_KEYS = java.util.Set.of(WX_SECRET);

    private record Rule(Predicate<String> check, String hint) {
    }

    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry(MP_ACTIVATION, new Rule(v -> v.matches("^[01]$"), "请填 1（显示）或 0（隐藏）")),
            Map.entry(MP_RESTORE, new Rule(v -> v.matches("^[01]$"), "请填 1（显示）或 0（隐藏）")),
            Map.entry(WRONG_REMOVE_STREAK, new Rule(v -> v.matches("^([1-9]|10)$"), "请输入 1~10 的整数")),
            Map.entry("app.trial_questions", new Rule(v -> v.matches("^([0-9]|[1-9][0-9]|100)$"), "请输入 0~100 的整数")),
            Map.entry(SERVICE_WECHAT, new Rule(v -> v.matches("^[A-Za-z0-9_-]{0,40}$"), "微信号只能包含字母、数字、下划线和横线")),
            Map.entry(SERVICE_NOTE, new Rule(v -> v.length() <= 100, "最多 100 字")),
            Map.entry("notify.feishu_webhook", new Rule(v -> v.isEmpty() || v.matches("^https://open\\.(feishu\\.cn|larksuite\\.com)/open-apis/bot/v2/hook/[A-Za-z0-9-]+$"),
                    "请填写飞书群机器人的 webhook 地址（https://open.feishu.cn/open-apis/bot/v2/hook/...）")),
            Map.entry("notify.feishu_secret", new Rule(v -> v.length() <= 100, "最多 100 字")),
            Map.entry("app.register_gift_until", new Rule(SysConfigService::isDateOrEmpty, "请填写日期，格式 2027-01-01；留空表示不赠送")),
            Map.entry(APP_THEME, new Rule(v -> v.matches("^(paper|indigo|teal|amber|rose|violet|coffee|graphite)$|^#[0-9a-fA-F]{6}$"),
                    "请选择预设主题，或填写 #RRGGBB 格式的主色")),
            Map.entry(WX_APPID, new Rule(v -> v.matches("^(wx[0-9a-z]{16})?$"), "AppID 是 wx 开头的 18 位字符")),
            Map.entry(WX_SECRET, new Rule(v -> v.matches("^[0-9A-Za-z]{32}$"), "AppSecret 是 32 位字母和数字")));

    private final SysConfigMapper mapper;

    private static boolean isDateOrEmpty(String v) {
        if (v.isEmpty()) {
            return true;
        }
        try {
            java.time.LocalDate.parse(v);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /** 管理端列表：密钥类参数只返回后 4 位（如 ****a1b2），未设置时为空 */
    public List<SysConfig> list() {
        List<SysConfig> list = mapper.selectList(Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getId));
        for (SysConfig c : list) {
            String v = c.getConfigValue();
            if (SECRET_KEYS.contains(c.getConfigKey()) && v != null && !v.isEmpty()) {
                c.setConfigValue("****" + v.substring(Math.max(0, v.length() - 4)));
            }
        }
        return list;
    }

    public static boolean isSecret(String key) {
        return SECRET_KEYS.contains(key);
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
