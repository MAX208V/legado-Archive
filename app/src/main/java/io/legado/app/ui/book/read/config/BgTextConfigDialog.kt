package io.legado.app.ui.book.read.config

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import io.legado.app.ui.widget.compose.releaseComposeImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.appcompat.widget.AppCompatImageView
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isImage
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.WallpaperItem
import io.legado.app.ui.book.read.page.WallpaperLayerType
import io.legado.app.ui.book.read.page.isWallpaperPrefab
import io.legado.app.ui.file.HandleFileContract
import androidx.compose.runtime.MutableState
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.AppThemedStepperSlider
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.LegadoMiuixSwitch
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.showComposeChoiceListDialog
import io.legado.app.ui.widget.compose.showComposeMultiChoiceDialog
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SystemUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.inputStream
import io.legado.app.utils.dpToPx
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.longToast
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import splitties.init.appCtx

class BgTextConfigDialog : BaseDialogFragment(0) {

    companion object {
        const val TEXT_COLOR = 121
        const val BG_COLOR = 122
        const val TEXT_ACCENT_COLOR = 123
        private const val PREF_PAG_THEME_ROOT = "pref_pag_theme_root_dir"
    }

    private val configFileName = "readConfig.zip"
    private val importFormNet = "网络导入"
    private var presetBgImages by mutableStateOf<List<String>>(emptyList())
    private var refreshTick by mutableIntStateOf(0)
    private var pendingSelfConfigEvents = 0

