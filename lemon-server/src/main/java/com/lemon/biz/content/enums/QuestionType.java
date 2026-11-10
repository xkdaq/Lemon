package com.lemon.biz.content.enums;

import com.lemon.common.BizException;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题型
 */
@Getter
@AllArgsConstructor
public enum QuestionType {

    SINGLE(1, "单选题", true),
    MULTIPLE(2, "多选题", true),
    JUDGE(3, "判断题", true),
    SUBJECTIVE(4, "主观题", false);

    private final int code;
    private final String label;
    /** 是否可自动判题 */
    private final boolean autoJudge;

    public static QuestionType of(Integer code) {
        return Arrays.stream(values()).filter(t -> code != null && t.code == code).findFirst()
                .orElseThrow(() -> new BizException("未知的题型：" + code));
    }
}
