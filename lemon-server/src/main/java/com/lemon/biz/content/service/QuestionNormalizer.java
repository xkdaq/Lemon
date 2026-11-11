package com.lemon.biz.content.service;

import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.biz.content.enums.QuestionType;
import com.lemon.common.BizException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按题型校验并规范化选项和答案（纯函数，无数据库依赖，管理端录入和 Excel 导入共用）。
 * <ul>
 *   <li>单选：2~8 个选项，答案恰好 1 个且在选项内</li>
 *   <li>多选：2~8 个选项，答案至少 1 个，规范成去重升序，如 "ABD"</li>
 *   <li>判断：选项可不填，默认 A 正确 / B 错误；答案可填 A/B、对/错、正确/错误、√/×、T/F</li>
 *   <li>主观：不能有选项，答案置空；解析即参考答案，可以不填（背诵题：题干本身就是要背的内容）</li>
 * </ul>
 */
public final class QuestionNormalizer {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 8;

    static final List<OptionItem> JUDGE_DEFAULT_OPTIONS = List.of(
            new OptionItem("A", "正确"), new OptionItem("B", "错误"));

    private static final Map<String, String> JUDGE_ALIASES = Map.ofEntries(
            Map.entry("对", "A"), Map.entry("正确", "A"), Map.entry("√", "A"), Map.entry("T", "A"),
            Map.entry("TRUE", "A"), Map.entry("Y", "A"),
            Map.entry("错", "B"), Map.entry("错误", "B"), Map.entry("×", "B"), Map.entry("X", "B"),
            Map.entry("F", "B"), Map.entry("FALSE", "B"), Map.entry("N", "B"));

    /** 规范化结果：options 为空表示无选项（主观题） */
    public record Result(List<OptionItem> options, String answer) {
    }

    private QuestionNormalizer() {
    }

    public static Result normalize(int typeCode, List<OptionItem> rawOptions, String rawAnswer, String analysis) {
        QuestionType type = QuestionType.of(typeCode);
        return switch (type) {
            case SINGLE -> choice(rawOptions, rawAnswer, false);
            case MULTIPLE -> choice(rawOptions, rawAnswer, true);
            case JUDGE -> judge(rawOptions, rawAnswer);
            case SUBJECTIVE -> subjective(rawOptions);
        };
    }

    private static Result choice(List<OptionItem> rawOptions, String rawAnswer, boolean multiple) {
        List<OptionItem> options = normalizeOptions(rawOptions);
        BizException.throwIf(options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS,
                "选择题需要 " + MIN_OPTIONS + "~" + MAX_OPTIONS + " 个选项");
        String answer = normalizeAnswerKeys(rawAnswer, options);
        BizException.throwIf(answer.isEmpty(), "请填写答案");
        BizException.throwIf(!multiple && answer.length() != 1, "单选题只能有一个答案");
        return new Result(options, answer);
    }

    private static Result judge(List<OptionItem> rawOptions, String rawAnswer) {
        boolean useDefault = rawOptions == null || rawOptions.isEmpty();
        List<OptionItem> options = useDefault ? JUDGE_DEFAULT_OPTIONS : normalizeOptions(rawOptions);
        BizException.throwIf(options.size() != 2, "判断题只能有 2 个选项");
        BizException.throwIf(rawAnswer == null || rawAnswer.isBlank(), "请填写答案");

        String trimmed = rawAnswer.trim().toUpperCase(Locale.ROOT);
        String alias = useDefault ? JUDGE_ALIASES.get(trimmed) : null;
        String answer = alias != null ? alias : normalizeAnswerKeys(rawAnswer, options);
        BizException.throwIf(answer.length() != 1, "判断题答案只能是一个");
        return new Result(options, answer);
    }

    private static Result subjective(List<OptionItem> rawOptions) {
        BizException.throwIf(rawOptions != null && !rawOptions.isEmpty(), "主观题不能有选项");
        return new Result(List.of(), null);
    }

    /** 去掉空选项，key 统一大写，key 为空时按位置补字母；key 不能重复 */
    static List<OptionItem> normalizeOptions(List<OptionItem> raw) {
        List<OptionItem> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (OptionItem item : raw) {
            if (item == null || item.content() == null || item.content().isBlank()) {
                continue;
            }
            String key = item.key() == null || item.key().isBlank()
                    ? String.valueOf((char) ('A' + result.size()))
                    : item.key().trim().toUpperCase(Locale.ROOT);
            BizException.throwIf(key.length() != 1 || key.charAt(0) < 'A' || key.charAt(0) > 'Z',
                    "选项标识只能是单个字母：" + key);
            BizException.throwIf(!seen.add(key), "选项 " + key + " 重复");
            result.add(new OptionItem(key, item.content().trim()));
        }
        return result;
    }

    /** 从答案字符串中提取字母（容忍 "A,B D"、"abd" 等写法），校验都在选项内，返回去重升序 */
    static String normalizeAnswerKeys(String rawAnswer, List<OptionItem> options) {
        if (rawAnswer == null) {
            return "";
        }
        Set<String> validKeys = new HashSet<>();
        options.forEach(o -> validKeys.add(o.key()));
        TreeSet<Character> keys = new TreeSet<>();
        for (char c : rawAnswer.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                BizException.throwIf(!validKeys.contains(String.valueOf(c)), "答案 " + c + " 不在选项中");
                keys.add(c);
            } else if (!Character.isWhitespace(c) && ",，、;；/".indexOf(c) < 0) {
                throw new BizException("答案格式不正确：" + rawAnswer);
            }
        }
        StringBuilder sb = new StringBuilder();
        keys.forEach(sb::append);
        return sb.toString();
    }
}