    private val selectBgImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> setBgFromUri(uri) }
    }
    private val selectExportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> exportConfig(uri) }
    }
    private val selectImportDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.path == "/$importFormNet") {
                importNetConfigAlert()
            } else {
                importConfig(uri)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (pendingSelfConfigEvents > 0) {
                pendingSelfConfigEvents--
            } else {
                refreshTick++
            }
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val style = rememberAppDialogStyle()
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = style.bodyFontFamily)
                ) {
                    BgTextConfigContent(style = style)
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        presetBgImages = requireContext().assets.list("bg")?.toList().orEmpty()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    @Composable
    private fun BgTextConfigContent(style: AppDialogStyle) {
        refreshTick
        LegadoMiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            color = style.surface,
            contentColor = style.primaryText,
            cornerRadius = style.panelRadius,
            insidePadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                HeaderSection(style)
                ToggleSection(style)
                if (ReadBook.book?.isImage != true) {
                    UnderlineSection(style)
                }
                ColorSection(style)
                ImportExportSection(style)
                SliderSection(style)
                BackgroundImageSection(style)
            }
        }
    }

    @Composable
    private fun HeaderSection(style: AppDialogStyle) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.style_name),
                    color = style.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ReadBookConfig.durConfig.name.ifBlank { "文字" },
                    color = style.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit),
                    tint = style.secondaryText,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { editStyleName() }
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.restore),
                    color = style.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { restorePreset() }
                )
            }
        }
    }

    @Composable
    private fun ToggleSection(style: AppDialogStyle) {
        ReaderSectionCard(style = style, title = null, contentPadding = PaddingValues(6.dp)) {
            var darkStatusIcon by rememberSaveable(refreshTick) {
                mutableStateOf(ReadBookConfig.durConfig.curStatusIconDark())
            }
            var scrollFollowBg by rememberSaveable(refreshTick) {
                mutableStateOf(ReadBookConfig.durConfig.curReadScrollFollowBackground())
            }
            ReaderSwitchRow(
                title = stringResource(R.string.dark_status_icon),
                checked = darkStatusIcon,
                style = style
            ) {
                darkStatusIcon = it
                ReadBookConfig.durConfig.setCurStatusIconDark(it)
                (activity as? ReadBookActivity)?.upSystemUiVisibility()
            }
            ReaderSwitchRow(
                title = stringResource(R.string.read_scroll_follow_background),
                checked = scrollFollowBg,
                style = style,
                summary = stringResource(R.string.read_scroll_follow_background_summary)
            ) {
                scrollFollowBg = it
                ReadBookConfig.durConfig.setCurReadScrollFollowBackground(it)
                postReadConfigChanged(1, 5)
            }
            // 壁纸轮换
            WallpaperRotationSection(style)
            // 壁纸图层
            WallpaperLayersSection(style)
            // PAG叠加动画
            PagOverlaySection(style)
        }
    }

    @Composable
    private fun WallpaperRotationSection(style: AppDialogStyle) {
        var rotationEnabled by rememberSaveable(refreshTick) {
            mutableStateOf(ReadBookConfig.durConfig.wallpaperRotationEnabled)
        }
        var rotationInterval by rememberSaveable(refreshTick) {
            mutableIntStateOf(ReadBookConfig.durConfig.wallpaperRotationIntervalSec)
        }
        var entries by rememberSaveable(refreshTick) {
            mutableStateOf(ReadBookConfig.durConfig.wallpaperRotationImageList)
        }
        val presetImages = remember { requireContext().assets.list("bg")?.toList().orEmpty() }

        ReaderSwitchRow(
            title = stringResource(R.string.wallpaper_rotation),
            checked = rotationEnabled,
            style = style,
            summary = if (rotationEnabled) {
                stringResource(R.string.wallpaper_rotation_interval, rotationInterval)
            } else null
        ) {
            rotationEnabled = it
            ReadBookConfig.durConfig.wallpaperRotationEnabled = it
            postReadConfigChanged(9, 10)
            refreshTick++ // 联动壁纸图层：轮换项随开关显示/隐藏
        }
        if (rotationEnabled) {
            SliderRow(
                title = stringResource(R.string.wallpaper_rotation_interval_label),
                value = rotationInterval,
                range = 15..300,
                style = style,
                valueText = "${rotationInterval}秒"
            ) {
                rotationInterval = it
                ReadBookConfig.durConfig.wallpaperRotationIntervalSec = it
                // 立即重启轮换 Job 使新间隔生效
                postReadConfigChanged(9)
            }

            // --- 按白天/黑夜模式轮换（默认启用，无需开关）---
            // 列表项可设置白天☀️/黑夜🌙/都可用🌓，轮换时按当前模式过滤

            // --- 四种壁纸来源（左侧开关控制该来源是否参与轮换）---
            RotationSourceRow(
                prefKey = ReadBookConfig.PREF_ROTATION_SOURCE_CUSTOM,
                icon = R.drawable.ic_image,
                text = "选择自定义壁纸",
                count = entries.count { it.startsWith("custom:") },
                style = style,
                onClick = { addCustomWallpaper(entries) { entries = it } }
            )
            RotationSourceRow(
                prefKey = ReadBookConfig.PREF_ROTATION_SOURCE_STYLE,
                icon = R.drawable.ic_arrange,
                text = "添加样式壁纸",
                count = entries.count { it.startsWith("style:") },
                style = style,
                onClick = { addStyleWallpaper(entries) { entries = it } }
            )
            RotationSourceRow(
                prefKey = ReadBookConfig.PREF_ROTATION_SOURCE_BUILTIN,
                icon = R.drawable.ic_cfg_theme,
                text = "选择内置壁纸",
                count = entries.count { it.startsWith("asset:") || !it.contains(":") },
                total = presetImages.size,
                style = style,
                onClick = { showBuiltinWallpaperDialog(presetImages, entries) { entries = it } }
            )
            RotationSourceRow(
                prefKey = ReadBookConfig.PREF_ROTATION_SOURCE_PAGTHEME,
                icon = R.drawable.ic_play_outline_24dp,
                text = "选择PAG主题",
                count = entries.count { it.startsWith("pagtheme:") },
                style = style,
                onClick = { selectPagThemeRoot() }
            )
            RotationSourceRow(
                prefKey = ReadBookConfig.PREF_ROTATION_SOURCE_VIDEO,
                icon = R.drawable.ic_play_outline_24dp,
                text = "添加视频壁纸",
                count = entries.count { it.startsWith("video:") },
                style = style,
                onClick = { selectRotationVideo.launch {
                    mode = HandleFileContract.VIDEO
                    title = getString(R.string.select_video)
                } }
            )
            RotationSourceRow(
                prefKey = ReadBookConfig.PREF_ROTATION_SOURCE_URL,
                icon = R.drawable.ic_web_outline,
                text = "添加URL壁纸",
                count = entries.count { it.startsWith("http") },
                style = style,
                onClick = { showAddRotationUrlDialog(entries) { entries = it } }
            )

            // --- 当前轮换列表 ---
            if (entries.isNotEmpty()) {
                RotationEntryList(entries, style) { entries = it }
            }
        }
    }

    /** 来源行：左侧开关（控制该来源是否参与轮换）+ 来源按钮 */
    @Composable
    private fun RotationSourceRow(
        prefKey: String,
        icon: Int,
        text: String,
        count: Int,
        total: Int? = null,
        style: AppDialogStyle,
        onSourceToggled: () -> Unit = {},
        onClick: () -> Unit
    ) {
        val context = LocalContext.current
        var enabled by rememberSaveable(prefKey) {
            mutableStateOf(
                context.defaultSharedPreferences.getBoolean(prefKey, true)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegadoMiuixSwitch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    context.defaultSharedPreferences.edit()
                        .putBoolean(prefKey, it)
                        .apply()
                    onSourceToggled()
                    postReadConfigChanged(9)
                },
                palette = style.toMiuixPalette(),
                modifier = Modifier.padding(start = 2.dp, end = 6.dp)
            )
            RotationSourceButton(icon, text, count, total, style, onClick)
        }
    }

    @Composable
    private fun RotationSourceButton(
        icon: Int,
        text: String,
        count: Int,
        total: Int? = null,
        style: AppDialogStyle,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = style.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (total != null) "$count/$total" else "${count}张",
                    color = style.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    @Composable
    private fun RotationEntryList(
        entries: MutableList<String>,
        style: AppDialogStyle,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "当前轮换列表（☀️白天/🌙黑夜/🌓都可用，≡ 拖动排序，✕ 移除）",
                color = style.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val prefs = ctx.defaultSharedPreferences
            var visCount = 0
            entries.forEachIndexed { index, entry ->
                val (pureForVis, _) = ReadBookConfig.parseRotationEntry(entry)
                if (!ReadBookConfig.rotationSourceEnabled(pureForVis, prefs)) {
                    return@forEachIndexed // 来源开关关闭：轮换列表项隐藏
                }
                val visIndex = visCount++
                val label = rotationEntryLabel(entry)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(style.actionRadius),
                    color = style.fieldSurface,
                    contentColor = style.primaryText,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 缩略图（点击预览）
                        Box(
                            modifier = Modifier.clickable { previewRotationEntry(entry) }
                        ) {
                            WallpaperThumb(entry, style)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = style.primaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { previewRotationEntry(entry) }
                        )
                        // 白天/黑夜模式按钮（▶ 左边，三态循环）
                        val (pureEntry, mode) = ReadBookConfig.parseRotationEntry(entry)
                        val modeIcon = when (mode) {
                            ReadBookConfig.ROTATION_MODE_NIGHT -> "🌙"
                            ReadBookConfig.ROTATION_MODE_ALL -> "🌓"
                            else -> "☀️"
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val nextMode = when (mode) {
                                        ReadBookConfig.ROTATION_MODE_NIGHT -> ReadBookConfig.ROTATION_MODE_ALL
                                        ReadBookConfig.ROTATION_MODE_ALL -> ReadBookConfig.ROTATION_MODE_DAY
                                        else -> ReadBookConfig.ROTATION_MODE_NIGHT
                                    }
                                    val mutable = entries.toMutableList()
                                    mutable[index] = ReadBookConfig.buildRotationEntry(pureEntry, nextMode)
                                    onChanged(ArrayList(mutable))
                                    ReadBookConfig.durConfig.wallpaperRotationImageList = ArrayList(mutable)
                                    postReadConfigChanged(9)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeIcon,
                                fontSize = 13.sp
                            )
                        }
                        // ▶ 预览按钮（所有条目：PAG主题→动画预览，其余→大图预览）
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { previewRotationEntry(entry) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "▶",
                                color = style.accent,
                                fontSize = 14.sp
                            )
                        }
                        // ≡ 拖动手柄（长按拖动排序）
                        RotationEntryDragHandle(
                            onMoveBy = { delta ->
                                moveRotationEntryVis(entries, entry, delta, onChanged)
                            }
                        )

                        // ✕ 移除按钮（独立点击区）
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val mutable = entries.toMutableList()
                                    mutable.removeAt(index)
                                    onChanged(ArrayList(mutable))
                                    ReadBookConfig.durConfig.wallpaperRotationImageList = ArrayList(mutable)
                                    postReadConfigChanged(9)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = style.danger,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    /** 长按拖动排序手柄（参考 RuleSubDragHandle 实现） */
    @Composable
    private fun RotationEntryDragHandle(onMoveBy: (Int) -> Unit) {
        val density = LocalDensity.current
        val thresholdPx = with(density) { 36.dp.toPx() }
        var accumulatedY by remember { mutableFloatStateOf(0f) }
        Icon(
            painter = painterResource(R.drawable.ic_menu),
            contentDescription = null,
            tint = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.5f),
            modifier = Modifier
                .size(44.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = {
                            accumulatedY = 0f
                        },
                        onDragCancel = {
                            accumulatedY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedY += dragAmount.y
                            while (accumulatedY >= thresholdPx) {
                                onMoveBy(1)
                                accumulatedY -= thresholdPx
                            }
                            while (accumulatedY <= -thresholdPx) {
                                onMoveBy(-1)
                                accumulatedY += thresholdPx
                            }
                        }
                    )
                }
                .padding(7.dp)
        )
    }

    /** 轮换列表拖动：按「正在拖动的项」实时定位（拖动触发重组后 visIndex 过期 → 按 entry 查找避免越界） */
    private fun moveRotationEntryVis(
        entries: MutableList<String>,
        entry: String,
        delta: Int,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        val prefs = requireContext().defaultSharedPreferences
        val visible = entries.filter {
            ReadBookConfig.rotationSourceEnabled(
                ReadBookConfig.parseRotationEntry(it).first, prefs
            )
        }
        if (visible.size <= 1) return
        val from = visible.indexOf(entry)
        if (from < 0) return
        val target = from + delta
        if (target !in visible.indices) return
        val mutable = entries.toMutableList()
        mutable.remove(entry)
        val anchor = visible[target]
        val insertAt = mutable.indexOf(anchor)
        if (insertAt < 0) return
        mutable.add(if (target < from) insertAt else insertAt + 1, entry)
        onChanged(ArrayList(mutable))
        ReadBookConfig.durConfig.wallpaperRotationImageList = ArrayList(mutable)
        postReadConfigChanged(9)
    }

    private fun rotationEntryLabel(entry: String): String {
        val (pureEntry, _) = ReadBookConfig.parseRotationEntry(entry)
        return when {
            pureEntry.startsWith("video:") -> {
                "🎬 ${pureEntry.removePrefix("video:").substringAfterLast("/")}"
            }
            pureEntry.startsWith("http") -> {
                "🌐 ${pureEntry.substringAfterLast("/")}"
            }
            pureEntry.startsWith("custom:") -> {
                val name = pureEntry.removePrefix("custom:").substringAfterLast("/")
                "🖼️ $name"
            }
            pureEntry.startsWith("style:") -> {
                val idx = pureEntry.removePrefix("style:").toIntOrNull() ?: -1
                val styleConfig = ReadBookConfig.getConfig(idx)
                "🎨 ${styleConfig.name}"
            }
            pureEntry.startsWith("pagtheme:") -> {
                "🎞️ ${pureEntry.removePrefix("pagtheme:").substringAfterLast("/")}"
            }
            else -> {
                val name = pureEntry.removePrefix("asset:").substringBeforeLast(".")
                "🖼️ $name"
            }
        }
    }

    /** 解析轮换条目对应的预览路径（背景图）；纯色返回 null */
    private fun rotationEntryImagePath(entry: String): String? {
        val (pureEntry, _) = ReadBookConfig.parseRotationEntry(entry)
        return when {
            pureEntry.startsWith("custom:") -> pureEntry.removePrefix("custom:")
            pureEntry.startsWith("style:") -> {
                configBgImagePath(
                    ReadBookConfig.getConfig(pureEntry.removePrefix("style:").toIntOrNull() ?: 0)
                )
            }
            pureEntry.startsWith("pagtheme:") -> {
                themeBackground(File(pureEntry.removePrefix("pagtheme:")))?.absolutePath
            }
            pureEntry.startsWith("video:") -> pureEntry.removePrefix("video:")
            pureEntry.startsWith("http") -> pureEntry
            else -> {
                val name = pureEntry.removePrefix("asset:")
                "file:///android_asset/bg/$name"
            }
        }
    }

    /** 统一条目预览：PAG主题→动画预览，样式→完整预览（背景+PAG+文字），其余→大图预览 */
    private fun previewRotationEntry(entry: String) {
        val (pureEntry, _) = ReadBookConfig.parseRotationEntry(entry)
        if (pureEntry.startsWith("video:")) {
            requireContext().toastOnUi("🎬 视频壁纸（轮换到时循环播放）：${pureEntry.removePrefix("video:").substringAfterLast("/")}")
            return
        }
        when {
            pureEntry.startsWith("pagtheme:") -> {
                pagThemePreview(File(pureEntry.removePrefix("pagtheme:")))
            }
            pureEntry.startsWith("style:") -> {
                stylePreview(
                    ReadBookConfig.getConfig(pureEntry.removePrefix("style:").toIntOrNull() ?: 0)
                )
            }
            else -> showWallpaperPreview(entry)
        }
    }

    /** 样式完整预览：背景图（缩略图同链路）+ PAG动画（如启用）+ 示例文字（样式当前文字色） */
    private fun stylePreview(config: ReadBookConfig.Config) {
        val context = requireContext()
        val container = FrameLayout(context).apply {
            // 明确像素宽高，避免 dialog wrap_content 下 MATCH_PARENT 宽测为 0（预览空白）
            layoutParams = FrameLayout.LayoutParams(
                (SystemUtils.screenWidthPx * 0.92f).toInt(),
                (SystemUtils.screenHeightPx * 0.72f).toInt()
            )
        }
        val bgView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val bgPath = configBgImagePath(config)
        if (bgPath != null) {
            ImageLoader.load(context, bgPath)
                .centerCrop()
                .error(R.drawable.image_loading_error)
                .into(bgView)
        } else {
            bgView.setBackgroundColor(configBgColor(config))
        }
        container.addView(bgView)
        // 样式启用了 PAG 叠加动画则预览播放
        val pagPath = if (config.pagOverlayEnabled && config.pagOverlayPath.isNotBlank()) {
            config.pagOverlayPath
        } else null
        val pagView = if (pagPath != null) {
            org.libpag.PAGView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                runCatching {
                    setPath(pagPath)
                    setRepeatCount(-1)
                    setScaleMode(org.libpag.PAGScaleMode.Zoom)
                    play()
                }
            }
        } else null
        pagView?.let { container.addView(it) }
        val textColor = config.curTextColor()
        val textView = TextView(context).apply {
            text = "样式预览文字"
            setTextColor(textColor)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(textView)
        alert(title = config.name.ifBlank { "样式预览" }) {
            customView { container }
            okButton()
            onDismiss {
                pagView?.let {
                    runCatching { if (it.isPlaying) it.stop() }
                }
            }
        }
    }

    /** 点击轮换条目预览大图 */
    private fun showWallpaperPreview(entry: String) {
        val context = requireContext()
        // 用明确的像素宽高（不用 MATCH_PARENT），避免 dialog wrap_content 下宽测为 0
        // 导致 Glide 拿不到有效尺寸而不绘制（表现为空白）
        val maxW = (SystemUtils.screenWidthPx * 0.92f).toInt()
        val maxH = (SystemUtils.screenHeightPx * 0.72f).toInt()
        val imageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(maxW, maxH)
        }
        val loadPath = rotationEntryImagePath(entry)
        if (loadPath != null) {
            ImageLoader.load(context, loadPath)
                .centerCrop()
                .error(R.drawable.image_loading_error)
                .into(imageView)
        } else {
            val (pureEntry, _) = ReadBookConfig.parseRotationEntry(entry)
            val color = if (pureEntry.startsWith("style:")) {
                configBgColor(
                    ReadBookConfig.getConfig(pureEntry.removePrefix("style:").toIntOrNull() ?: 0)
                )
            } else {
                0xFFEEEEEE.toInt()
            }
            imageView.setBackgroundColor(color)
        }
        alert(title = rotationEntryLabel(entry)) {
            customView {
                imageView
            }
            okButton()
        }
    }

    @Composable
    private fun WallpaperThumb(entry: String, style: AppDialogStyle) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(style.surface)
        ) {
            val (pureEntry, _) = ReadBookConfig.parseRotationEntry(entry)
            if (pureEntry.startsWith("video:")) {
                // 视频条目：▶ 图标
                Icon(
                    painter = painterResource(R.drawable.ic_play_24dp),
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier.size(18.dp)
                )
            } else {
            val loadPath = when {
                pureEntry.startsWith("custom:") -> pureEntry.removePrefix("custom:")
                pureEntry.startsWith("pagtheme:") -> {
                    themeBackground(File(pureEntry.removePrefix("pagtheme:")))?.absolutePath
                }
                pureEntry.startsWith("http") -> pureEntry
                pureEntry.startsWith("asset:") || !pureEntry.contains(":") -> {
                    val name = pureEntry.removePrefix("asset:")
                    "file:///android_asset/bg/$name"
                }
                pureEntry.startsWith("style:") -> {
                    configBgImagePath(
                        ReadBookConfig.getConfig(pureEntry.removePrefix("style:").toIntOrNull() ?: 0)
                    )
                }
                else -> null
            }
            if (loadPath != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        AppCompatImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { iv ->
                        iv.setImageDrawable(null)
                        ImageLoader.load(iv.context, loadPath).centerCrop().into(iv)
                    },
                    onRelease = { it.releaseComposeImage() }
                )
            } else {
                // 纯色背景
                val color = runCatching {
                    when {
                        pureEntry.startsWith("style:") -> {
                            configBgColor(
                                ReadBookConfig.getConfig(pureEntry.removePrefix("style:").toIntOrNull() ?: 0)
                            )
                        }
                        else -> 0xFFEEEEEE.toInt()
                    }
                }.getOrDefault(0xFFEEEEEE.toInt())
                Box(modifier = Modifier.fillMaxSize().background(Color(color)))
            }
            }
        }
    }

    private fun addCustomWallpaper(
        currentEntries: MutableList<String>,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        selectCustomWallpaper.launch {
            mode = HandleFileContract.IMAGE
        }
    }

    private val selectCustomWallpaper = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> addCustomWallpaperFromUri(uri) }
    }

    private val selectRotationVideo = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            val entry = ReadBookConfig.buildRotationEntry("video:${uri}")
            val list = ReadBookConfig.durConfig.wallpaperRotationImageList
            list.add(entry)
            ReadBookConfig.durConfig.wallpaperRotationImageList = list
            refreshTick++
            postReadConfigChanged(9)
        }
    }

    private fun addCustomWallpaperFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val context = requireContext()
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bgDir = File(context.externalFiles, "bg")
                bgDir.mkdirs()
                val fileName = buildCustomWallpaperFileName(context, uri)
                val destFile = File(bgDir, fileName)
                destFile.outputStream().use { out -> inputStream.copyTo(out) }
                val entry = ReadBookConfig.buildRotationEntry("custom:${destFile.absolutePath}")
                val list = ReadBookConfig.durConfig.wallpaperRotationImageList
                list.add(entry)
                ReadBookConfig.durConfig.wallpaperRotationImageList = list
                refreshTick++
                postReadConfigChanged(9)
            } catch (e: Exception) {
                requireContext().toastOnUi(e.stackTraceStr)
            }
        }
    }


    /** 添加URL壁纸：直链（http/https）直接入轮换列表 */
    private fun showAddRotationUrlDialog(
        entries: MutableList<String>,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        val context = requireContext()
        val editText = android.widget.EditText(context).apply {
            hint = "https://example.com/wallpaper.jpg"
            setSingleLine(true)
        }
        alert("添加URL壁纸") {
            customView {
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(android.widget.TextView(context).apply {
                        text = "支持图片/视频直链（https）"
                        textSize = 12f
                    })
                    addView(editText)
                }
            }
            okButton {
                val url = editText.text.toString().trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    context.toastOnUi("请输入 http/https 链接")
                } else {
                    val updated = ArrayList(entries)
                    updated.add(ReadBookConfig.buildRotationEntry(url))
                    onChanged(updated)
                    ReadBookConfig.durConfig.wallpaperRotationImageList = updated
                    refreshTick++
                    postReadConfigChanged(9)
                }
            }
            noButton()
        }
    }

    /**
     * 生成自定义壁纸文件名：
     * - 保留原始扩展名（取 MIME 类型映射，其次取原文件名后缀）
     * - 净化 URL 编码与非法文件名字符
     * - 加时间戳前缀避免重名覆盖
     *
     * 注意：Uri.getLastPathSegment() 已返回解码后的字符串（中文/空格已还原），
     * 无需再做 URLDecoder.decode，避免 `+`→空格、非法 `%` 序列等双重解码问题。
     */
    private fun buildCustomWallpaperFileName(context: Context, uri: Uri): String {
        // 1. 从 MIME 推断扩展名（content:// 场景最可靠）
        val mimeExt = runCatching {
            context.contentResolver.getType(uri)
        }.getOrNull()?.let { mime ->
            when (mime.lowercase()) {
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/bmp" -> ".bmp"
                "image/gif" -> ".gif"
                else -> null
            }
        }
        // 2. 取原文件名（Uri.getLastPathSegment 已解码）
        val rawName = uri.lastPathSegment ?: ""
        // 3. 扩展名回退：原文件名后缀
        val nameExt = rawName.substringAfterLast('.', "").takeIf {
            it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() }
        }?.let { ".$it" }
        val ext = mimeExt ?: nameExt ?: ".jpg"
        // 4. 净化文件名主体（保留中文/字母/数字/下划线/中划线）
        val base = rawName.substringBeforeLast('.', rawName)
            .ifBlank { "wallpaper" }
            .replace(Regex("[^\\u4e00-\\u9fa5a-zA-Z0-9_-]"), "_")
            .take(40)
            .ifBlank { "wallpaper" }
        return "custom_${System.currentTimeMillis()}_$base$ext"
    }

    private fun addStyleWallpaper(
        currentEntries: MutableList<String>,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        val configs = ReadBookConfig.configList
        val styleNames = configs.mapIndexed { i, c ->
            c.name.ifBlank { "样式${i + 1}" }
        }
        val thumbnails = configs.map { config ->
            styleThumbnailSpec(config)
        }
        val currentStyleEntries = currentEntries.mapNotNull {
            val (pureEntry, _) = ReadBookConfig.parseRotationEntry(it)
            if (pureEntry.startsWith("style:")) {
                pureEntry.removePrefix("style:").toIntOrNull()
            } else null
        }.toSet()
        val checkedIndices = configs.indices.filter { it in currentStyleEntries }.toSet()
        showComposeMultiChoiceDialog(
            title = "选择要加入轮换的样式（多选）",
            labels = styleNames,
            checkedIndices = checkedIndices,
            thumbnails = thumbnails,
            positiveText = getString(android.R.string.ok),
            negativeText = getString(android.R.string.cancel),
            onPositive = { checkedArray ->
                val newSelected = configs.indices.filter { i ->
                    i < checkedArray.size && checkedArray[i]
                }
                val list = ReadBookConfig.durConfig.wallpaperRotationImageList
                // 移除旧的 style 条目
                val mutable = list.filter { e ->
                    val (pure, _) = ReadBookConfig.parseRotationEntry(e)
                    !pure.startsWith("style:")
                }.toMutableList()
                newSelected.forEach { i ->
                    mutable.add(ReadBookConfig.buildRotationEntry("style:$i"))
                }
                val result = ArrayList(mutable)
                onChanged(result)
                ReadBookConfig.durConfig.wallpaperRotationImageList = result
                postReadConfigChanged(9)
            },
            onDismissAction = { refreshTick++ }
        )
    }

    // ── PAG 主题 ──

    private fun selectPagThemeRoot() {
        // 记住上次根目录：再次点击直接扫描上次目录，不用重新选文件夹
        val savedPath = requireContext().defaultSharedPreferences
            .getString(PREF_PAG_THEME_ROOT, null)
        val savedDir = savedPath?.let { File(it) }
        if (savedDir != null && savedDir.isDirectory) {
            showPagThemeDialog(
                savedDir,
                entries = ReadBookConfig.durConfig.wallpaperRotationImageList
            ) { list ->
                ReadBookConfig.durConfig.wallpaperRotationImageList = list
                postReadConfigChanged(9)
            }
        } else {
            openPagThemeRootPicker()
        }
    }

    private fun openPagThemeRootPicker() {
        selectPagThemeRootContract.launch {
            mode = HandleFileContract.DIR
            title = "选择PAG主题根目录"
        }
    }

    private val selectPagThemeRootContract = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            val path = uri.path ?: return@let
            val dir = File(path)
            // 记住本次选择的根目录
            requireContext().defaultSharedPreferences.edit()
                .putString(PREF_PAG_THEME_ROOT, path)
                .apply()
            showPagThemeDialog(
                dir,
                entries = ReadBookConfig.durConfig.wallpaperRotationImageList
            ) { list ->
                ReadBookConfig.durConfig.wallpaperRotationImageList = list
                postReadConfigChanged(9)
            }
        }
    }

    private fun showPagThemeDialog(
        rootDir: File,
        entries: MutableList<String>,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        if (!rootDir.isDirectory) {
            requireContext().toastOnUi("所选目录无效：${rootDir.path}")
            return
        }
        val themeDirs = rootDir.listFiles { it.isDirectory }
            ?.sortedBy { it.name }
            .orEmpty()
        if (themeDirs.isEmpty()) {
            requireContext().toastOnUi("所选目录下没有子文件夹")
            return
        }
        val labels = themeDirs.map { it.name }
        val thumbnails = themeDirs.map { dir ->
            val thumb = themePreviewFile(dir)
            if (thumb != null) "image:${thumb.absolutePath}" else "color:#EEEEEE"
        }
        val currentEntries = entries.mapNotNull {
            val (pureEntry, _) = ReadBookConfig.parseRotationEntry(it)
            if (pureEntry.startsWith("pagtheme:")) {
                pureEntry.removePrefix("pagtheme:")
            } else null
        }.toSet()
        val checkedIndices = themeDirs.indices
            .filter { themeDirs[it].absolutePath in currentEntries }
            .toSet()
        showComposeMultiChoiceDialog(
            title = "选择PAG主题（根目录：${rootDir.name}）",
            labels = labels,
            checkedIndices = checkedIndices,
            thumbnails = thumbnails,
            actionText = "▶",
            positiveText = getString(android.R.string.ok),
            negativeText = getString(android.R.string.cancel),
            extraActionText = "更换目录",
            onExtraAction = { openPagThemeRootPicker() },
            onItemActionClick = { index ->
                pagThemePreview(themeDirs[index])
            },
            onDismissAction = { refreshTick++ },
            onPositive = { checkedArray ->
                val newSelected = themeDirs.indices.filter { i ->
                    i < checkedArray.size && checkedArray[i]
                }
                val mutable = entries.filter { e ->
                    val (pure, _) = ReadBookConfig.parseRotationEntry(e)
                    !pure.startsWith("pagtheme:")
                }.toMutableList()
                newSelected.forEach { i ->
                    mutable.add(
                        ReadBookConfig.buildRotationEntry("pagtheme:${themeDirs[i].absolutePath}")
                    )
                }
                val result = ArrayList(mutable)
                onChanged(result)
                ReadBookConfig.durConfig.wallpaperRotationImageList = result
                postReadConfigChanged(9)
            }
        )
    }

    /** 背景图：优先 theme.json bg.image，否则扫描 jpg */
    private fun themeBackground(dir: File): File? {
        val cfg = ReadBookConfig.parsePagThemeConfig(dir)
        return cfg?.bgImage
            ?.let { File(dir, it).takeIf { f -> f.isFile } }
            ?: dir.listFiles { it.isFile && it.extension.equals("jpg", true) }
                ?.minByOrNull { it.name }
    }

    /** PAG 动画：优先 theme.json pagLayer，否则扫描 .pag */
    private fun themePagFile(dir: File): File? {
        val cfg = ReadBookConfig.parsePagThemeConfig(dir)
        return cfg?.pagLayer
            ?.let { File(dir, it).takeIf { f -> f.isFile } }
            ?: dir.listFiles { it.isFile && it.extension.equals("pag", true) }
                ?.minByOrNull { it.name }
    }

    /** 缩略图：优先 theme.json previewImage，否则背景图 */
    private fun themePreviewFile(dir: File): File? {
        val cfg = ReadBookConfig.parsePagThemeConfig(dir)
        return cfg?.previewImage
            ?.let { File(dir, it).takeIf { f -> f.isFile } }
            ?: themeBackground(dir)
    }

    /** 预览 PAG 主题：背景图 + PAG 动画 + 示例文字（theme.json 字体色） */
    private fun pagThemePreview(dir: File) {
        val context = requireContext()
        val container = FrameLayout(context).apply {
            // 明确像素宽高，避免 dialog wrap_content 下 MATCH_PARENT 宽测为 0（预览空白）
            layoutParams = FrameLayout.LayoutParams(
                (SystemUtils.screenWidthPx * 0.92f).toInt(),
                (SystemUtils.screenHeightPx * 0.72f).toInt()
            )
        }
        val bgView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val bg = themeBackground(dir)
        if (bg != null) {
            ImageLoader.load(context, bg.absolutePath)
                .centerCrop()
                .error(R.drawable.image_loading_error)
                .into(bgView)
        } else {
            val bgColor = ReadBookConfig.parsePagThemeConfig(dir)?.backgroundColor
                ?: 0xFFEEEEEE.toInt()
            bgView.setBackgroundColor(bgColor)
        }
        container.addView(bgView)
        val pagFile = themePagFile(dir)
        val pagView = if (pagFile != null) {
            org.libpag.PAGView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                runCatching {
                    setPath(pagFile.absolutePath)
                    setRepeatCount(-1)
                    setScaleMode(org.libpag.PAGScaleMode.Zoom)
                    play()
                }
            }
        } else null
        pagView?.let { container.addView(it) }
        val fontColor = ReadBookConfig.parseThemeFontColor(dir) ?: 0xFF3E3D3B.toInt()
        val textView = TextView(context).apply {
            text = "主题示例文字"
            setTextColor(fontColor)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(textView)
        alert(title = dir.name) {
            customView { container }
            okButton()
            onDismiss {
                pagView?.let {
                    runCatching { if (it.isPlaying) it.stop() }
                }
            }
        }
    }

    /**
     * 解析样式背景为缩略图 spec。
     * - 当前模式有壁纸 → 显示当前模式壁纸（白天/黑夜自动切换）
     * - 当前模式是纯色 → 回退读取黑夜模式壁纸，再回退日间壁纸
     * - 都没有 → 返回 null（纯色，用 configBgColor() 取色）
     */
    private fun configBgImagePath(config: ReadBookConfig.Config): String? {
        when (config.curBgType()) {
            1, 2 -> return resolveStyleBgPath(config.curBgType(), config.curBgStr())
        }
        // 当前模式纯色：优先读取黑夜模式壁纸
        if (config.bgTypeNight == 1 || config.bgTypeNight == 2) {
            return resolveStyleBgPath(config.bgTypeNight, config.bgStrNight)
        }
        // 再回退日间壁纸
        if (config.bgType == 1 || config.bgType == 2) {
            return resolveStyleBgPath(config.bgType, config.bgStr)
        }
        return null
    }

    private fun resolveStyleBgPath(bgType: Int, bgStr: String): String {
        return when (bgType) {
            1 -> "file:///android_asset/bg/$bgStr"
            else -> {
                if (bgStr.contains(File.separator)) bgStr
                else FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
            }
        }
    }

    private fun configBgColor(config: ReadBookConfig.Config): Int {
        return runCatching { config.curBgStr().toColorInt() }
            .getOrDefault(0xFFEEEEEE.toInt())
    }

    /** 生成样式缩略图 spec（与样式库预览一致） */
    private fun styleThumbnailSpec(config: ReadBookConfig.Config): String {
        return configBgImagePath(config)?.let { "image:$it" }
            ?: "color:${config.curBgStr()}"
    }

    private fun showBuiltinWallpaperDialog(
        presetImages: List<String>,
        currentEntries: MutableList<String>,
        onChanged: (ArrayList<String>) -> Unit
    ) {
        val currentAssetEntries = currentEntries.map {
            val (pureEntry, _) = ReadBookConfig.parseRotationEntry(it)
            if (pureEntry.startsWith("asset:")) pureEntry.removePrefix("asset:")
            else if (!pureEntry.contains(":")) pureEntry
            else null
        }.filterNotNull().toSet()
        val labels = presetImages.map { it.substringBeforeLast(".") }
        val thumbnails = presetImages.map { "image:file:///android_asset/bg/$it" }
        val checkedIndices = presetImages.indices.filter { presetImages[it] in currentAssetEntries }.toSet()
        showComposeMultiChoiceDialog(
            title = getString(R.string.select_rotation_images),
            labels = labels,
            checkedIndices = checkedIndices,
            thumbnails = thumbnails,
            positiveText = getString(android.R.string.ok),
            negativeText = getString(android.R.string.cancel),
            onPositive = { checkedArray ->
                val newAssets = presetImages.filterIndexed { i, _ ->
                    i < checkedArray.size && checkedArray[i]
                }
                // 移除旧的 asset 条目，添加新选的
                val mutable = currentEntries.filter { e ->
                    val (pure, _) = ReadBookConfig.parseRotationEntry(e)
                    val assetName = if (pure.startsWith("asset:")) pure.removePrefix("asset:")
                        else if (!pure.contains(":")) pure else null
                    assetName == null || assetName !in currentAssetEntries
                }.toMutableList()
                newAssets.forEach { name ->
                    mutable.add(ReadBookConfig.buildRotationEntry("asset:$name"))
                }
                val result = ArrayList(mutable)
                onChanged(result)
                ReadBookConfig.durConfig.wallpaperRotationImageList = result
                postReadConfigChanged(9)
            },
            onDismissAction = { refreshTick++ }
        )
    }

    // ===== 壁纸图层 =====
    @Composable
    private fun WallpaperLayersSection(style: AppDialogStyle) {
        var enabled by rememberSaveable(refreshTick) {
            mutableStateOf(ReadBookConfig.durConfig.wallpaperLayersEnabled)
        }
        var items by rememberSaveable(refreshTick) {
            val raw = ArrayList(ReadBookConfig.durConfig.wallpaperLayerItems)
            val norm = normalizeLayerItems(raw)
            if (norm != raw) ReadBookConfig.durConfig.wallpaperLayerItems = norm
            mutableStateOf(norm)
        }
        ReaderSwitchRow(
            title = "壁纸图层",
            checked = enabled,
            style = style,
            summary = if (enabled) {
                "多图层从下到上叠放，可调顺序（如：底层视频+上层镂空窗户）"
            } else {
                "默认含原有背景图片；轮换开启时含轮换壁纸项"
            }
        ) {
            enabled = it
            ReadBookConfig.durConfig.wallpaperLayersEnabled = it
            applyWallpaperLayers()
        }
        if (enabled) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "图层顺序：列表底部=最底层，顶部=最上层（↑ ↓ 调整，✕ 删除；背景/轮换为预置项）",
                    color = style.secondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val prefs = ctx.defaultSharedPreferences
            var visCount = 0
                items.forEachIndexed { index, entry ->
                    if (!entry.isWallpaperPrefab() &&
                        !ReadBookConfig.layerSourceEnabled(entry, prefs)
                    ) {
                        return@forEachIndexed // 来源开关关闭：隐藏该图层项
                    }
                    val visIndex = visCount++
                    WallpaperLayerRow(
                        entry = entry,
                        index = index,
                        visIndex = visIndex,
                        total = items.size,
                        style = style,
                        onDelete = { removeWallpaperLayer(index) }
                    )
                }
                if (visCount < items.size) {
                    Text(
                        text = "（部分来源已关闭开关，对应图层已隐藏）",
                        color = style.secondaryText.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 各来源带开关（与壁纸轮换一致）：关闭 → 该来源图层隐藏且不渲染
                RotationSourceRow(
                    prefKey = ReadBookConfig.PREF_LAYER_SOURCE_IMAGE,
                    icon = R.drawable.ic_image,
                    text = "添加图片壁纸（PNG可镂空）",
                    count = items.count {
                        WallpaperItem.fromJson(it)?.type == WallpaperLayerType.IMAGE &&
                            ReadBookConfig.layerSourceEnabled(it, prefs)
                    },
                    style = style,
                    onSourceToggled = { applyWallpaperLayers() }
                ) { addWallpaperLayerFile(WallpaperLayerType.IMAGE) }
                RotationSourceRow(
                    prefKey = ReadBookConfig.PREF_LAYER_SOURCE_VIDEO,
                    icon = R.drawable.ic_play_24dp,
                    text = "添加视频壁纸（循环播放）",
                    count = items.count {
                        WallpaperItem.fromJson(it)?.type == WallpaperLayerType.VIDEO &&
                            ReadBookConfig.layerSourceEnabled(it, prefs)
                    },
                    style = style,
                    onSourceToggled = { applyWallpaperLayers() }
                ) { addWallpaperLayerFile(WallpaperLayerType.VIDEO) }
                RotationSourceRow(
                    prefKey = ReadBookConfig.PREF_LAYER_SOURCE_URL,
                    icon = R.drawable.ic_add_online,
                    text = "添加URL壁纸（直链/解析）",
                    count = items.count {
                        val t = WallpaperItem.fromJson(it)?.type
                        (t == WallpaperLayerType.URL_IMAGE || t == WallpaperLayerType.URL_RESOLVE) &&
                            ReadBookConfig.layerSourceEnabled(it, prefs)
                    },
                    style = style,
                    onSourceToggled = { applyWallpaperLayers() }
                ) { showAddWallpaperLayerUrlDialog() }
            }
        }
    }

    /** 图层项缩略图：预置项显示实际背景/轮换缩略，自定义项显示来源 */
    @Composable
    private fun WallpaperLayerThumb(entry: String, style: AppDialogStyle) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(style.surface)
        ) {
            when (entry) {
                WallpaperLayerType.PREFAB_BG -> {
                    val cfg = ReadBookConfig.durConfig
                    val bgPath = configBgImagePath(cfg)
                    if (bgPath != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                AppCompatImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { iv ->
                                iv.setImageDrawable(null)
                                ImageLoader.load(iv.context, bgPath).centerCrop().into(iv)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(configBgColor(cfg)))
                        )
                    }
                }
                WallpaperLayerType.PREFAB_ROTATION -> {
                    val first = ReadBookConfig.durConfig.wallpaperRotationImageList.firstOrNull()
                    if (first != null) {
                        WallpaperThumb(first, style)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_mode_random),
                            contentDescription = null,
                            tint = style.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                else -> {
                    val item = WallpaperItem.fromJson(entry)
                    when {
                        item == null -> Unit
                        item.type == WallpaperLayerType.VIDEO ||
                            item.type == WallpaperLayerType.LIVE_PHOTO -> Icon(
                            painter = painterResource(R.drawable.ic_play_24dp),
                            contentDescription = null,
                            tint = style.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        item.type == WallpaperLayerType.URL_IMAGE ||
                            item.type == WallpaperLayerType.URL_RESOLVE -> Icon(
                            painter = painterResource(R.drawable.ic_add_online),
                            contentDescription = null,
                            tint = style.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        else -> AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                AppCompatImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { iv ->
                                iv.setImageDrawable(null)
                                ImageLoader.load(iv.context, item.src).centerCrop().into(iv)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WallpaperLayerRow(
        entry: String,
        index: Int,
        visIndex: Int,
        total: Int,
        style: AppDialogStyle,
        onDelete: () -> Unit
    ) {
        val isPrefab = entry.isWallpaperPrefab()
        val item = WallpaperItem.fromJson(entry)
        val label = when (entry) {
            WallpaperLayerType.PREFAB_BG -> "背景图片"
            WallpaperLayerType.PREFAB_ROTATION -> "轮换壁纸"
            else -> item?.typeLabel() ?: "未知图层"
        }
        val sub = when (entry) {
            WallpaperLayerType.PREFAB_BG -> "Legado 原有背景"
            WallpaperLayerType.PREFAB_ROTATION -> "轮换壁纸（轮换中）"
            else -> item?.src ?: ""
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 缩略图（点击预览）
                Box(
                    modifier = Modifier.clickable { previewWallpaperLayer(entry) }
                ) {
                    WallpaperLayerThumb(entry, style)
                }
                Spacer(Modifier.width(8.dp))
                // 名称/来源（点击预览）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { previewWallpaperLayer(entry) }
                ) {
                    Text(
                        text = label,
                        color = style.primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sub,
                        color = style.secondaryText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 🔊 视频/LivePhoto 声音开关（仅自定义项；默认静音）
                if ((item?.type == WallpaperLayerType.VIDEO ||
                        item?.type == WallpaperLayerType.LIVE_PHOTO) && !isPrefab) {
                    val soundIcon = if (item.soundOn) "🔊" else "🔇"
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { toggleWallpaperLayerSound(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(soundIcon, fontSize = 13.sp)
                    }
                }
                // 白天/黑夜模式按钮（☀️→🌙→🌓 三态循环；预置项固定 🌓 不可改）
                val mode = item?.mode ?: ReadBookConfig.ROTATION_MODE_ALL
                val modeIcon = when (mode) {
                    ReadBookConfig.ROTATION_MODE_DAY -> "☀️"
                    ReadBookConfig.ROTATION_MODE_NIGHT -> "🌙"
                    else -> "🌓"
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isPrefab) { setWallpaperLayerMode(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeIcon,
                        fontSize = 13.sp,
                        color = if (isPrefab) {
                            style.secondaryText.copy(alpha = 0.4f)
                        } else {
                            androidx.compose.material3.LocalContentColor.current
                        }
                    )
                }
                // ▶ 预览按钮
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { previewWallpaperLayer(entry) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = style.accent, fontSize = 13.sp)
                }
                // ≡ 拖动手柄（长按拖动排序，背景/轮换/自定义层均可拖）
                RotationEntryDragHandle(
                    onMoveBy = { delta -> moveWallpaperLayerVis(entry, delta) }
                )

                // ✕ 删除（仅自定义层）
                if (!isPrefab) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = style.danger, fontSize = 13.sp)
                    }
                } else {
                    Spacer(Modifier.width(30.dp))
                }
            }
        }
    }

    @Composable
    private fun LayerAddButton(
        icon: Int,
        text: String,
        count: Int,
        style: AppDialogStyle,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = style.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${count}层",
                    color = style.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    /** 循环切换图层日夜模式：☀️仅白天 → 🌙仅黑夜 → 🌓都显示 */
    private fun setWallpaperLayerMode(index: Int) {
        val list = ReadBookConfig.durConfig.wallpaperLayerItems
        val entry = list.getOrNull(index) ?: return
        if (entry.isWallpaperPrefab()) return
        val item = WallpaperItem.fromJson(entry) ?: return
        val next = when (item.mode) {
            ReadBookConfig.ROTATION_MODE_DAY -> ReadBookConfig.ROTATION_MODE_NIGHT
            ReadBookConfig.ROTATION_MODE_NIGHT -> ReadBookConfig.ROTATION_MODE_ALL
            else -> ReadBookConfig.ROTATION_MODE_DAY
        }
        val mutable = list.toMutableList()
        mutable[index] = item.copy(mode = next).toJson()
        ReadBookConfig.durConfig.wallpaperLayerItems = normalizeLayerItems(ArrayList(mutable))
        applyWallpaperLayers()
    }

    /** 视频图层声音开关：只更新音量（不重建图层、不重启视频） */
    private fun toggleWallpaperLayerSound(index: Int) {
        val list = ReadBookConfig.durConfig.wallpaperLayerItems
        val entry = list.getOrNull(index) ?: return
        val item = WallpaperItem.fromJson(entry) ?: return
        if ((item.type != WallpaperLayerType.VIDEO &&
                item.type != WallpaperLayerType.LIVE_PHOTO) || entry.isWallpaperPrefab()) return
        val next = !item.soundOn
        val mutable = list.toMutableList()
        mutable[index] = item.copy(soundOn = next).toJson()
        ReadBookConfig.durConfig.wallpaperLayerItems = normalizeLayerItems(ArrayList(mutable))
        (activity as? ReadBookActivity)?.setLayerSound(index, next)
        refreshTick++
    }

    /** 预览图层（预置项：背景/当前轮换；图片/URL：大图；视频：信息提示） */
    private fun previewWallpaperLayer(entry: String) {
        when (entry) {
            WallpaperLayerType.PREFAB_BG -> {
                val cfg = ReadBookConfig.durConfig
                previewLayerImagePath(configBgImagePath(cfg), configBgColor(cfg), "背景图片")
            }
            WallpaperLayerType.PREFAB_ROTATION -> {
                val first = ReadBookConfig.durConfig.wallpaperRotationImageList.firstOrNull()
                if (first != null) {
                    showWallpaperPreview(first)
                } else {
                    requireContext().toastOnUi("当前无轮换壁纸")
                }
            }
            else -> {
                val item = WallpaperItem.fromJson(entry) ?: return
                if (item.type == WallpaperLayerType.VIDEO ||
                    item.type == WallpaperLayerType.LIVE_PHOTO) {
                    requireContext().toastOnUi("视频/LivePhoto（循环播放）：${item.src}")
                } else {
                    previewLayerImagePath(item.src, null, item.typeLabel())
                }
            }
        }
    }

    /** 图层大图预览（path 空则显示纯色块） */
    private fun previewLayerImagePath(path: String?, color: Int?, title: String) {
        val context = requireContext()
        val maxW = (SystemUtils.screenWidthPx * 0.92f).toInt()
        val maxH = (SystemUtils.screenHeightPx * 0.72f).toInt()
        val imageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(maxW, maxH)
        }
        if (path != null) {
            ImageLoader.load(context, path)
                .centerCrop()
                .error(R.drawable.image_loading_error)
                .into(imageView)
        } else {
            imageView.setBackgroundColor(color ?: 0xFFEEEEEE.toInt())
        }
        alert(title = title) {
            customView {
                imageView
            }
            okButton()
        }
    }

    private fun applyWallpaperLayers() {
        refreshTick++
        (activity as? ReadBookActivity)?.refreshWallpaperLayers()
    }

    /** 规整图层列表：列表第 1 行(北)=顶, 末尾(南)=底。
     *  背景图片 __bg__ 默认在第 1 行(最顶层), 轮换 __rotation__ 紧随其后；
     *  仅在缺失时补默认位置, 已有则保持用户拖动的当前位置。
     */
    private fun normalizeLayerItems(raw: ArrayList<String>): ArrayList<String> {
        val list = ArrayList(raw)
        if (WallpaperLayerType.PREFAB_BG !in list) {
            list.add(0, WallpaperLayerType.PREFAB_BG)
        }
        val rotationOn = ReadBookConfig.durConfig.wallpaperRotationEnabled
        if (rotationOn) {
            if (WallpaperLayerType.PREFAB_ROTATION !in list) {
                list.add(1, WallpaperLayerType.PREFAB_ROTATION)
            }
        } else {
            list.remove(WallpaperLayerType.PREFAB_ROTATION)
        }
        return list
    }

    /** 图层拖动：按「正在拖动的项」实时定位（拖动触发界面重组后 visIndex 会过期 → 必须按 entry 查找，避免越界崩溃） */
    private fun moveWallpaperLayerVis(entry: String, delta: Int) {
        val list = ReadBookConfig.durConfig.wallpaperLayerItems
        val prefs = requireContext().defaultSharedPreferences
        val visible = list.filter {
            it.isWallpaperPrefab() || ReadBookConfig.layerSourceEnabled(it, prefs)
        }
        if (visible.size <= 1) return
        val from = visible.indexOf(entry)
        if (from < 0) return
        val target = from + delta
        if (target !in visible.indices) return
        val mutable = list.toMutableList()
        mutable.remove(entry)
        val anchor = visible[target]
        val insertAt = mutable.indexOf(anchor)
        if (insertAt < 0) return
        mutable.add(if (target < from) insertAt else insertAt + 1, entry)
        ReadBookConfig.durConfig.wallpaperLayerItems = normalizeLayerItems(ArrayList(mutable))
        applyWallpaperLayers()
    }

    private fun removeWallpaperLayer(index: Int) {
        val list = ReadBookConfig.durConfig.wallpaperLayerItems
        val entry = list.getOrNull(index) ?: return
        if (entry.isWallpaperPrefab()) return // 预置项不可删除
        val mutable = list.toMutableList()
        mutable.removeAt(index)
        ReadBookConfig.durConfig.wallpaperLayerItems = normalizeLayerItems(ArrayList(mutable))
        applyWallpaperLayers()
    }

    /** 选择本地图片/视频文件作为壁纸图层（复制到 bg 目录持久保存） */
    private fun addWallpaperLayerFile(type: Int) {
        if (type == WallpaperLayerType.VIDEO) {
            selectLayerVideo.launch {
                mode = HandleFileContract.VIDEO
                title = getString(R.string.select_video)
            }
        } else {
            selectLayerImage.launch {
                mode = HandleFileContract.IMAGE
            }
        }
    }

    private val selectLayerImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> addWallpaperLayerFromUri(uri, WallpaperLayerType.IMAGE) }
    }

    private val selectLayerVideo = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> addWallpaperLayerFromUri(uri, WallpaperLayerType.VIDEO) }
    }

    /** 新图层默认插入「自定义层底部」（预置项之上、已有自定义层之下） */
    private fun addWallpaperLayerItem(item: WallpaperItem) {
        val list = ArrayList(ReadBookConfig.durConfig.wallpaperLayerItems)
        // 列表: 北方(第1行)=顶, 南方(末尾)=底。新壁纸恒插列表末尾 = 最底层(最南)
        list.add(item.toJson())
        ReadBookConfig.durConfig.wallpaperLayerItems = normalizeLayerItems(list)
        applyWallpaperLayers()
    }

    private fun addWallpaperLayerFromUri(uri: Uri, type: Int) {
        lifecycleScope.launch {
            try {
                val context = requireContext()
                if (type == WallpaperLayerType.VIDEO) {
                    // 视频：直接调用原文件（content:// URI），不复制。播放权限已由 HandleFileActivity 持久化
                    addWallpaperLayerItem(WallpaperItem(type, uri.toString()))
                    return@launch
                }
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bgDir = File(context.externalFiles, "bg")
                bgDir.mkdirs()
                val fileName = buildCustomWallpaperFileName(context, uri)
                val destFile = File(bgDir, fileName)
                destFile.outputStream().use { out -> inputStream.copyTo(out) }
                // LivePhoto：①伴生视频文件(苹果 HEIC+MOV / 小米部分伴生) ②单文件 Motion Photo(谷歌标准: JPG 内嵌 H.264 视频流) → LivePhoto 层(可开声音)
                val liveVideo = tryCopyLivePhotoVideo(context, uri, fileName, bgDir)
                    ?: extractMotionPhotoVideo(context, uri, fileName, bgDir)
                if (liveVideo != null) {
                    addWallpaperLayerItem(
                        WallpaperItem(WallpaperLayerType.LIVE_PHOTO, destFile.absolutePath, videoSrc = liveVideo.absolutePath)
                    )
                } else {
                    addWallpaperLayerItem(WallpaperItem(type, destFile.absolutePath))
                }
            } catch (e: Exception) {
                requireContext().toastOnUi(e.stackTraceStr)
            }
        }
    }

    /** 检测并复制 LivePhoto 伴生视频：
     *  ① 媒体库中与照片同基础名的视频（苹果 HEIC↔MOV / 通用 IMG_1234 ↔ IMG_1234.mp4，均会进入 MediaStore）
     *  ② 照片同目录伴生视频（小米动态照片等：JPG/HEIC + 同名 .mp4/.mov 未必入库，直接扫目录）
     */
    private suspend fun tryCopyLivePhotoVideo(
        context: android.content.Context,
        photoUri: android.net.Uri,
        photoFileName: String,
        bgDir: File
    ): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val base = photoFileName.substringBeforeLast('.').trim()
            if (base.isBlank()) return@runCatching null
            val videoUri = findLivePairInMediaStore(context, base)
                ?: findLivePairInSameDir(context, photoUri, base)
                ?: return@runCatching null
            val videoFile = File(bgDir, "${base}.live.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { vin ->
                videoFile.outputStream().use { out -> vin.copyTo(out) }
            }
            if (videoFile.isFile && videoFile.length() > 0) videoFile else null
        }.getOrNull()
    }

    /** 媒体库视频表中找同基础名视频（IMG_1234.MOV / IMG_1234.mp4） */
    private fun findLivePairInMediaStore(context: android.content.Context, base: String): android.net.Uri? {
        return context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME
            ),
            "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$base%"),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.substringBeforeLast('.').trim().equals(base, ignoreCase = true)) {
                    return@use android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        c.getLong(0)
                    )
                }
            }
            null
        }
    }

    /** 照片同目录扫描伴生视频（小米动态照片：JPG/HEIC 与同名 .mp4 相邻存放；媒体库可能漏索引） */
    private fun findLivePairInSameDir(
        context: android.content.Context,
        photoUri: android.net.Uri,
        base: String
    ): android.net.Uri? {
        var dataPath: String? = null
        try {
            context.contentResolver.query(
                photoUri,
                arrayOf(android.provider.MediaStore.Images.Media.DATA),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) dataPath = c.getString(0)
            }
        } catch (_: Exception) {}
        if (dataPath.isNullOrEmpty()) {
            val id = photoUri.lastPathSegment?.toLongOrNull()
            if (id != null) {
                try {
                    context.contentResolver.query(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(android.provider.MediaStore.Images.Media.DATA),
                        "_id=?",
                        arrayOf(id.toString()),
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) dataPath = c.getString(0)
                    }
                } catch (_: Exception) {}
            }
        }
        val dir = dataPath?.let { File(it).parentFile } ?: return null
        if (!dir.isDirectory) return null
        val videoExts = setOf("mp4", "mov", "hevc", "mkv", "3gp")
        val lowerBase = base.lowercase()
        val match = dir.listFiles()?.firstOrNull { f ->
            if (!f.isFile) return@firstOrNull false
            val name = f.name.lowercase()
            if (name.substringBeforeLast('.').trim() == lowerBase) {
                f.extension.lowercase() in videoExts
            } else {
                // 宽松兜底：以 base 开头且为视频（处理 IMG_1234(1).mp4 之类变体）
                name.startsWith(lowerBase) && f.extension.lowercase() in videoExts
            }
        } ?: return null
        return android.net.Uri.fromFile(match)
    }

    /** 单文件 Motion Photo（谷歌动态照片标准，vivo/OPPO/小米通用）：
     *  JPEG 中段 XMP 元数据标记 GCamera:MotionPhoto，视频流(H.264)追加在 JPEG 文件尾部 → 提取为独立 .mp4 */
    private suspend fun extractMotionPhotoVideo(
        context: android.content.Context,
        photoUri: android.net.Uri,
        photoFileName: String,
        bgDir: File
    ): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val base = photoFileName.substringBeforeLast('.').trim()
            if (base.isBlank()) return@runCatching null
            val tmp = File(context.cacheDir, "motion_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(photoUri)?.use { i ->
                tmp.outputStream().use { o -> i.copyTo(o) }
            }
            if (!tmp.isFile || tmp.length() <= 0) return@runCatching null
            val bytes = tmp.readBytes()
            tmp.delete()
            if (bytes.size > 100_000_000) return@runCatching null // 超大文件跳过（防 OOM）
            val xmpInfo = findMotionXmp(bytes) ?: return@runCatching null
            val videoStart = motionVideoStart(bytes, xmpInfo.first) ?: return@runCatching null
            val out = File(bgDir, "${base}.live.mp4")
            out.outputStream().use { o -> o.write(bytes, videoStart, bytes.size - videoStart) }
            if (out.isFile && out.length() > 0) out else null
        }.getOrNull()
    }

    /** 在 JPEG 段(APP1)中查找谷歌相机 XMP：返回 (xmp 文本, 该段结束位置) */
    private fun findMotionXmp(bytes: ByteArray): Pair<String, Int>? {
        var i = 2 // 跳过 SOI
        while (i + 4 <= bytes.size) {
            if (bytes[i] == 0xFF.toByte()) {
                val marker = bytes[i + 1].toInt() and 0xFF
                if (marker == 0xE1) { // APP1
                    if (i + 4 <= bytes.size) {
                        val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                        if (len >= 2 && i + 2 + len <= bytes.size) {
                            val seg = String(bytes, i + 4, len - 2, Charsets.UTF_8)
                            if (seg.contains("http://ns.google.com/photos/1.0/camera/") ||
                                seg.contains("GCamera") || seg.contains("MotionPhoto")
                            ) {
                                return seg to (i + 2 + len)
                            }
                            i += 2 + len
                        } else return null
                    } else return null
                } else if (marker == 0xDA) { // SOS：图像数据开始，之后不再有段
                    return null
                } else if (marker == 0x01 || (marker in 0xD0..0xD9)) {
                    i += 2
                } else {
                    if (i + 4 > bytes.size) return null
                    val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    i += 2 + len
                }
            } else i++
        }
        return null
    }

    /** 计算内嵌视频流起点：旧标准 MicroVideoOffset（尾部偏移）或新标准从 XMP 段之后的 ftyp 起 */
    private fun motionVideoStart(bytes: ByteArray, xmp: String): Int? {
        val off = Regex("MicroVideoOffset[\"']?\\s*[:=]\\s*[\"']?(\\d+)")
            .find(xmp)?.groupValues?.get(1)?.toLongOrNull()
        if (off != null && off > 0) {
            return (bytes.size - off.toInt()).coerceIn(0, bytes.size - 1)
        }
        val xmpEnd = findMotionXmp(bytes)?.second ?: 2
        var i = xmpEnd
        val end = bytes.size - 8
        while (i < end) {
            if (bytes[i] == 'f'.code.toByte() && bytes[i + 1] == 't'.code.toByte() &&
                bytes[i + 2] == 'y'.code.toByte() && bytes[i + 3] == 'p'.code.toByte()
            ) {
                // 校验 major brand 为可打印字符（isom/mp42/mp41/qt 等）
                val ok = (0 until 4).all { k ->
                    val b = bytes[i + 4 + k].toInt() and 0xFF
                    b in 0x20..0x7E
                }
                if (ok) return i
            }
            i++
        }
        return null
    }

    /** URL 壁纸：直链 / 解析 二选一添加 */
    private fun showAddWallpaperLayerUrlDialog() {
        val context = requireContext()
        val editText = android.widget.EditText(context).apply {
            hint = "https://example.com/image.jpg"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val container = android.widget.FrameLayout(context).apply {
            val pad = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(
                editText,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        alert("添加URL壁纸") {
            customView { container }
            neutralButton("直链") {
                val url = editText.text?.toString()?.trim().orEmpty()
                if (url.isNotBlank()) addWallpaperLayerUrl(url, WallpaperLayerType.URL_IMAGE)
            }
            positiveButton("解析") {
                val url = editText.text?.toString()?.trim().orEmpty()
                if (url.isNotBlank()) addWallpaperLayerUrl(url, WallpaperLayerType.URL_RESOLVE)
            }
        }
    }

    private fun addWallpaperLayerUrl(url: String, type: Int) {
        addWallpaperLayerItem(WallpaperItem(type, url))
    }

    @Composable
    private fun PagOverlaySection(style: AppDialogStyle) {
        var pagEnabled by rememberSaveable(refreshTick) {
            mutableStateOf(ReadBookConfig.durConfig.pagOverlayEnabled)
        }
        var pagPath by rememberSaveable(refreshTick) {
            mutableStateOf(ReadBookConfig.durConfig.pagOverlayPath)
        }
        ReaderSwitchRow(
            title = stringResource(R.string.pag_overlay),
            checked = pagEnabled,
            style = style,
            summary = if (pagPath.isNotBlank()) pagPath.substringAfterLast("/") else null
        ) {
            pagEnabled = it
            ReadBookConfig.durConfig.pagOverlayEnabled = it
            if (!it) {
                (activity as? ReadBookActivity)?.refreshPagOverlay()
            }
            postReadConfigChanged(10)
        }
        if (pagEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectPagFileAction() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_pag_file),
                    color = style.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pagPath.substringAfterLast("/").ifBlank { "未选择" },
                    color = style.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }

    @Composable
    private fun UnderlineSection(style: AppDialogStyle) {
        ReaderSectionCard(
            style = style,
            title = stringResource(R.string.text_underline),
            contentPadding = PaddingValues(8.dp)
        ) {
            var underlineMode by rememberSaveable(refreshTick) {
                mutableIntStateOf(ReadBookConfig.durConfig.underlineMode)
            }
            ReaderSegmentedOptions(
                options = listOf(
                    ReaderOption("0", "关闭"),
                    ReaderOption("1", "实线"),
                    ReaderOption("2", "虚线")
                ),
                selectedValue = underlineMode.toString(),
                style = style,
                pillStyle = true
            ) { value ->
                val next = value.toIntOrNull() ?: return@ReaderSegmentedOptions
                if (next == underlineMode) return@ReaderSegmentedOptions
                underlineMode = next
                ReadBookConfig.durConfig.underlineMode = next
                postReadConfigChanged(6, 9, 11)
            }
        }
    }

    @Composable
    private fun ColorSection(style: AppDialogStyle) {
        ReaderSectionCard(style = style, title = null, contentPadding = PaddingValues(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ColorAction(
                    text = stringResource(R.string.text_color),
                    color = Color(ReadBookConfig.durConfig.curTextColor()),
                    style = style,
                    modifier = Modifier.weight(1f)
                ) { showTextColorPicker() }
                ColorAction(
                    text = stringResource(R.string.bg_color),
                    color = currentBackgroundSwatch(),
                    style = style,
                    modifier = Modifier.weight(1f)
                ) { showBgColorPicker() }
                ColorAction(
                    text = stringResource(R.string.text_accent_color),
                    color = Color(ReadBookConfig.durConfig.curTextAccentColor()),
                    style = style,
                    modifier = Modifier.weight(1f)
                ) { showTextAccentColorPicker() }
            }
        }
    }

    @Composable
    private fun ColorAction(
        text: String,
        color: Color,
        style: AppDialogStyle,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = modifier
                .heightIn(min = 42.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = text,
                    color = style.primaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun ImportExportSection(style: AppDialogStyle) {
        ReaderSectionCard(style = style, title = null, contentPadding = PaddingValues(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionButton(
                    text = stringResource(R.string.import_str),
                    iconRes = R.drawable.ic_import,
                    style = style,
                    modifier = Modifier.weight(1f),
                    onClick = ::launchImport
                )
                SmallActionButton(
                    text = stringResource(R.string.export_str),
                    iconRes = R.drawable.ic_export,
                    style = style,
                    modifier = Modifier.weight(1f),
                    onClick = ::launchExport
                )
                SmallActionButton(
                    text = stringResource(R.string.delete),
                    iconRes = R.drawable.ic_clear_all,
                    style = style,
                    modifier = Modifier.weight(1f),
                    danger = true,
                    onClick = ::deleteCurrentConfig
                )
            }
        }
    }

    @Composable
    private fun SmallActionButton(
        text: String,
        iconRes: Int,
        style: AppDialogStyle,
        modifier: Modifier = Modifier,
        danger: Boolean = false,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = modifier
                .heightIn(min = 42.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(style.actionRadius),
            color = if (danger) style.danger.copy(alpha = 0.11f) else style.fieldSurface,
            contentColor = if (danger) style.danger else style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = text,
                    tint = if (danger) style.danger else style.primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    color = if (danger) style.danger else style.primaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun SliderSection(style: AppDialogStyle) {
        ReaderSectionCard(style = style, title = null, contentPadding = PaddingValues(8.dp)) {
            var bgAlpha by rememberSaveable(refreshTick) { mutableIntStateOf(ReadBookConfig.bgAlpha) }
            var textShadow by rememberSaveable(refreshTick) { mutableIntStateOf(ReadBookConfig.paperInkStrength) }
            SliderRow(
                title = stringResource(R.string.bg_alpha),
                value = bgAlpha,
                range = 0..100,
                style = style
            ) {
                bgAlpha = it
                ReadBookConfig.bgAlpha = it
                postReadConfigChanged(3)
            }
            SliderRow(
                title = stringResource(R.string.text_shadow),
                value = textShadow,
                range = 0..100,
                style = style,
                valueText = if (textShadow == 0) stringResource(R.string.jf_convert_o) else "$textShadow%"
            ) {
                textShadow = it
                ReadBookConfig.paperInkStrength = it
                postReadConfigChanged(2, 9, 6)
            }
        }
    }

    @Composable
    private fun SliderRow(
        title: String,
        value: Int,
        range: IntRange,
        style: AppDialogStyle,
        valueText: String = "$value%",
        onValueChange: (Int) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = style.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = valueText,
                    color = style.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            AppThemedStepperSlider(
                value = value,
                range = range,
                onValueChange = onValueChange,
                palette = style.toMiuixPalette(),
                trackHeight = 32.dp,
                thumbSize = 24.dp,
                endpointWidth = 28.dp
            )
        }
    }

    @Composable
    private fun BackgroundImageSection(style: AppDialogStyle) {
        ReaderSectionCard(
            style = style,
            title = stringResource(R.string.bg_image),
            contentPadding = PaddingValues(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "select_image") {
                    SelectImageTile(style)
                }
                items(
                    items = presetBgImages,
                    key = { it }
                ) { imageName ->
                    PresetBackgroundTile(imageName = imageName, style = style)
                }
            }
        }
    }

    @Composable
    private fun SelectImageTile(style: AppDialogStyle) {
        Surface(
            modifier = Modifier
                .width(82.dp)
                .height(82.dp)
                .clickable {
                    selectBgImage.launch {
                        mode = HandleFileContract.IMAGE
                    }
                },
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = stringResource(R.string.select_image),
                    tint = style.primaryText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.select_image),
                    color = style.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun PresetBackgroundTile(imageName: String, style: AppDialogStyle) {
        Surface(
            modifier = Modifier
                .width(82.dp)
                .height(82.dp)
                .clickable {
                    ReadBookConfig.durConfig.setCurBg(1, imageName)
                    postReadConfigChanged(1)
                    refreshTick++
                },
            shape = RoundedCornerShape(style.actionRadius),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = style.actionRadius, topEnd = style.actionRadius))
                        .background(style.surface)
                ) {
                    PresetBackgroundPreview(
                        imageName = imageName,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = imageName.substringBeforeLast("."),
                    color = style.secondaryText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }

    @Composable
    private fun PresetBackgroundPreview(imageName: String, modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                AppCompatImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            update = { imageView ->
                ImageLoader.load(imageView.context, "file:///android_asset/bg/$imageName")
                    .centerCrop()
                    .into(imageView)
            },
            onRelease = { it.releaseComposeImage() }
        )
    }

    private fun editStyleName() {
        showComposeTextInputDialog(
            title = getString(R.string.style_name),
            hint = "name",
            initialValue = ReadBookConfig.durConfig.name,
            onPositive = {
                ReadBookConfig.durConfig.name = it
                refreshTick++
            }
        )
    }

    private fun restorePreset() {
        val defaultConfigs = DefaultData.readConfigs
        val layoutNames = defaultConfigs.map { it.name }
        showComposeChoiceListDialog("选择预设布局", layoutNames) { i ->
            if (i >= 0) {
                ReadBookConfig.durConfig = defaultConfigs[i].copy()
                refreshTick++
                postReadConfigChanged(1, 2, 5)
            }
        }
    }

    private fun showTextColorPicker() {
        ColorPickerDialog.newBuilder()
            .setColor(ReadBookConfig.durConfig.curTextColor())
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(TEXT_COLOR)
            .show(requireActivity())
    }

    private fun showTextAccentColorPicker() {
        ColorPickerDialog.newBuilder()
            .setColor(ReadBookConfig.durConfig.curTextAccentColor())
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(TEXT_ACCENT_COLOR)
            .show(requireActivity())
    }

    private fun showBgColorPicker() {
        val bgColor =
            if (ReadBookConfig.durConfig.curBgType() == 0) ReadBookConfig.durConfig.curBgStr().toColorInt()
            else "#015A86".toColorInt()
        ColorPickerDialog.newBuilder()
            .setColor(bgColor)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(BG_COLOR)
            .show(requireActivity())
    }

    private fun currentBackgroundSwatch(): Color {
        return if (ReadBookConfig.durConfig.curBgType() == 0) {
            runCatching { Color(ReadBookConfig.durConfig.curBgStr().toColorInt()) }
                .getOrDefault(Color(0xFF015A86))
        } else {
            Color(0xFF015A86)
        }
    }


    private val selectPagFile = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> setPagFromUri(uri) }
    }

    private fun selectPagFileAction() {
        selectPagFile.launch {
            mode = HandleFileContract.PAG
        }
    }

    private fun setPagFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@launch
                val pagDir = File(requireContext().externalFiles, "pag")
                pagDir.mkdirs()
                // 先写入临时文件，读取 PAG 内置名称后再定名
                val tempFile = File(pagDir, "tmp_${System.currentTimeMillis()}.pag")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                // 优先 PAG 文件内置名称（根合成层名），其次原始文件名
                var fileName = runCatching {
                    org.libpag.PAGFile.Load(tempFile.absolutePath)?.layerName()?.trim()
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: (queryDisplayName(uri) ?: uri.lastPathSegment)
                    ?: "${System.currentTimeMillis()}.pag"
                fileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .ifBlank { "${System.currentTimeMillis()}.pag" }
                var destFile = File(pagDir, fileName)
                if (destFile.exists()) {
                    // 重名加时间戳后缀
                    val base = fileName.substringBeforeLast('.', fileName)
                    val ext = fileName.substringAfterLast('.', "pag")
                    destFile = File(pagDir, "${base}_${System.currentTimeMillis()}.$ext")
                }
                tempFile.renameTo(destFile)
                ReadBookConfig.durConfig.pagOverlayPath = destFile.absolutePath
                refreshTick++
                (activity as? ReadBookActivity)?.refreshPagOverlay()
            } catch (e: Exception) {
                requireContext().toastOnUi(e.stackTraceStr)
            }
        }
    }

    /** 查询 content URI 的显示名称 */
    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            requireContext().contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun postReadConfigChanged(vararg configKeys: Int) {
        pendingSelfConfigEvents++
        postEvent(EventBus.UP_CONFIG, arrayListOf(*configKeys.toTypedArray()))
    }

    private fun launchImport() {
        selectImportDoc.launch {
            mode = HandleFileContract.FILE
            title = getString(R.string.import_str)
            allowExtensions = arrayOf("zip")
            otherActions = arrayListOf(SelectItem(importFormNet, -1))
        }
    }

    private fun launchExport() {
        selectExportDir.launch {
            title = getString(R.string.export_str)
        }
    }

    private fun deleteCurrentConfig() {
        if (ReadBookConfig.deleteDur()) {
            postReadConfigChanged(1, 2, 5)
            dismissAllowingStateLoss()
        } else {
            toastOnUi("数量已是最少, 不能删除.")
        }
    }

    private fun exportConfig(uri: Uri) {
        val exportFileName = if (ReadBookConfig.config.name.isBlank()) {
            configFileName
        } else {
            "${ReadBookConfig.config.name}.zip"
        }
        execute {
            val exportFiles = arrayListOf<File>()
            val configDir = requireContext().externalCache.getFile("readConfig")
            configDir.createFolderReplace()
            val configFile = configDir.getFile("readConfig.json")
            configFile.createFileReplace()
            val config = ReadBookConfig.getExportConfig()
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontDoc = FileDoc.fromFile(fontPath)
                val fontName = fontDoc.name
                val fontInputStream = fontDoc.openInputStream().getOrNull()
                fontInputStream?.use {
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    fontExportFile.outputStream().use { out ->
                        it.copyTo(out)
                    }
                    config.textFont = fontName
                    exportFiles.add(fontExportFile)
                }
            }
            configFile.writeText(GSON.toJson(config))
            exportFiles.add(configFile)
            repeat(3) {
                val path = ReadBookConfig.durConfig.getBgPath(it) ?: return@repeat
                val bgExportFile = copyBgImage(path, configDir) ?: return@repeat
                exportFiles.add(bgExportFile)
            }
            val configZipPath = FileUtils.getPath(requireContext().externalCache, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                val exportDir = FileDoc.fromDir(uri)
                exportDir.find(exportFileName)?.delete()
                val exportFileDoc = exportDir.createFileIfNotExist(exportFileName)
                exportFileDoc.openOutputStream().getOrThrow().use { out ->
                    File(configZipPath).inputStream().use {
                        it.copyTo(out)
                    }
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printOnDebug()
            AppLog.put("导出失败:${it.localizedMessage}", it)
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    private fun copyBgImage(path: String, configDir: File): File? {
        val bgName = FileUtils.getName(path)
        val bgFile = File(path)
        if (bgFile.exists()) {
            val bgExportFile = File(FileUtils.getPath(configDir, bgName))
            if (!bgExportFile.exists()) {
                bgFile.copyTo(bgExportFile)
                return bgExportFile
            }
        }
        return null
    }

    private fun importNetConfigAlert() {
        showComposeTextInputDialog(
            title = "输入地址",
            onPositive = { url ->
                if (url.isNotBlank()) {
                    importNetConfig(url)
                }
            }
        )
    }

    private fun importNetConfig(url: String) {
        execute {
            okHttpClient.newCallResponseBody {
                url(url)
            }.bytes().let {
                importConfig(it)
            }
        }.onError {
            longToast(it.stackTraceStr)
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            importConfig(uri.readBytes(requireContext()))
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun importConfig(byteArray: ByteArray) {
        execute {
            ReadBookConfig.import(byteArray)
        }.onSuccess {
            ReadBookConfig.durConfig = it
            refreshTick++
            postReadConfigChanged(1, 2, 5)
            toastOnUi("导入成功")
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun setBgFromUri(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            lifecycleScope.launch {
                kotlin.runCatching {
                    appCtx.toastOnUi("下载图片中...")
                    val analyzeUrl = AnalyzeUrl(uri.toString())
                    val url = analyzeUrl.urlNoQuery
                    var file = requireContext().externalFiles
                    val res = okHttpClient.newCallResponse(0) {
                        addHeaders(analyzeUrl.headerMap)
                        url(url)
                    }
                    val contentType = res.header("Content-Type") ?: "image/jpeg"
                    val imageType = when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val suffix = if (url.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        ".$imageType"
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    ReadBookConfig.durConfig.setCurBg(2, fileName)
                    postReadConfigChanged(1)
                    refreshTick++
                }.onSuccess {
                    appCtx.toastOnUi("设定成功")
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
                postReadConfigChanged(1)
                refreshTick++
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
