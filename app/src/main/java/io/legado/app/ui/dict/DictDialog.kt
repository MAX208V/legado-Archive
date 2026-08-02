package io.legado.app.ui.dict

import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
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
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
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
        binding.tvDict.movementMethod = LinkMovementMethod()
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
            upDictModeText()
            lastContent?.let { renderDictContent(it) }
        }
        upDictModeText()
        viewModel.initData {
            it.forEach { d  ->
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
     * 按当前显示模式渲染词典内容
     */
    private fun renderDictContent(content: String) {
        binding.rotateLoading.inVisible()
        val dictRule = lastDictRule ?: return
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
                        binding.tvDict.text = contentTrimS
                        return
                    }
                    mark = contentTrimS.substring(4, lastIndex)
                }
            }
        }
        if (mark != null) {
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
            imgOnClickListener = { click  ->
                viewModel.onButtonClick(dictRule, "image", click)
            }
        )
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

    private fun createDictTabView(name: String, selected: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = name
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            isSelected = selected
            setTextColor(if (selected) accentColor else secondaryTextColor)
            textSize = 14f
            typeface = requireContext().uiTypeface()
            setPadding(14.dpToPx(), 8.dpToPx(), 14.dpToPx(), 8.dpToPx())
            background = UiCorner.actionSelector(
                android.graphics.Color.TRANSPARENT,
                backgroundColor,
                UiCorner.actionRadius(requireContext())
            )
        }
    }

    private fun updateDictTabs() {
        for (index in 0 until binding.tabLayout.tabCount) {
            val tab = binding.tabLayout.getTabAt(index) ?: continue
            val selected = tab.isSelected
            (tab.customView as? TextView)?.run {
                isSelected = selected
                setTextColor(if (selected) accentColor else secondaryTextColor)
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

    override fun onDestroyView() {
        super.onDestroyView()
        if (initGetter) {
            glideImageGetter.clear()
        }
    }
}
