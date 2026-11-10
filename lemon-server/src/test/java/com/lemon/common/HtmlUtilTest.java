package com.lemon.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlUtilTest {

    @Test
    void sanitize_removesScriptsAndEventHandlers() {
        String dirty = "<p onclick=\"alert(1)\">题干<script>alert(2)</script></p>"
                + "<img src=\"x\" onerror=\"alert(3)\"><iframe src=\"https://evil\"></iframe>"
                + "<a href=\"javascript:alert(4)\">链接</a><span style=\"position:fixed\">样式</span>";
        String clean = HtmlUtil.sanitize(dirty);
        assertThat(clean).doesNotContain("script", "onclick", "onerror", "iframe", "javascript", "style", "<a");
        assertThat(clean).contains("<p>题干</p>", "链接", "样式");
    }

    @Test
    void sanitize_keepsFormattingAndRelativeImages() {
        String html = "<p><strong>粗</strong><em>斜</em><u>下划线</u><s>删除</s></p><ul><li>一</li></ul>"
                + "<p><img src=\"/uploads/images/2026/11/a.png\"></p><img src=\"https://cdn.example.com/b.jpg\">";
        String clean = HtmlUtil.sanitize(html);
        assertThat(clean).contains("<strong>粗</strong>", "<em>斜</em>", "<u>下划线</u>", "<s>删除</s>", "<li>一</li>",
                "src=\"/uploads/images/2026/11/a.png\"", "src=\"https://cdn.example.com/b.jpg\"");
    }

    @Test
    void sanitize_dropsDataAndJavascriptImageSrc() {
        assertThat(HtmlUtil.sanitize("<img src=\"javascript:alert(1)\"><img src=\"data:image/svg+xml;base64,AAA\">"))
                .doesNotContain("javascript", "data:");
    }

    @Test
    void isBlank() {
        assertThat(HtmlUtil.isBlank(null)).isTrue();
        assertThat(HtmlUtil.isBlank("<p></p>")).isTrue();
        assertThat(HtmlUtil.isBlank("<p><br></p>")).isTrue();
        assertThat(HtmlUtil.isBlank("<p> &nbsp; </p>")).isTrue();
        assertThat(HtmlUtil.isBlank("<p><img src=\"/uploads/a.png\"></p>")).isFalse();
        assertThat(HtmlUtil.isBlank("<p>字</p>")).isFalse();
    }

    @Test
    void textToHtml_escapesAndSplitsLines() {
        assertThat(HtmlUtil.textToHtml("a<b>\n第二行 & 3")).isEqualTo("<p>a&lt;b&gt;</p><p>第二行 &amp; 3</p>");
        assertThat(HtmlUtil.textToHtml("  ")).isNull();
    }

    @Test
    void htmlToText_roundTrip() {
        assertThat(HtmlUtil.htmlToText(HtmlUtil.textToHtml("第一行\n第二行 <x>"))).isEqualTo("第一行\n第二行 <x>");
        assertThat(HtmlUtil.htmlToText("<p>看图<img src=\"/a.png\">回答</p>")).isEqualTo("看图[图片]回答");
        assertThat(HtmlUtil.htmlToText("<p>一<br>二</p>")).isEqualTo("一\n二");
        // 图片不在段落里（编辑器粘贴图片常见）也要保留
        assertThat(HtmlUtil.htmlToText("<p>物质的唯一特性是</p><img src=\"/a.png\"><p></p>")).isEqualTo("物质的唯一特性是[图片]");
        assertThat(HtmlUtil.htmlToText("纯文本没有标签")).isEqualTo("纯文本没有标签");
    }
}
