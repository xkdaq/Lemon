package com.lemon.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

/**
 * 富文本处理。题干、解析存 HTML，保存前统一白名单过滤，防止存储型 XSS（用户端会直接渲染这些 HTML）。
 */
public final class HtmlUtil {

    /** 允许常见排版标签和图片；不允许 style / script / 事件属性 / iframe 等 */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("s", "u", "mark", "hr")
            .removeTags("a")
            .preserveRelativeLinks(true);

    /** 解析相对链接用的占位 base，只为让 jsoup 认得 /uploads/xxx 这类相对地址 */
    private static final String BASE_URI = "http://localhost";

    private HtmlUtil() {
    }

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        Document.OutputSettings out = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, BASE_URI, SAFELIST, out).trim();
    }

    /** 没有文字也没有图片视为空，如 "<p></p>"、"<p><br></p>" */
    public static boolean isBlank(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        return doc.text().isBlank() && doc.select("img").isEmpty();
    }

    /** 纯文本转 HTML（Excel 导入用）：转义特殊字符，按换行分段 */
    public static String textToHtml(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.strip().split("\\r?\\n")) {
            sb.append("<p>").append(Entities.escape(line)).append("</p>");
        }
        return sb.toString();
    }

    /** HTML 转纯文本（Excel 导出用）：段落和换行保留为换行，图片标记为 [图片] */
    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element img : doc.select("img")) {
            img.replaceWith(new org.jsoup.nodes.TextNode("[图片]"));
        }
        // wholeText() 会把 <br> 输出为换行，无需额外处理
        // 逐个顶层节点取文本：块级元素各占一行；直接挂在 body 下的文本（如替换后的 [图片]）接在当前行
        StringBuilder sb = new StringBuilder();
        for (org.jsoup.nodes.Node node : doc.body().childNodes()) {
            String text = node instanceof Element el ? el.wholeText().strip()
                    : node instanceof org.jsoup.nodes.TextNode tn ? tn.getWholeText().strip() : "";
            if (text.isEmpty()) {
                continue;
            }
            boolean block = node instanceof Element el && el.isBlock();
            if (!sb.isEmpty() && block) {
                sb.append('\n');
            }
            sb.append(text);
        }
        return sb.toString();
    }
}
