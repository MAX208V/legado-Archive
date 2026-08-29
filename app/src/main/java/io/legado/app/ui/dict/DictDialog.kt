package io.legado.app.ui.dict

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictBinding
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.webView.WebRenderExtensions
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词典
 */
class DictDialog() : BaseDialogFragment(R.layout.dialog_dict) {

    constructor(word: String) : this() {
        arguments = Bundle().apply {
            putString("word", word)
        }
    }

    /**
     * @param word     待查询词
     * @param ruleName 预选的字典规则名（测试某条规则时传入），为空则默认选中排序第一的规则
     */
    constructor(word: String, ruleName: String?) : this() {
        arguments = Bundle().apply {
            putString("word", word)
            putString("ruleName", ruleName)
        }
    }

    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)
    private var word: String? = null
    // 调试用：统计 renderHtml 被调用次数，便于区分首屏(第1次)与手动切换(后续)
    private var renderHtmlCount = 0
    // 当前 HTML 模式使用的池化 WebView（用于 dismiss 时释放回池）
    private var currentPooledWebView: PooledWebView? = null
    private val imgAvailableWidth by lazy {
        val textView = binding.tvDict
        textView.width - textView.paddingLeft - textView.paddingRight
    }
    private var initGetter = false
    private val glideImageGetter by lazy {
        initGetter = true
        GlideImageGetter(
            requireContext(),
            binding.tvDict,
            this@DictDialog.lifecycle,
            imgAvailableWidth
        )
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        word = arguments?.getString("word")
        if (word.isNullOrEmpty()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }
        // 测试某条规则时传入的预选规则名；为空则默认选中排序第一的规则
        val presetRuleName = arguments?.getString("ruleName")
        binding.tabLayout.setBackgroundColor(backgroundColor)
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateDictTabs()
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                updateDictTabs()
                val dictRule = tab.tag as DictRule
                binding.rotateLoading.visible()
                if (dictRule.htmlMode) {
                    // HTML 模式：直接以浏览器方式加载完整网页（CSS/JS 完整显示）
                    renderHtml(dictRule)
                } else {
                    // 原始模式：原版 ScrollTextView + Markwon 渲染
                    viewModel.dict(dictRule, word!!) { content ->
                        binding.rotateLoading.inVisible()
                        renderRaw(dictRule, content)
                    }
                }
            }
        })
        viewModel.initData {
            it.forEach { d ->
                binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
                    customView = createDictTabView(d.name, false)
                    tag = d
                })
            }
            setupTabLayoutMode(it.size)
            // 选中预选规则（测试时传入）；未指定则默认选中排序第一的规则。
            // select() 会触发 onTabSelected，从而加载首屏字典内容（修复排序第一规则不加载的问题）。
            val presetIndex = if (!presetRuleName.isNullOrBlank()) {
                it.indexOfFirst { d -> d.name == presetRuleName }.let { idx -> if (idx < 0) 0 else idx }
            } else {
                0
            }
            val initialTab = binding.tabLayout.getTabAt(presetIndex)
            if (initialTab != null) {
                updateDictTabs()
                binding.tabLayout.selectTab(initialTab)
            } else {
                updateDictTabs()
            }
        }
    }

    /**
     * 原始模式：与原版阅读一致，ScrollTextView + Markwon 渲染。
     * <md> 前缀 → markdown 渲染；否则 → HTML setHtml 渲染（图片/按钮回调）
     */
    private fun renderRaw(dictRule: DictRule, content: String) {
        binding.wvDict?.invisible()
        binding.tvDict?.visible()
        binding.tvDict.movementMethod = LinkMovementMethod()
        val contentTrimS = content.trimStart()
        if (contentTrimS.startsWith("<md>")) {
            val lastIndex = contentTrimS.lastIndexOf("<")
            if (lastIndex < 4) {
                binding.tvDict.text = contentTrimS
                return
            }
            val mark = contentTrimS.substring(4, lastIndex)
            viewLifecycleOwner.lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.tvDict.setTextClassifier(TextClassifier.NO_OP)
                }
                val markwon: Markwon
                val markdown = withContext(IO) {
                    markwon = Markwon.builder(requireContext())
                        .usePlugin(
                            GlideImagesPlugin.create(
                                Glide.with(requireContext())
                                    .applyDefaultRequestOptions(
                                        RequestOptions()
                                            .override(imgAvailableWidth)
                                            .encodeQuality(88)
                                    )
                            )
                        )
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(requireContext()))
                        .build()
                    markwon.toMarkdown(mark)
                }
                binding.tvDict.setMarkdown(
                    markwon,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source))
                    }
                )
            }
            return
        }
        val textViewTagHandler = TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(dictRule, "button $name", click)
            }
        })
        binding.tvDict.setHtml(
            content,
            glideImageGetter,
            textViewTagHandler,
            imgOnLongClickListener = { source ->
                showDialogFragment(PhotoDialog(source))
            },
            imgOnClickListener = { click ->
                viewModel.onButtonClick(dictRule, "image", click)
            }
        )
    }

    /**
     * HTML 模式：直接调用 WebView 加载 urlRule 生成的完整网页（浏览器级别，CSS/JS 正常显示）
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderHtml(dictRule: DictRule) {
        // [DEBUG-dict] 临时调试日志(Log.d, 用 adb logcat -s DictDebug 抓取)
        renderHtmlCount++
        Log.d(
            "DictDebug",
            "[renderHtml #$renderHtmlCount] name=${dictRule.name} " +
                "htmlMode=${dictRule.htmlMode} word=[$word] " +
                "urlRule=[${dictRule.urlRule}]"
        )
        binding.tvDict?.invisible()
        binding.wvDict?.visible()
        val container = binding.wvDict ?: return
        // 复用「正文长按搜索」同一套 WebViewPool 预热实例：百度汉语等重前端 JS 的 SPA
        // 在冷 WebView 上首屏白屏，池化(已 resumeTimers/JS引擎热)实例可正常渲染。
        // 先释放上一次使用的池化实例，再取新的放入容器。
        currentPooledWebView?.let { old ->
            container.removeView(old.realWebView)
            WebViewPool.release(old)
            currentPooledWebView = null
        }
        val pooled = WebViewPool.acquire(requireContext())
        currentPooledWebView = pooled
        val webView = pooled.realWebView
        container.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webView.stopLoading()
        // 与正文长按「搜索」一致：恢复 WebView 的渲染/JS 定时器调度。
        // 否则 Fragment 内 WebView 处于未 resume 状态，百度等重前端 JS 的 SPA
        // 渲染被挂起 -> 白屏且 onPageFinished 不回调。
        webView.resumeTimers()
        webView.onResume()
        // 与正文长按条「搜索」共用同一套浏览器级别 WebView 配置
        WebRenderExtensions.applyBrowserSettings(webView)
        webView.webChromeClient = WebChromeClient()
        binding.rotateLoading.visible()
        viewLifecycleOwner.lifecycleScope.launch {
            val url = withContext(IO) {
                runCatching {
                    val analyzeUrl = AnalyzeUrl(
                        dictRule.urlRule,
                        key = word,
                        coroutineContext = kotlinx.coroutines.currentCoroutineContext()
                    )
                    analyzeUrl.url
                }.getOrNull()
            }
            binding.rotateLoading.inVisible()
            if (url.isNullOrBlank()) {
                binding.wvDict?.invisible()
                binding.tvDict?.visible()
                binding.tvDict.text = "URL 解析失败"
                return@launch
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("DictDebug", "[onPageStarted #$renderHtmlCount] url=[$url]")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        Log.d(
                            "DictDebug",
                            "[onReceivedError #$renderHtmlCount] url=[${request.url}] " +
                                "code=${error?.errorCode} desc=${error?.description}"
                        )
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // 与「正文长按搜索」同一套 html 加载渲染逻辑：拦截主框架响应，
                    // 用统一的 okHttpClient 重请求并把 htmlShowRule/cssRule 注入 CSS、jsRule 注入 JS
                    val css = buildString {
                        if (dictRule.htmlShowRule.isNotBlank()) appendLine(dictRule.htmlShowRule)
                        if (dictRule.cssRule.isNotBlank()) appendLine(dictRule.cssRule)
                    }.takeIf { it.isNotBlank() }
                    WebRenderExtensions.interceptAndInjectHtml(
                        view,
                        request,
                        css,
                        dictRule.jsRule
                    )?.let { return it }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // [DEBUG-dict] 确认首屏是否触发 onPageFinished 及最终 url
                    Log.d(
                        "DictDebug",
                        "[onPageFinished #$renderHtmlCount] url=[$url] " +
                            "contentHeight=${(view as? WebView)?.contentHeight}"
                    )
                    // 页面加载完成后再兜底注入一次（处理 JS 类型注入）
                    (view as? WebView)?.let { WebRenderExtensions.injectRenderContent(it, dictRule.htmlShowRule) }
                }
            }
            // [DEBUG-dict] 打印最终解析出的真实 url，判断 key 是否丢失
            Log.d(
                "DictDebug",
                "[renderHtml #$renderHtmlCount] FINAL url=[$url]"
            )
            // 百度汉语等重前端 JS 的 SPA 站点：WebView 冷启动(首屏)时 JS 引擎未就绪，
            // 直接 loadUrl 会白屏；用户手动切换 tab 时 WebView 已热则正常。
            // 首屏(#1)延迟一小段时间再加载，对齐"二次加载才正常"的时序，规避冷启动白屏。
            if (renderHtmlCount == 1) {
                // 首屏延迟一小段，对齐 WebView 已 attached 的稳定时序
                val target = pooled
                webView.postDelayed({
                    if (currentPooledWebView == target) webView.loadUrl(url)
                }, 300)
            } else {
                webView.loadUrl(url)
            }
        }
    }

    //根据已启用词典数动态选取布局
    private fun setupTabLayoutMode(dictCount: Int) {
        if (dictCount <= 4) {
            binding.tabLayout.tabMode = TabLayout.MODE_FIXED
            binding.tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        } else {
            binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            binding.tabLayout.tabGravity = TabLayout.GRAVITY_CENTER
        }
    }

    private fun createDictTabView(name: String, selected: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = name
            textSize = 13f
            setPadding(4.dpToPx(), 0, 4.dpToPx(), 0)
            setTextColor(
                when {
                    selected -> accentColor
                    else -> secondaryTextColor
                }
            )
            typeface = requireContext().uiTypeface()
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun updateDictTabs() {
        val selectedTab = binding.tabLayout.selectedTabPosition
        for (i in 0 until binding.tabLayout.tabCount) {
            val tab = binding.tabLayout.getTabAt(i) ?: continue
            val view = tab.customView as? TextView ?: continue
            view.setTextColor(if (i == selectedTab) accentColor else secondaryTextColor)
        }
    }

    override fun onDestroyView() {
        // 释放 HTML 模式使用的池化 WebView 回池（不可 destroy 容器本身）
        currentPooledWebView?.let { pooled ->
            binding.wvDict?.removeView(pooled.realWebView)
            WebViewPool.release(pooled)
            currentPooledWebView = null
        }
        binding.wvDict?.removeAllViews()
        super.onDestroyView()
    }
}
