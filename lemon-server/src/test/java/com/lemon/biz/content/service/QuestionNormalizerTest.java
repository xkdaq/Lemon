package com.lemon.biz.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lemon.biz.content.dto.QuestionReqs.OptionItem;
import com.lemon.common.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestionNormalizerTest {

    private static final int SINGLE = 1;
    private static final int MULTIPLE = 2;
    private static final int JUDGE = 3;
    private static final int SUBJECTIVE = 4;

    private static List<OptionItem> abcd() {
        return List.of(new OptionItem("A", "甲"), new OptionItem("B", "乙"),
                new OptionItem("C", "丙"), new OptionItem("D", "丁"));
    }

    // ---------- 单选 ----------

    @Test
    void single_ok_lowercaseAnswer() {
        var r = QuestionNormalizer.normalize(SINGLE, abcd(), " b ", null);
        assertThat(r.answer()).isEqualTo("B");
        assertThat(r.options()).hasSize(4);
    }

    @Test
    void single_rejectsMultipleAnswers() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(SINGLE, abcd(), "AB", null))
                .isInstanceOf(BizException.class).hasMessageContaining("只能有一个答案");
    }

    @Test
    void single_rejectsAnswerNotInOptions() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(SINGLE, abcd(), "E", null))
                .isInstanceOf(BizException.class).hasMessageContaining("不在选项中");
    }

    @Test
    void single_rejectsTooFewOptions() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(SINGLE, List.of(new OptionItem("A", "甲")), "A", null))
                .isInstanceOf(BizException.class).hasMessageContaining("个选项");
    }

    @Test
    void single_rejectsEmptyAnswer() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(SINGLE, abcd(), "", null))
                .isInstanceOf(BizException.class).hasMessageContaining("请填写答案");
    }

    // ---------- 多选 ----------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"DBA|ABD", "a,b,d|ABD", "A、B；D|ABD", "A B B D|ABD", "C|C"})
    void multiple_normalizesAnswer(String raw, String expected) {
        assertThat(QuestionNormalizer.normalize(MULTIPLE, abcd(), raw, null).answer()).isEqualTo(expected);
    }

    @Test
    void multiple_rejectsGarbage() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(MULTIPLE, abcd(), "A1", null))
                .isInstanceOf(BizException.class).hasMessageContaining("答案格式不正确");
    }

    // ---------- 选项规范化 ----------

    @Test
    void options_autoKeyAndSkipBlank() {
        var raw = List.of(new OptionItem(null, " 甲 "), new OptionItem("", "乙"),
                new OptionItem(null, "  "), new OptionItem(null, "丙"));
        var r = QuestionNormalizer.normalize(SINGLE, raw, "C", null);
        assertThat(r.options()).containsExactly(
                new OptionItem("A", "甲"), new OptionItem("B", "乙"), new OptionItem("C", "丙"));
    }

    @Test
    void options_rejectDuplicateKey() {
        var raw = List.of(new OptionItem("A", "甲"), new OptionItem("a", "乙"));
        assertThatThrownBy(() -> QuestionNormalizer.normalize(SINGLE, raw, "A", null))
                .isInstanceOf(BizException.class).hasMessageContaining("重复");
    }

    // ---------- 判断 ----------

    @ParameterizedTest
    @CsvSource({"对,A", "正确,A", "√,A", "t,A", "A,A", "错,B", "错误,B", "×,B", "F,B", "b,B"})
    void judge_defaultOptionsAndAliases(String raw, String expected) {
        var r = QuestionNormalizer.normalize(JUDGE, null, raw, null);
        assertThat(r.answer()).isEqualTo(expected);
        assertThat(r.options()).isEqualTo(QuestionNormalizer.JUDGE_DEFAULT_OPTIONS);
    }

    @Test
    void judge_customOptions() {
        var opts = List.of(new OptionItem("A", "是"), new OptionItem("B", "否"));
        assertThat(QuestionNormalizer.normalize(JUDGE, opts, "B", null).answer()).isEqualTo("B");
    }

    @Test
    void judge_rejectsThreeOptions() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(JUDGE, abcd().subList(0, 3), "A", null))
                .isInstanceOf(BizException.class).hasMessageContaining("2 个选项");
    }

    // ---------- 主观 ----------

    @Test
    void subjective_ok_clearsAnswer() {
        var r = QuestionNormalizer.normalize(SUBJECTIVE, List.of(), "随便", "参考答案……");
        assertThat(r.options()).isEmpty();
        assertThat(r.answer()).isNull();
    }

    @Test
    void subjective_withoutAnalysis_isRecitationCard() {
        // 背诵题：题干就是要背的内容，没有参考答案
        var r = QuestionNormalizer.normalize(SUBJECTIVE, null, null, " ");
        assertThat(r.options()).isEmpty();
        assertThat(r.answer()).isNull();
    }

    @Test
    void subjective_rejectsOptions() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(SUBJECTIVE, abcd(), null, "答案"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能有选项");
    }

    @Test
    void unknownType() {
        assertThatThrownBy(() -> QuestionNormalizer.normalize(9, abcd(), "A", null))
                .isInstanceOf(BizException.class).hasMessageContaining("未知的题型");
    }
}
