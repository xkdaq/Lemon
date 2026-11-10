package com.lemon.biz.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lemon.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnswerJudgeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"b,a|AB", "ABA|AB", " d c |CD", "''|''", "1x|X"})
    void normalize(String raw, String expected) {
        assertThat(AnswerJudge.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void single() {
        assertThat(AnswerJudge.judge(1, "B", "b")).isTrue();
        assertThat(AnswerJudge.judge(1, "B", "A")).isFalse();
        assertThatThrownBy(() -> AnswerJudge.judge(1, "B", "AB")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AnswerJudge.judge(1, "B", "")).hasMessageContaining("请选择答案");
    }

    @Test
    void multiple_mustMatchExactly() {
        assertThat(AnswerJudge.judge(2, "ABD", "DBA")).isTrue();
        assertThat(AnswerJudge.judge(2, "ABD", "AB")).isFalse();
        assertThat(AnswerJudge.judge(2, "ABD", "ABCD")).isFalse();
    }

    @Test
    void judgeType() {
        assertThat(AnswerJudge.judge(3, "A", "A")).isTrue();
        assertThat(AnswerJudge.judge(3, "A", "B")).isFalse();
    }

    @Test
    void subjective_selfAssessment() {
        assertThat(AnswerJudge.judge(4, null, "1")).isTrue();
        assertThat(AnswerJudge.judge(4, null, "0")).isFalse();
        assertThatThrownBy(() -> AnswerJudge.judge(4, null, "A")).hasMessageContaining("会");
    }
}
