package io.legado.app.ui.dict

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.invisible
import io.legado.app.utils.putPrefString
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
 * 词典显示模式
 */
enum class DictDisplayMode { HTML, RAW }

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
    private var displayMode = DictDisplayMode.HTML
    private var lastDictRule: DictRule? = null
    private var lastContent: String? = null
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
                // 记忆该字典自身的显示模式
                displayMode = loadDictMode(dictRule)
                upDictModeText()
                binding.rotateLoading.visible()
                viewModel.dict(dictRule, word!!) { content ->
                    lastDictRule = dictRule
                    lastContent = content
                    renderDictContent(content)
                }
            }
        })
        binding.tvDictMode.setOnClickListener {
            displayMode = when (displayMode) {
                DictDisplayMode.HTML -> DictDisplayMode.RAW
                DictDisplayMode.RAW -> DictDisplayMode.HTML
            }
            lastDictRule?.let { saveDictMode(it, displayMode) }
            upDictModeText()
            lastContent?.let { renderDictContent(it) }
        }
        upDictModeText()
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

    private fun upDictModeText() {
        binding.tvDictMode.text = when (displayMode) {
            DictDisplayMode.HTML -> "HTML"
            DictDisplayMode.RAW -> getString(R.string.dict_mode_raw)
        }
    }

    /**
     * 读取某字典记忆的显示模式
     */
    private fun loadDictMode(rule: DictRule): DictDisplayMode {
        return when (requireContext().getPrefString("dictMode_${rule.name}", "HTML")) {
            "RAW", "AUTO", "MD" -> DictDisplayMode.RAW
            else -> DictDisplayMode.HTML
        }
    }

    /**
     * 记忆某字典的显示模式
     */
    private fun saveDictMode(rule: DictRule, mode: DictDisplayMode) {
        requireContext().putPrefString("dictMode_${rule.name}", mode.name)
    }

    /**
     * 按当前显示模式渲染词典内容
     */
    private fun renderDictContent(content: String) {
        binding.rotateLoading.inVisible()
        when (displayMode) {
            DictDisplayMode.HTML -> renderHtml(content)
            DictDisplayMode.RAW -> renderRaw(content)
        }
    }

    /**
     * 原始模式：与原版阅读一致，ScrollTextView + Markwon 渲染。
     * <md> 前缀 → markdown 渲染；否则 → HTML setHtml 渲染（图片/按钮回调）
     */
    private fun renderRaw(content: String) {
        val dictRule = lastDictRule ?: return
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
     * HTML 模式：浏览器级别 WebView 渲染，按钮/图片通过 JS 桥回调字典规则
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderHtml(content: String) {
        val dictRule = lastDictRule ?: return
        binding.tvDict?.invisible()
        binding.wvDict?.visible()
        val webView = binding.wvDict ?: return
        webView.stopLoading()
        initWebView(webView, dictRule)
        val isNight = AppConfig.isNightTheme
        val bgColor = if (isNight) "#1F1F1F" else "#FFFFFF"
        val textColor = if (isNight) "#DDDDDD" else "#333333"
        val linkColor = if (isNight) "#8AB4F8" else "#1A73E8"
        webView.setBackgroundColor(if (isNight) 0xFF1F1F1F.toInt() else 0xFFFFFFFF.toInt())
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>")
            append("<style>")
            append("html,body{margin:0;padding:12px;background:$bgColor;color:$textColor;")
            append("font-size:16px;line-height:1.7;word-break:break-word;}")
            append("img{max-width:100%;height:auto;}")
            append("a{color:$linkColor;}")
            append("button{padding:6px 14px;margin:4px 2px;border:none;border-radius:8px;")
            append("background:#2E7CF6;color:#FFFFFF;font-size:14px;}")
            append("</style></head><body>")
            append(content)
            append("</body></html>")
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    "(function(){" +
                        "var btns=document.getElementsByTagName('button');" +
                        "for(var i=0;i<btns.length;i++){(function(b){" +
                        "var t=b.textContent||'';" +
                        "var idx=t.indexOf('@onclick:');" +
                        "var name=idx>=0?t.substring(0,idx):t;" +
                        "var click=idx>=0?t.substring(idx+9):'';" +
                        "b.textContent=name;" +
                        "b.addEventListener('click',function(e){e.preventDefault();" +
                        "Android.onButtonClick(name,click);});" +
                        "})(btns[i]);" +
                        "}" +
                        "var imgs=document.getElementsByTagName('img');" +
                        "for(var i=0;i<imgs.length;i++){(function(im){" +
                        "im.addEventListener('click',function(){Android.onImageClick(im.src);});" +
                        "})(imgs[i]);}" +
                        "})();",
                    null
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /**
     * WebView 基础配置（浏览器级别）+ JS 桥
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(webView: WebView, dictRule: DictRule) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.textZoom = 100
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.setSupportMultipleWindows(false)
        webView.webChromeClient = WebChromeClient()
        webView.removeJavascriptInterface("Android")
        webView.addJavascriptInterface(DictJsBridge(dictRule), "Android")
    }

    /**
     * WebView JS 回调桥
     */
    private inner class DictJsBridge(private val dictRule: DictRule) {
        @JavascriptInterface
        fun onButtonClick(name: String, click: String) {
            viewModel.onButtonClick(dictRule, "button $name", click)
        }

        @JavascriptInterface
        fun onImageClick(src: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                showDialogFragment(PhotoDialog(src))
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
            gravity = Gravity.CENTER
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
