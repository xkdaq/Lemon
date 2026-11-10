package com.lemon.biz.practice.enums;

import com.lemon.common.BizException;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 做题模式
 */
@Getter
@AllArgsConstructor
public enum PracticeMode {

    /** 练习：每题作答后立即判题、显示解析；答错进错题集 */
    PRACTICE(1, "练习"),
    /** 背题：直接显示答案和解析，不记录作答 */
    RECITE(2, "背题"),
    /** 考试：限时，交卷后统一判分（仅试卷型题库） */
    EXAM(3, "考试"),
    /** 错题重练（M4） */
    WRONG(4, "错题重练"),
    /** 收藏练习（M4） */
    FAVORITE(5, "收藏练习"),
    /** 每日打卡：题目按组卷规则抽取，交卷即完成当天打卡 */
    PUNCH(6, "每日打卡");

    private final int code;
    private final String label;

    public static PracticeMode of(Integer code) {
        return Arrays.stream(values()).filter(m -> code != null && m.code == code).findFirst()
                .orElseThrow(() -> new BizException("未知的做题模式：" + code));
    }

    /** 作答后是否立即判题并公布答案 */
    public boolean judgesImmediately() {
        return this == PRACTICE || this == WRONG || this == FAVORITE || this == PUNCH;
    }
}
