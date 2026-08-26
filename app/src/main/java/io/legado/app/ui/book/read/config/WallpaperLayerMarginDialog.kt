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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogSliderGrid
import io.legado.app.ui.widget.compose.AppDialogSliderItem
import io.legado.app.ui.widget.compose.ComposeDialogFragment
import io.legado.app.ui.widget.compose.showComposeTextInputDialog

/**
 * 图片/背景图层「边距」设置对话框。
 * 样式与「字号 / 字距」一致：四方向独立 AppThemedStepperSlider 卡片，
 * 显示数值、最大 400、支持点击数字手动输入更大值。
 */
class WallpaperLayerMarginDialog : ComposeDialogFragment() {

    private val index: Int by lazy { arguments?.getInt("index") ?: 0 }
    private val isBg: Boolean by lazy { arguments?.getBoolean("isBg") ?: false }
    private var onApply: ((Int, Int, Int, Int) -> Unit)? = null
    private var onReset: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MarginContent()
            }
        }
    }

    @Composable
    private fun MarginContent() {
        val cfg = ReadBookConfig.durConfig
        val cur = if (isBg) {
            intArrayOf(
                cfg.wallpaperLayerBgMarginTop,
                cfg.wallpaperLayerBgMarginRight,
                cfg.wallpaperLayerBgMarginBottom,
                cfg.wallpaperLayerBgMarginLeft
            )
        } else {
            val it = cfg.wallpaperLayerItems.getOrNull(index)
                ?.let { WallpaperItem.fromJson(it) }
            intArrayOf(
                it?.marginTop ?: 0,
                it?.marginRight ?: 0,
                it?.marginBottom ?: 0,
                it?.marginLeft ?: 0
            )
        }

        var top by remember { mutableIntStateOf(cur[0]) }
        var right by remember { mutableIntStateOf(cur[1]) }
        var bottom by remember { mutableIntStateOf(cur[2]) }
        var left by remember { mutableIntStateOf(cur[3]) }

        val dirLabels = listOf("上 ↑", "右 →", "下 ↓", "左 ←")

        AppDialogFrame(
            title = "边距（上下左右）",
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(4) { i ->
                        val getValue: () -> Int = when (i) {
                            0 -> ({ top }); 1 -> ({ right })
                            2 -> ({ bottom }); else -> ({ left })
                        }
                        val setValue: (Int) -> Unit = when (i) {
                            0 -> ({ top = it }); 1 -> ({ right = it })
                            2 -> ({ bottom = it }); else -> ({ left = it })
                        }
                        AppDialogSliderGrid(
                            items = listOf(
                                AppDialogSliderItem(
                                    title = dirLabels[i],
                                    value = getValue(),
                                    range = 0..400,
                                    showStepper = true,
                                    onValueChange = { setValue(it) },
                                    onValueChangeFinished = {
                                        onApply?.invoke(top, right, bottom, left)
                                    }
                                )
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 手动输入更大值（超过 400）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "手动输入（可大于 400）",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            this@WallpaperLayerMarginDialog.showComposeTextInputDialog(
                                title = "边距数值（0-2000）",
                                hint = "输入像素值",
                                initialValue = top.toString(),
                                validateInput = { it.toIntOrNull() != null },
                                onPositive = { v ->
                                    val n = v.toIntOrNull()?.coerceIn(0, 2000) ?: return@showComposeTextInputDialog
                                    top = n
                                    values[0].value = n
                                    onApply?.invoke(top, right, bottom, left)
                                }
                            )
                        }) {
                            Text("输入…")
                        }
                    }
                }
            },
            actions = {
                TextButton(onClick = {
                    onReset?.invoke()
                    dismiss()
                }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    onApply?.invoke(top, right, bottom, left)
                    dismiss()
                }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }

    companion object {
        fun create(
            index: Int,
            isBg: Boolean,
            onApply: ((Int, Int, Int, Int) -> Unit),
            onReset: (() -> Unit)
        ): WallpaperLayerMarginDialog {
            val d = WallpaperLayerMarginDialog()
            d.arguments = Bundle().apply {
                putInt("index", index)
                putBoolean("isBg", isBg)
            }
            d.onApply = onApply
            d.onReset = onReset
            return d
        }
    }
}
