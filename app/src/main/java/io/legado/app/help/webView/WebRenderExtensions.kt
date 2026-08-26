package io.legado.app.help.webView

import android.annotation.SuppressLint
import android.webkit.WebView
import io.legado.app.constant.AppLog
import org.json.JSONObject

/**
 * 统一管理 WebView 渲染逻辑的扩展函数。
 *
 * 正文长按条「搜索」与字典规则「HTML 模式」原本各自实现了一套
 * 往渲染后的网页注入 CSS / 执行 JS 的逻辑，这里把它们收敛为同一套实现，
 * 保证两者使用完全相同的 html 渲染逻辑。
 *
 * 约定的注入内容语法（字典规则 showRule 复用同一字段承载）：
 * - 以 <style ...> 开头 → 整段内部 CSS 注入为 <style>
 * - 以 <script ...> 开头 → 整段作为 script 执行
 * - 否则：看起来像 JS（function/return/赋值/调用等）则执行，否则当作纯 CSS 文本注入
 */
object WebRenderExtensions {

    /**
     * 注入一段内容（CSS 或 JS）到当前 WebView，供正文搜索与字典 HTML 模式共用。
     */
    @JvmStatic
    fun WebView.injectRenderContent(content: String?) {
        if (content.isNullOrBlank()) return
        val trimmed = content.trim()
        when {
            // <style> 包裹：提取内部 CSS 文本注入
            trimmed.startsWith("<style", ignoreCase = true) -> {
                val inner = trimmed
                    .removePrefix("<style")
                    .dropWhile { it != '>' }
                    .removePrefix(">")
                    .replace("</style>", "", ignoreCase = true)
                    .trim()
                if (inner.isNotBlank()) injectStyle(inner)
            }
            // <script> 包裹：当作 JS 执行
            trimmed.startsWith("<script", ignoreCase = true) -> {
                val inner = trimmed
                    .removePrefix("<script")
                    .dropWhile { it != '>' }
                    .removePrefix(">")
                    .replace("</script>", "", ignoreCase = true)
                    .trim()
                injectScript(inner)
            }
            // 看起来像 JS → 执行
            looksLikeJs(trimmed) -> injectScript(trimmed)
            // 纯 CSS 文本 → 注入
            else -> injectStyle(trimmed)
        }
    }

    /** 注入 CSS：写入/更新页面 <head> 中的固定 id <style> 节点 */
    @JvmStatic
    fun WebView.injectStyle(css: String) {
        if (css.isBlank()) return
        val safe = css.replace("</style", "<\\/style")
        val js = """
            (function() {
                try {
                    var css = ${JSONObject.quote(safe)};
                    var id = 'legado-render-injected-style';
                    var style = document.getElementById(id);
                    if (!style) {
                        style = document.createElement('style');
                        style.id = id;
                        (document.head || document.documentElement).appendChild(style);
                    }
                    style.textContent = css;
                } catch (e) {}
            })();
        """.trimIndent()
        evaluateJavascript(js, null)
    }

    /** 执行一段 JS 内容 */
    @JvmStatic
    fun WebView.injectScript(js: String) {
        if (js.isBlank()) return
        evaluateJavascript(js) { result ->
            if (result == null) {
                AppLog.putDebug("WebRender injectScript evaluateJavascript returned null")
            }
        }
    }

    /**
     * 在 HTML 文档里直接注入 CSS（用于拦截主框架响应时避免首帧闪烁）。
     * 与正文长按搜索原实现行为一致。
     */
    @JvmStatic
    fun injectCssIntoHtml(html: String, css: String): String {
        val style = """<style id="legado-render-injected-style">${
            css.replace("</style", "<\\/style")
        }</style>"""
        val headOpen = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        headOpen.find(html)?.let { match ->
            return html.replaceRange(match.range, "${match.value}$style")
        }
        val htmlOpen = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        htmlOpen.find(html)?.let { match ->
            return html.replaceRange(match.range, "${match.value}<head>$style</head>")
        }
        return "$style$html"
    }

    /**
     * 浏览器级别 WebView 配置（与字典 HTML 模式原来的 initWebView 等价）。
     * 正文搜索与字典 HTML 模式共用同一套设置，确保渲染表现一致。
     */
    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun WebView.applyBrowserSettings() {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = 100
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
    }

    /**
     * 拦截主框架请求，把 CSS 直接注入到 HTML 文档（避免首帧闪烁）。
     * 与正文长按条「搜索」原实现行为完全一致。
     *
     * 兼容处理：showRule 可能以 <style> 包裹或纯 CSS 文本形式给出；
     * 仅当内容能解析为 CSS（非 JS）时才拦截注入，JS 类型交给 onPageFinished 处理。
     * 返回包装后的 WebResourceResponse，无法处理时返回 null 让 WebView 正常加载。
     */
    @JvmStatic
    fun injectCssIntoHtmlResponse(
        view: WebView?,
        request: android.webkit.WebResourceRequest,
        renderContent: String?
    ): WebResourceResponse? {
        val css = toCss(renderContent) ?: return null
        if (css.isBlank()) return null
        val url = request.url.toString()
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", view?.settings?.userAgentString ?: "")
            conn.instanceFollowRedirects = true
            conn.connect()
            val rawBytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            val charset = conn.contentType?.substringAfter("charset=", "utf-8")
                ?.substringBefore(";")?.trim() ?: "utf-8"
            val rawHtml = rawBytes.toString(Charsets.UTF_8)
            val modified = injectCssIntoHtml(rawHtml, css)
            val bytes = modified.toByteArray(charset(charset))
            val mime = when {
                url.endsWith(".html", true) || url.endsWith(".htm", true) -> "text/html"
                else -> "text/html"
            }
            @Suppress("DEPRECATION")
            WebResourceResponse(mime, charset, java.io.ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            // 任何读取失败都回退到 WebView 默认加载，不要阻断页面
            null
        }
    }

    private fun looksLikeJs(text: String): Boolean {
        if (text.startsWith("function") || text.startsWith("var ") || text.startsWith("let ")
            || text.startsWith("const ") || text.startsWith("window.")
            || text.startsWith("document.") || text.startsWith("!") || text.startsWith("(")
        ) {
            return true
        }
        if (text.contains("function") || text.contains("=>") || text.contains("return")) return true
        if (text.contains("=") && !text.contains("{")) return true
        if (text.contains(";") && !text.contains(":")) return true
        return false
    }
}
