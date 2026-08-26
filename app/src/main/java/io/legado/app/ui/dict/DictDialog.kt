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

    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)
    private var word: String? = null
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
            updateDictTabs()
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
        binding.tvDict?.invisible()
        binding.wvDict?.visible()
        val webView = binding.wvDict ?: return
        webView.stopLoading()
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
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // 主框架响应：直接把继承自搜索的渲染 CSS 注入到 HTML，避免首帧闪烁
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && request?.isForMainFrame == true
                    ) {
                        WebRenderExtensions.injectCssIntoHtmlResponse(
                            view,
                            request,
                            dictRule.showRule
                        )?.let { return it }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 页面加载完成后再注入一次（兜底；并处理 JS 类型注入）
                    (view as? WebView)?.let { WebRenderExtensions.injectRenderContent(it, dictRule.showRule) }
                }
            }
            webView.loadUrl(url)
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
        binding.wvDict?.removeJavascriptInterface("Android")
        binding.wvDict?.stopLoading()
        binding.wvDict?.removeAllViews()
        binding.wvDict?.destroy()
        super.onDestroyView()
    }
}
