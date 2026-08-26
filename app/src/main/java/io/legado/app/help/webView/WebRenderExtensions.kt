package io.legado.app.help.webView

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.legado.app.constant.AppLog
import io.legado.app.help.http.okHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

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
    fun injectRenderContent(webView: WebView, content: String?) {
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
                if (inner.isNotBlank()) injectStyle(webView, inner)
            }
            // <script> 包裹：当作 JS 执行
            trimmed.startsWith("<script", ignoreCase = true) -> {
                val inner = trimmed
                    .removePrefix("<script")
                    .dropWhile { it != '>' }
                    .removePrefix(">")
                    .replace("</script>", "", ignoreCase = true)
                    .trim()
                injectScript(webView, inner)
            }
            // 看起来像 JS → 执行
            looksLikeJs(trimmed) -> injectScript(webView, trimmed)
            // 纯 CSS 文本 → 注入
            else -> injectStyle(webView, trimmed)
        }
    }

    /** 注入 CSS：写入/更新页面 <head> 中的固定 id <style> 节点 */
    @JvmStatic
    fun injectStyle(webView: WebView, css: String) {
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
        webView.evaluateJavascript(js, null)
    }

    /** 执行一段 JS 内容 */
    @JvmStatic
    fun injectScript(webView: WebView, js: String) {
        if (js.isBlank()) return
        webView.evaluateJavascript(js) { result ->
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
    fun applyBrowserSettings(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.textZoom = 100
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.setSupportMultipleWindows(false)
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
    fun toCss(content: String?): String? {
        if (content.isNullOrBlank()) return null
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("<style", ignoreCase = true) -> {
                val inner = trimmed
                    .removePrefix("<style")
                    .dropWhile { it != '>' }
                    .removePrefix(">")
                    .replace("</style>", "", ignoreCase = true)
                    .trim()
                inner.ifBlank { null }
            }
            trimmed.startsWith("<script", ignoreCase = true) -> null
            looksLikeJs(trimmed) -> null
            else -> trimmed
        }
    }

    /**
     * 拦截主框架 GET 请求，用应用统一的 okHttpClient 重新拉取文档，
     * 并把 CSS / JS 直接注入到 HTML（避免首帧闪烁）。
     *
     * 这是「正文长按搜索」与「字典 HTML 模式」共用的同一套 html 加载渲染逻辑：
     * 两者只是传入的 css / js 内容不同（搜索注入搜索引擎 hideCss，字典注入 showRule/cssRule/jsRule）。
     *
     * @param view   触发拦截的 WebView（用于取 UA）
     * @param request 资源请求
     * @param css    要注入到 <head> 的 CSS（可为 showRule / cssRule 等组合；内部按 injectRenderContent 语法判定）
     * @param js     要注入到 <body> 末尾的 JS（可为 jsRule；为空则不注入 JS）
     */
    @JvmStatic
    fun interceptAndInjectHtml(
        view: WebView?,
        request: WebResourceRequest?,
        css: String?,
        js: String? = null
    ): WebResourceResponse? {
        request ?: return null
        if (!request.isForMainFrame) return null
        if (!request.method.equals("GET", ignoreCase = true)) return null
        val url = request.url?.toString().orEmpty()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        val cssContent = toCss(css)
        val jsContent = js?.takeIf { it.isNotBlank() && looksLikeJs(it.trim()) }
        if (cssContent.isNullOrBlank() && jsContent == null) return null
        return runCatching {
            val response = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                .newCall(buildInterceptRequest(view, request, url))
                .execute()
            response.use { res ->
                if (!res.isSuccessful || res.code == 204 || res.code == 205 || res.code == 304) {
                    return null
                }
                val body = res.body ?: return null
                val contentType = body.contentType()
                val mimeType = contentType?.toString()?.substringBefore(";") ?: "text/html"
                if (!mimeType.contains("html", ignoreCase = true)) return null
                val charset = contentType?.charset() ?: StandardCharsets.UTF_8
                val html = body.string()
                val modified = injectAllIntoHtml(html, cssContent, jsContent)
                val bytes = modified.toByteArray(charset)
                val headers = res.headers.toMultimap()
                    .mapValues { it.value.joinToString(",") }
                    .toMutableMap()
                    .apply {
                        // 注入后内容长度变化，移除长度/编码相关头，避免浏览器按旧长度截断
                        remove("content-length")
                        remove("Content-Length")
                        remove("content-encoding")
                        remove("Content-Encoding")
                        remove("transfer-encoding")
                        remove("Transfer-Encoding")
                    }
                @Suppress("DEPRECATION")
                WebResourceResponse(mimeType, charset.name(), ByteArrayInputStream(bytes)).apply {
                    responseHeaders = headers
                }
            }
        }.getOrNull()
    }

    /** 在 HTML 文档的 <head> 注入 CSS，并在 <body> 末尾注入 JS（与 injectCssIntoHtml 同源）。 */
    @JvmStatic
    fun injectAllIntoHtml(html: String, css: String?, js: String?): String {
        var result = if (css.isNullOrBlank()) html else injectCssIntoHtml(html, css)
        if (!js.isNullOrBlank()) {
            val script = "<script id=\"legado-render-injected-script\">${js.replace("</script", "<\\/script")}</script>"
            val bodyClose = Regex("</body(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
            result = bodyClose.find(result)?.let { match ->
                result.replaceRange(match.range, "$script${match.value}")
            } ?: "$result$script"
        }
        return result
    }

    /**
     * 构造「拦截重请求」：转发原始请求头、附带 Cookie 与应用 UA，
     * 避免丢失登录态（正文搜索与字典 HTML 模式共用）。
     */
    @JvmStatic
    fun buildInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
        url: String
    ): Request {
        val ua = view?.settings?.userAgentString
        return Request.Builder()
            .url(url)
            .apply {
                request.requestHeaders.forEach { (key, value) ->
                    if (!key.equals("accept-encoding", true) &&
                        !key.equals("content-length", true) &&
                        value.isNotBlank()
                    ) {
                        header(key, value)
                    }
                }
                CookieManager.getInstance().getCookie(url)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("Cookie", it) }
                header("Accept-Encoding", "identity")
                ua?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
            }
            .get()
            .build()
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
