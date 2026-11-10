package com.lemon.biz.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionExcelServiceTest {

    private static List<String> oneGroup(int n) {
        return Collections.nCopies(n, "");
    }

    @Test
    void numberedFile_stripsSequentialNumbers() {
        List<String> stems = List.of("1.2026年7月，在韩国釜山", "2、下列说法正确的是", "3． 全角点", " 4 . 带空格");
        List<Integer> nums = QuestionExcelService.sequentialNumbers(stems, oneGroup(4));
        assertThat(nums).containsExactly(1, 2, 3, 4);
        assertThat(QuestionExcelService.stripNumber(stems.get(0), 1)).isEqualTo("2026年7月，在韩国釜山");
        assertThat(QuestionExcelService.stripNumber(stems.get(1), 2)).isEqualTo("下列说法正确的是");
        assertThat(QuestionExcelService.stripNumber(stems.get(2), 3)).isEqualTo("全角点");
        assertThat(QuestionExcelService.stripNumber(stems.get(3), 4)).isEqualTo("带空格");
    }

    @Test
    void materialSubQuestions_doNotBreakNumbering() {
        // 33 是材料，后面两道小题不带题号，34 继续
        List<String> stems = Arrays.asList("31.论述", "32.论述", "33.<p>阅读材料</p>", "<p>评析(5分)</p>", "<p>分析(5分)</p>", "34.阅读材料");
        List<String> groups = oneGroup(6);
        List<Integer> nums = QuestionExcelService.sequentialNumbers(
                java.util.stream.Stream.concat(
                        java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(i -> i + ".单选"), stems.stream()).toList(),
                oneGroup(36));
        assertThat(nums.subList(30, 36)).containsExactly(31, 32, 33, null, null, 34);
        assertThat(QuestionExcelService.stripNumber("33.<p>阅读材料</p>", 33)).isEqualTo("<p>阅读材料</p>");
        assertThat(groups).hasSize(6);
    }

    @Test
    void numberingRestartsPerPaper() {
        List<String> stems = List.of("1.甲", "2.乙", "1.丙", "2.丁");
        List<String> groups = List.of("/第1套", "/第1套", "/第2套", "/第2套");
        assertThat(QuestionExcelService.sequentialNumbers(stems, groups)).containsExactly(1, 2, 1, 2);
    }

    @Test
    void numberInsideLeadingTag() {
        assertThat(QuestionExcelService.stripNumber("<p>12.题干</p>", 12)).isEqualTo("<p>题干</p>");
    }

    @Test
    void keepsNumberThatIsPartOfContent() {
        assertThat(QuestionExcelService.stripNumber("1.1.5倍的增长说明", 1)).isEqualTo("1.5倍的增长说明");
        assertThat(QuestionExcelService.stripNumber("7.不是第 2 题的题号", 2)).isEqualTo("7.不是第 2 题的题号");
        assertThat(QuestionExcelService.stripNumber("1.5倍", null)).isEqualTo("1.5倍");
    }

    @Test
    void unnumberedFile_notDetected() {
        List<String> stems = List.of("1.5倍的增长说明", "2026年7月，没有题号", "马克思指出：1.生产", "下列说法正确的是");
        assertThat(QuestionExcelService.sequentialNumbers(stems, oneGroup(4))).containsOnlyNulls();
    }

    @Test
    void toHtml_keepsHtmlAndConvertsText() {
        assertThat(QuestionExcelService.toHtml("<p>已有<strong>格式</strong></p>")).isEqualTo("<p>已有<strong>格式</strong></p>");
        assertThat(QuestionExcelService.toHtml("a<b\n第二行")).isEqualTo("<p>a&lt;b</p><p>第二行</p>");
        assertThat(QuestionExcelService.toHtml("  ")).isNull();
    }
}
