package com.lemon.biz.content.enums;

import com.lemon.common.BizException;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题库类型
 */
@Getter
@AllArgsConstructor
public enum BankType {

    /** 科目型：题库 → 科目 → 章节 → 题目 */
    COURSE(1, "科目型"),
    /** 试卷型：题库 → 章节（每章一套卷）→ 题目 */
    PAPER(2, "试卷型");

    private final int code;
    private final String label;

    public static BankType of(Integer code) {
        return Arrays.stream(values()).filter(t -> code != null && t.code == code).findFirst()
                .orElseThrow(() -> new BizException("未知的题库类型：" + code));
    }
}
