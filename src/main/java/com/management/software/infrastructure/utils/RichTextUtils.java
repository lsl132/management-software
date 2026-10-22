package com.management.software.infrastructure.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 富文本正文的清洗与统计工具。
 *
 * <p>公告正文由前端富文本编辑器（Quill「完整经典」工具栏）产出，存的是 HTML。既然是 HTML 就必须防存储型
 * XSS：前端渲染前的 DOMPurify 只是第一道防线，**绕过页面直接调接口**照样能塞进 {@code <script>}
 * 或 {@code <img onerror>}，所以落库前还要在这里按白名单清洗一次。
 *
 * <p><b>白名单是三处副本，必须同步</b>：编辑器工具栏（{@code announcements.js} 的
 * {@code modules.toolbar} 与 {@code formats}）、前端 DOMPurify 的 {@code RICH_ALLOWED_TAGS}
 * （{@code app.js}）、本类的 {@link #SAFELIST}。以后想放开新格式，三处都要改，否则内容会被静默丢掉。
 *
 * <p><b>属性不是整包放行，而是「先放行、再按值过滤」</b>：颜色/对齐/缩进这些格式天然要落在
 * {@code style} / {@code class} 上，而 {@code style} 是 XSS 的经典载体（{@code expression()}、
 * {@code url(javascript:)}、{@code behavior}）。因此这里只把属性放行到清洗阶段，
 * 随后在 {@link #filterStyle} / {@link #filterClass} 里**逐条声明**校验，只留白名单内的属性与取值。
 */
public final class RichTextUtils {

    /** 正文允许的最大纯文字长度（不含排版标记）。 */
    public static final int MAX_TEXT_LENGTH = 20000;

    /**
     * 允许的标签：与编辑器「完整经典」工具栏一一对应。
     *
     * <p>标题 h1~h3、粗斜下删、上下标、有序无序列表、引用、代码块、链接、颜色（span）。
     * <b>不含 img / iframe / video</b>：本轮明确不支持在正文里插媒体，编辑器侧也拦了拖拽与粘贴。
     */
    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "sub", "sup",
                    "h1", "h2", "h3", "ol", "ul", "li", "blockquote", "pre", "code", "a", "span")
            // 链接：协议与编辑器侧 Quill 的 PROTOCOL_WHITELIST 对齐，否则编辑器能产出的链接会被服务端悄悄剥成纯文字
            .addAttributes("a", "href", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto", "tel", "sms")
            // 颜色落在 span 的 style 上；具体声明由 filterStyle 逐条校验
            .addAttributes("span", "style");

    /** 对齐/缩进落在块级元素的 class 上；取值由 {@link #filterClass} 逐条校验。 */
    private static final String[] CLASS_TAGS =
            {"p", "h1", "h2", "h3", "li", "ol", "ul", "blockquote", "pre", "code", "span"};

    static {
        for (String tag : CLASS_TAGS) {
            SAFELIST.addAttributes(tag, "class");
        }
    }

    /**
     * 输出关闭 prettyPrint：jsoup 默认会格式化输出（给块级标签补换行与缩进），
     * 那些空白会原样存进库，也会让「正文是否为空」之类的比较失真。
     */
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    /**
     * style 里允许出现的声明。
     *
     * <p>{@code text-align} 是给「对齐」兜底的：Quill 2 的对齐有 class 与 style 两种实现，
     * 具体注册哪一个取决于构建产物，两边都认才不会因为升级 Quill 就丢格式。
     */
    private static final Set<String> ALLOWED_STYLE_PROPERTIES = Set.of("color", "background-color", "text-align");

    /** 颜色值只接受十六进制或 rgb()/rgba()：Quill 取色板产出的就是这两种形态。 */
    private static final Pattern SAFE_COLOR = Pattern.compile(
            "^(#[0-9a-fA-F]{3,8}|rgba?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*(?:,\\s*(?:0|1|0?\\.\\d{1,3}))?\\s*\\))$");

    private static final Set<String> ALLOWED_TEXT_ALIGN = Set.of("left", "center", "right", "justify");

    /**
     * class 只接受编辑器的排版类名。
     *
     * <p>{@code ql-align-*} / {@code ql-indent-*} 是 Quill 的对齐与缩进；
     * {@code ql-syntax} 是代码块在高亮开启时的类名，当前没有 syntax 模块，留着防升级后丢样式。
     *
     * <p><b>为什么必须按值过滤而不是整包放行 class</b>：class 能命中本站已有的样式，
     * 放行任意值等于把「页面长什么样」交给正文作者——轻则藏掉元素（{@code hidden}），
     * 重则把正文伪装成按钮诱导点击。
     */
    private static final Pattern SAFE_CLASS =
            Pattern.compile("^ql-(?:align-(?:center|right|justify)|indent-[1-8]|syntax)$");

    private RichTextUtils() {
    }

    /**
     * 按白名单清洗 HTML：只保留 {@link #SAFELIST} 中的标签，属性再按值过滤一遍。
     *
     * @param html 原始 HTML，可为 null
     * @return 清洗后的 HTML；无有效内容时返回空串
     */
    public static String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String cleaned = Jsoup.clean(html, "", SAFELIST, OUTPUT_SETTINGS).trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(OUTPUT_SETTINGS.clone());
        normalizeAttributes(document);
        return document.body().html().trim();
    }

    /**
     * 二次过滤属性取值。
     *
     * <p>jsoup 的 Safelist 只能表达「哪个标签允许哪个属性」，表达不了「属性值长什么样」，
     * 所以 style / class 的取值校验必须在这里补上；链接则补 {@code rel}，防反向标签劫持。
     */
    private static void normalizeAttributes(Document document) {
        for (Element element : document.select("[style]")) {
            String style = filterStyle(element.attr("style"));
            if (style.isEmpty()) {
                element.removeAttr("style");
            } else {
                element.attr("style", style);
            }
        }
        for (Element element : document.select("[class]")) {
            String className = filterClass(element.attr("class"));
            if (className.isEmpty()) {
                element.removeAttr("class");
            } else {
                element.attr("class", className);
            }
        }
        for (Element link : document.select("a")) {
            // 新窗口打开必须带 rel，否则目标页能通过 window.opener 反向操纵本页
            if ("_blank".equalsIgnoreCase(link.attr("target"))) {
                link.attr("target", "_blank");
                link.attr("rel", "noopener noreferrer");
            } else {
                link.removeAttr("target");
                link.removeAttr("rel");
            }
        }
    }

    /**
     * 只保留白名单内的 CSS 声明，其余整条丢弃（不是把值洗一洗——脏声明留着没有意义）。
     *
     * @param style 原始 style 属性值
     * @return 过滤后的 style；全部被丢弃时返回空串
     */
    static String filterStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).trim();
            if (!ALLOWED_STYLE_PROPERTIES.contains(property) || value.isEmpty()) {
                continue;
            }
            boolean safe = "text-align".equals(property)
                    ? ALLOWED_TEXT_ALIGN.contains(value.toLowerCase(Locale.ROOT))
                    : SAFE_COLOR.matcher(value).matches();
            if (!safe) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(' ');
            }
            kept.append(property).append(": ").append(value).append(';');
        }
        return kept.toString();
    }

    /**
     * 只保留编辑器的排版类名。
     *
     * @param classAttribute 原始 class 属性值
     * @return 过滤后的 class；全部被丢弃时返回空串
     */
    static String filterClass(String classAttribute) {
        if (!StringUtils.hasText(classAttribute)) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String token : classAttribute.trim().split("\\s+")) {
            if (!SAFE_CLASS.matcher(token).matches()) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(' ');
            }
            kept.append(token);
        }
        return kept.toString();
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
