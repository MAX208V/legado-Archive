package io.legado.app.ui.dict

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * 词典显示模式
 */
enum class DictDisplayMode { AUTO, MD, HTML }

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
    private var displayMode = DictDisplayMode.AUTO
    private var lastDictRule: DictRule? = null
    private var lastContent: String? = null

    private val markedJs by lazy { loadAssetText("web/help/js/marked.min.js") }
    private val markdownCss by lazy { loadAssetText("web/help/css/github-markdown-light.min.css") }

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
                DictDisplayMode.AUTO -> DictDisplayMode.MD
                DictDisplayMode.MD -> DictDisplayMode.HTML
                DictDisplayMode.HTML -> DictDisplayMode.AUTO
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
            DictDisplayMode.AUTO -> getString(R.string.dict_mode_auto)
            DictDisplayMode.MD -> "MD"
            DictDisplayMode.HTML -> "HTML"
        }
    }

    /**
     * 读取某字典记忆的显示模式
     */
    private fun loadDictMode(rule: DictRule): DictDisplayMode {
        return when (requireContext().getPrefString("dictMode_${rule.name}", "AUTO")) {
            "MD" -> DictDisplayMode.MD
            "HTML" -> DictDisplayMode.HTML
            else -> DictDisplayMode.AUTO
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
        var mark: String? = null
        when (displayMode) {
            DictDisplayMode.MD -> {
                mark = extractMarkdownContent(content.trimStart())
            }

            DictDisplayMode.HTML -> Unit

            DictDisplayMode.AUTO -> {
                val contentTrimS = content.trimStart()
                if (contentTrimS.startsWith("<md>")) {
                    val lastIndex = contentTrimS.lastIndexOf("<")
                    if (lastIndex < 4) {
                        // 异常 <md> 包裹，按原始内容 HTML 渲染
                        renderHtml(contentTrimS)
                        return
                    }
                    mark = contentTrimS.substring(4, lastIndex)
                }
            }
        }
        if (mark != null) {
            renderMarkdown(mark)
            return
        }
        renderHtml(content)
    }

    /**
     * MD 模式：marked.js 标准 markdown 渲染（WebView，含表格/代码高亮）
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderMarkdown(mark: String) {
        val dictRule = lastDictRule ?: return
        binding.wvDict.visible()
        val webView = binding.wvDict
        webView.stopLoading()
        initWebView(webView, dictRule)
        val isNight = AppConfig.isNightTheme
        val bgColor = if (isNight) "#1F1F1F" else "#FFFFFF"
        val textColor = if (isNight) "#DDDDDD" else "#333333"
        val linkColor = if (isNight) "#8AB4F8" else "#1A73E8"
        webView.setBackgroundColor(if (isNight) 0xFF1F1F1F.toInt() else 0xFFFFFFFF.toInt())
        val markdownJson = JSONArray().put(mark).toString()
        val html = buildString {
            append("<!DOCTYPE html><html><head>")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>")
            append("<style>$markdownCss</style>")
            append("<style>")
            append("html,body{margin:0;padding:12px;background:$bgColor;}")
            append("body{color:$textColor;}")
            append(".markdown-body{color:$textColor;background:transparent;}")
            append(".markdown-body img{max-width:100%;height:auto;}")
            append(".markdown-body a{color:$linkColor;}")
            append("pre,code{background:${if (isNight) "#2D2D2D" else "#F6F8FA"};}")
            append("</style></head><body class=\"markdown-body\">")
            append("<div id=\"md\"></div>")
            append("<script>$markedJs</script>")
            append("<script>")
            append("document.getElementById('md').innerHTML=marked.parse($markdownJson);")
            append("var imgs=document.getElementsByTagName('img');")
            append("for(var i=0;i<imgs.length;i++){(function(im){")
            append("im.addEventListener('click',function(){Android.onImageClick(im.src);});")
            append("})(imgs[i]);}")
            append("</script></body></html>")
        }
        webView.webViewClient = WebViewClient()
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /**
     * HTML 模式：WebView 渲染，按钮/图片通过 JS 桥回调字典规则
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderHtml(content: String) {
        val dictRule = lastDictRule ?: return
        binding.wvDict.visible()
        val webView = binding.wvDict
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
     * WebView 基础配置 + JS 桥
     */
    private fun initWebView(webView: WebView, dictRule: DictRule) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.textZoom = 100
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

    /**
     * 提取 <md> 包裹内的 markdown 内容
     */
    private fun extractMarkdownContent(contentTrimS: String): String? {
        if (!contentTrimS.startsWith("<md>")) return contentTrimS
        val lastIndex = contentTrimS.lastIndexOf("<")
        if (lastIndex < 4) return contentTrimS
        return contentTrimS.substring(4, lastIndex)
    }

    /**
     * 读取 assets 内文本资源
     */
    private fun loadAssetText(path: String): String {
        return try {
            requireContext().assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
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
            setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
            setTextColor(
                when {
                    selected -> accentColor
                    else -> secondaryTextColor
                }
            )
            typeface = uiTypeface
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
