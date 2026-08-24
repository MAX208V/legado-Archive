package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogSliderItem
import io.legado.app.ui.widget.compose.AppDialogSliderGrid
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.widget.compose.ComposeDialogFragment
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.widget.compose.showComposeTextInputDialog

/**
 * 图片/视频图层「图片边距」设置对话框：
 * 与「字号」「字距」一致的滑条卡片样式，四方向（上/右/下/左）独立滑条，显示数字，
 * 上限 400，并提供「手动输入」入口可填写更大值。
 */
class WallpaperLayerMarginDialog : ComposeDialogFragment() {

    override val dialogSize: io.legado.app.ui.widget.compose.AppDialogSize =
        io.legado.app.ui.widget.compose.AppDialogSize.Form

    private var onApply: ((top: Int, right: Int, bottom: Int, left: Int) -> Unit)? = null
    private var onReset: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args = arguments ?: Bundle()
        val index = args.getInt(ARG_INDEX)
        val isBg = args.getBoolean(ARG_IS_BG)
        val init = if (isBg) {
            val cfg = ReadBookConfig.durConfig
            intArrayOf(
                cfg.wallpaperLayerBgMarginTop,
                cfg.wallpaperLayerBgMarginRight,
                cfg.wallpaperLayerBgMarginBottom,
                cfg.wallpaperLayerBgMarginLeft
            )
        } else {
            val it = ReadBookConfig.durConfig.wallpaperLayerItems
                .getOrNull(index)
                ?.let { WallpaperItem.fromJson(it) }
            intArrayOf(
                it?.marginTop ?: 0,
                it?.marginRight ?: 0,
                it?.marginBottom ?: 0,
                it?.marginLeft ?: 0
            )
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val maxMargin = 400
                var top by rememberSaveable { mutableIntStateOf(init[0].coerceIn(0, maxMargin)) }
                var right by rememberSaveable { mutableIntStateOf(init[1].coerceIn(0, maxMargin)) }
                var bottom by rememberSaveable { mutableIntStateOf(init[2].coerceIn(0, maxMargin)) }
                var left by rememberSaveable { mutableIntStateOf(init[3].coerceIn(0, maxMargin)) }

                fun applyNow() = onApply?.invoke(top, right, bottom, left)

                val sliders = listOf(
                    AppDialogSliderItem(
                        title = "↑ 上",
                        value = top,
                        range = 0..maxMargin,
                        showStepper = true,
                        onValueChange = { top = it; applyNow() },
                        onValueChangeFinished = { applyNow() }
                    ),
                    AppDialogSliderItem(
                        title = "→ 右",
                        value = right,
                        range = 0..maxMargin,
                        showStepper = true,
                        onValueChange = { right = it; applyNow() },
                        onValueChangeFinished = { applyNow() }
                    ),
                    AppDialogSliderItem(
                        title = "↓ 下",
                        value = bottom,
                        range = 0..maxMargin,
                        showStepper = true,
                        onValueChange = { bottom = it; applyNow() },
                        onValueChangeFinished = { applyNow() }
                    ),
                    AppDialogSliderItem(
                        title = "← 左",
                        value = left,
                        range = 0..maxMargin,
                        showStepper = true,
                        onValueChange = { left = it; applyNow() },
                        onValueChangeFinished = { applyNow() }
                    )
                )

                AppDialogFrame(
                    title = "图片边距（上 右 下 左）",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AppDialogSliderGrid(items = sliders)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        // 手动输入：可填写超过 400 的更大值
                                        showManualInput(top, right, bottom, left) { nt, nr, nb, nl ->
                                            top = nt; right = nr; bottom = nb; left = nl
                                            applyNow()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.width(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    androidx.compose.material.Text("手动输入")
                                }
                            }
                        }
                    },
                    actions = {
                        androidx.compose.material.TextButton(onClick = { onReset?.invoke(); dismissAllowingStateLoss() }) {
                            androidx.compose.material.Text(stringResource(android.R.string.cancel))
                        }
                        androidx.compose.material.TextButton(onClick = { applyNow(); dismissAllowingStateLoss() }) {
                            androidx.compose.material.Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
        }
    }

    private fun showManualInput(
        t: Int, r: Int, b: Int, l: Int,
        onResult: (Int, Int, Int, Int) -> Unit
    ) {
        showComposeTextInputDialog(
            title = "手动输入边距（上,右,下,左；可超过400）",
            hint = "用逗号分隔，例如 50,30,50,30",
            initialValue = "$t,$r,$b,$l",
            validateInput = {
                val parts = it.split(",")
                parts.size == 4 && parts.all { p -> p.trim().toIntOrNull() != null }
            },
            onPositive = { raw ->
                val parts = raw.split(",").map { it.trim().toIntOrNull() ?: 0 }
                onResult(
                    parts[0].coerceAtLeast(0),
                    parts[1].coerceAtLeast(0),
                    parts[2].coerceAtLeast(0),
                    parts[3].coerceAtLeast(0)
                )
            }
        )
    }

    companion object {
        private const val ARG_INDEX = "index"
        private const val ARG_IS_BG = "isBg"

        fun create(
            index: Int,
            isBg: Boolean,
            onApply: (top: Int, right: Int, bottom: Int, left: Int) -> Unit,
            onReset: () -> Unit
        ): WallpaperLayerMarginDialog {
            return WallpaperLayerMarginDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INDEX, index)
                    putBoolean(ARG_IS_BG, isBg)
                }
                this.onApply = onApply
                this.onReset = onReset
            }
        }
    }
}
