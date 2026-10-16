package com.gangtise.backend.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 富文本清洗单测。
 *
 * <p>重点验证三件事：① 白名单内的排版标签原样保留；② 白名单外的一切（标签、属性）都被剥掉；
 * ③ 纯文本（含换行）不被改写——老公告的正文是纯文本，清洗不能把它们弄坏。
 */
class RichTextUtilsTest {

    /* ---------------------------------------------------------------- 保留 */

    @Test
    @DisplayName("白名单内的排版标签原样保留")
    void keepsAllowedTags() {
        String html = "<p>你好<strong>加粗</strong><em>斜体</em><u>下划线</u></p>"
                + "<ol><li>第一</li></ol><ul><li>第二</li></ul>";
        assertEquals(html, RichTextUtils.sanitize(html));
    }

    @Test
    @DisplayName("纯文本原样返回，不会被包成段落")
    void keepsPlainTextAsIs() {
        assertEquals("就是一段普通正文", RichTextUtils.sanitize("就是一段普通正文"));
    }

    @Test
    @DisplayName("纯文本的换行被保留（老公告正文靠换行分段）")
    void keepsPlainTextLineBreaks() {
        assertEquals("第一行\n第二行", RichTextUtils.sanitize("第一行\n第二行"));
    }

    @Test
    @DisplayName("输出不做 prettyPrint，块级标签之间不会插入多余空白")
    void doesNotPrettyPrint() {
        assertEquals("<p>甲</p><p>乙</p>", RichTextUtils.sanitize("<p>甲</p><p>乙</p>"));
    }

    /* ---------------------------------------------------------------- 剥离 */

    @Test
    @DisplayName("script 标签连同脚本内容一起被剥掉")
    void stripsScriptWithContent() {
        String cleaned = RichTextUtils.sanitize("<p>正文</p><script>alert('xss')</script>");
        assertEquals("<p>正文</p>", cleaned);
        assertFalse(cleaned.contains("alert"));
    }

    @Test
    @DisplayName("事件属性（onerror / onclick）被剥掉，标签其余部分保留")
    void stripsEventAttributes() {
        String cleaned = RichTextUtils.sanitize("<p onclick=\"steal()\">文字</p><img src=x onerror=alert(1)>");
        assertEquals("<p>文字</p>", cleaned);
        assertFalse(cleaned.contains("onclick"));
        assertFalse(cleaned.contains("onerror"));
    }

    @Test
    @DisplayName("class / style / id 等属性一律剥掉")
    void stripsClassAndStyle() {
        assertEquals("<p>文字</p>",
                RichTextUtils.sanitize("<p class=\"ql-align-center\" style=\"color:red\" id=\"x\">文字</p>"));
    }

    @Test
    @DisplayName("白名单外的标签被剥掉，但里面的文字保留")
    void unwrapsDisallowedTags() {
        assertEquals("标题正文", RichTextUtils.sanitize("<h1>标题</h1><div>正文</div>"));
        assertEquals("点我", RichTextUtils.sanitize("<a href=\"javascript:alert(1)\">点我</a>"));
    }

    @Test
    @DisplayName("只有危险标签时清洗结果为空（调用方据此判空拒绝）")
    void emptyWhenEverythingStripped() {
        assertEquals("", RichTextUtils.sanitize("<script>alert(1)</script>"));
        assertEquals("", RichTextUtils.sanitize("<iframe src=\"//evil\"></iframe>"));
    }

    @Test
    @DisplayName("null / 空白输入返回空串")
    void blankInputReturnsEmpty() {
        assertEquals("", RichTextUtils.sanitize(null));
        assertEquals("", RichTextUtils.sanitize(""));
        assertEquals("", RichTextUtils.sanitize("   "));
    }

    /* ---------------------------------------------------------------- 纯文本 */

    @Test
    @DisplayName("toPlainText 去掉标签、还原实体、把块级边界收成空白")
    void toPlainTextStripsMarkup() {
        assertEquals("甲 乙", RichTextUtils.toPlainText("<p>甲</p><p>乙</p>"));
        assertEquals("a & b", RichTextUtils.toPlainText("<p>a &amp; b</p>"));
        assertEquals("你好世界", RichTextUtils.toPlainText("<p>你好<strong>世界</strong></p>"));
        assertEquals("", RichTextUtils.toPlainText(null));
    }

    @Test
    @DisplayName("清洗后再统计纯文字长度：排版标记不占字数")
    void countsPlainTextLengthWithoutMarkup() {
        String html = "<p><strong>你好世界</strong></p>";
        String cleaned = RichTextUtils.sanitize(html);
        assertTrue(cleaned.length() > 10, "清洗后的 HTML 串本身很长");
        assertEquals(4, RichTextUtils.toPlainText(cleaned).length(), "纯文字只有 4 个字");
    }

}
