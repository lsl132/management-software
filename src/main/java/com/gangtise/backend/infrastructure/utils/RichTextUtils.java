package com.gangtise.backend.infrastructure.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

/**
 * 富文本正文的清洗与统计工具。
 *
 * <p>公告正文由前端富文本编辑器（Quill）产出，存的是 HTML。既然是 HTML 就必须防存储型 XSS：
 * 前端渲染前的 DOMPurify 只是第一道防线，**绕过页面直接调接口**照样能塞进 {@code <script>}
 * 或 {@code <img onerror>}，所以落库前还要在这里按白名单清洗一次。
 *
 * <p><b>白名单是三处副本，必须同步</b>：编辑器工具栏（{@code announcements.js} 的
 * {@code modules.toolbar}）、前端 DOMPurify 的 {@code ALLOWED_TAGS}、本类的 {@link #SAFELIST}。
 * 以后想放开新格式（标题、链接等），三处都要改，否则内容会被静默丢掉。
 */
public final class RichTextUtils {

    /** 正文允许的最大纯文字长度（不含排版标记）。 */
    public static final int MAX_TEXT_LENGTH = 20000;

    /**
     * 允许的标签：与「精简」工具栏（加粗 / 斜体 / 下划线 / 有序·无序列表）一一对应。
     *
     * <p>不放行任何属性——排版本就不需要属性，顺带挡掉 {@code onerror}、{@code style}、
     * {@code href="javascript:"} 这类载荷。
     */
    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "ol", "ul", "li");

    /**
     * 输出关闭 prettyPrint：jsoup 默认会格式化输出（给块级标签补换行与缩进），
     * 那些空白会原样存进库，也会让「正文是否为空」之类的比较失真。
     */
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    private RichTextUtils() {
    }

    /**
     * 按白名单清洗 HTML：只保留 {@link #SAFELIST} 中的标签，其余标签与所有属性一律剥掉。
     *
     * @param html 原始 HTML，可为 null
     * @return 清洗后的 HTML；无有效内容时返回空串
     */
    public static String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return Jsoup.clean(html, "", SAFELIST, OUTPUT_SETTINGS).trim();
    }

    /**
     * 取纯文字内容，用于字数统计与判空（标签被丢弃、HTML 实体被还原）。
     *
     * @param html HTML 或纯文本，可为 null
     * @return 纯文字，已 trim
     */
    public static String toPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

}
