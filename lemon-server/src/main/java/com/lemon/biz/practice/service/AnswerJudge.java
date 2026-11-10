package com.lemon.biz.practice.service;

import com.lemon.biz.content.enums.QuestionType;
import com.lemon.common.BizException;
import java.util.Locale;
import java.util.TreeSet;

/**
 * 判题（纯函数）。
 * <ul>
 *   <li>客观题：用户答案规范成去重升序字母后与标准答案比较；多选必须完全一致才算对</li>
 *   <li>主观题：无法自动判，练习模式由用户自评，答案传 "1"（会）/ "0"（不会）</li>
 * </ul>
 */
public final class AnswerJudge {

    public static final String SELF_RIGHT = "1";
    public static final String SELF_WRONG = "0";

    private AnswerJudge() {
    }

    /** 规范化客观题答案：只保留字母，大写、去重、升序，如 "b, a" → "AB" */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        TreeSet<Character> keys = new TreeSet<>();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                keys.add(c);
            }
        }
        StringBuilder sb = new StringBuilder();
        keys.forEach(sb::append);
        return sb.toString();
    }

    /**
     * @param type          题型
     * @param correctAnswer 标准答案（主观题为 null）
     * @param userAnswer    用户答案（主观题为自评 "1"/"0"）
     */
    public static boolean judge(int type, String correctAnswer, String userAnswer) {
        QuestionType qt = QuestionType.of(type);
        if (qt == QuestionType.SUBJECTIVE) {
            BizException.throwIf(!SELF_RIGHT.equals(userAnswer) && !SELF_WRONG.equals(userAnswer), "主观题请选择“会”或“不会”");
            return SELF_RIGHT.equals(userAnswer);
        }
        String user = normalize(userAnswer);
        BizException.throwIf(user.isEmpty(), "请选择答案");
        BizException.throwIf(qt != QuestionType.MULTIPLE && user.length() > 1, "该题只能选一个答案");
        return user.equals(normalize(correctAnswer));
    }
}
