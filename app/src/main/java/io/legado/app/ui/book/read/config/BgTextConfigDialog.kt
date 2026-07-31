package io.legado.app.ui.book.read.config

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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
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
import io.legado.app.ui.file.HandleFileContract
import androidx.compose.runtime.MutableState
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.AppThemedStepperSlider
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.showComposeChoiceListDialog
import io.legado.app.ui.widget.compose.showComposeMultiChoiceDialog
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
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

            // --- 三种壁纸来源按钮 ---
            RotationSourceButton(
                icon = R.drawable.ic_image,
                text = "选择自定义壁纸",
                count = entries.count { it.startsWith("custom:") },
                style = style,
                onClick = { addCustomWallpaper(entries) { entries = it } }
            )
            RotationSourceButton(
                icon = R.drawable.ic_arrange,
                text = "添加样式壁纸",
                count = entries.count { it.startsWith("style:") },
                style = style,
                onClick = { addStyleWallpaper(entries) { entries = it } }
            )
            RotationSourceButton(
                icon = R.drawable.ic_cfg_theme,
                text = "选择内置壁纸",
                count = entries.count { it.startsWith("asset:") || !it.contains(":") },
                total = presetImages.size,
                style = style,
                onClick = { showBuiltinWallpaperDialog(presetImages, entries) { entries = it } }
            )
            RotationSourceButton(
                icon = R.drawable.ic_play_outline_24dp,
                text = "选择PAG主题",
                count = entries.count { it.startsWith("pagtheme:") },
                style = style,
                onClick = { selectPagThemeRoot() }
            )

            // --- 当前轮换列表 ---
            if (entries.isNotEmpty()) {
                RotationEntryList(entries, style) { entries = it }
            }
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
                text = "当前轮换列表（点击或 ▶ 预览，✕ 移除）",
                color = style.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
            entries.forEachIndexed { index, entry ->
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

    private fun rotationEntryLabel(entry: String): String {
        return when {
            entry.startsWith("custom:") -> {
                val name = entry.removePrefix("custom:").substringAfterLast("/")
                "📁 $name"
            }
            entry.startsWith("style:") -> {
                val idx = entry.removePrefix("style:").toIntOrNull() ?: -1
                val name = ReadBookConfig.getConfig(idx).name.ifBlank { "样式$idx" }
                "🎨 $name"
            }
            entry.startsWith("pagtheme:") -> {
                "🎞️ ${entry.removePrefix("pagtheme:").substringAfterLast("/")}"
            }
            else -> {
                val name = entry.removePrefix("asset:").substringBeforeLast(".")
                "🖼️ $name"
            }
        }
    }

    /** 解析轮换条目对应的预览路径（背景图）；纯色返回 null */
    private fun rotationEntryImagePath(entry: String): String? {
        return when {
            entry.startsWith("custom:") -> entry.removePrefix("custom:")
            entry.startsWith("style:") -> {
                configBgImagePath(
                    ReadBookConfig.getConfig(entry.removePrefix("style:").toIntOrNull() ?: 0)
                )
            }
            entry.startsWith("pagtheme:") -> {
                themeBackground(File(entry.removePrefix("pagtheme:")))?.absolutePath
            }
            else -> {
                val name = entry.removePrefix("asset:")
                "file:///android_asset/bg/$name"
            }
        }
    }

    /** 统一条目预览：PAG主题→动画预览，样式→完整预览（背景+PAG+文字），其余→大图预览 */
    private fun previewRotationEntry(entry: String) {
        when {
            entry.startsWith("pagtheme:") -> {
                pagThemePreview(File(entry.removePrefix("pagtheme:")))
            }
            entry.startsWith("style:") -> {
                stylePreview(
                    ReadBookConfig.getConfig(entry.removePrefix("style:").toIntOrNull() ?: 0)
                )
            }
            else -> showWallpaperPreview(entry)
        }
    }

    /** 样式完整预览：背景图（缩略图同链路）+ PAG动画（如启用）+ 示例文字（样式当前文字色） */
    private fun stylePreview(config: ReadBookConfig.Config) {
        val context = requireContext()
        val container = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                320.dpToPx()
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
            ImageLoader.load(context, bgPath).centerCrop().into(bgView)
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
        val imageView = AppCompatImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300.dpToPx()
            )
        }
        val loadPath = rotationEntryImagePath(entry)
        if (loadPath != null) {
            ImageLoader.load(requireContext(), loadPath).centerCrop().into(imageView)
        } else {
            val color = if (entry.startsWith("style:")) {
                configBgColor(
                    ReadBookConfig.getConfig(entry.removePrefix("style:").toIntOrNull() ?: 0)
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
            val loadPath = when {
                entry.startsWith("custom:") -> entry.removePrefix("custom:")
                entry.startsWith("pagtheme:") -> {
                    themeBackground(File(entry.removePrefix("pagtheme:")))?.absolutePath
                }
                entry.startsWith("asset:") || !entry.contains(":") -> {
                    val name = entry.removePrefix("asset:")
                    "file:///android_asset/bg/$name"
                }
                entry.startsWith("style:") -> {
                    configBgImagePath(
                        ReadBookConfig.getConfig(entry.removePrefix("style:").toIntOrNull() ?: 0)
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
                        entry.startsWith("style:") -> {
                            configBgColor(
                                ReadBookConfig.getConfig(entry.removePrefix("style:").toIntOrNull() ?: 0)
                            )
                        }
                        else -> 0xFFEEEEEE.toInt()
                    }
                }.getOrDefault(0xFFEEEEEE.toInt())
                Box(modifier = Modifier.fillMaxSize().background(Color(color)))
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

    private fun addCustomWallpaperFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@launch
                val bgDir = File(requireContext().externalFiles, "bg")
                bgDir.mkdirs()
                val fileName = "custom_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "wallpaper"}"
                val destFile = File(bgDir, fileName)
                destFile.outputStream().use { out -> inputStream.copyTo(out) }
                val entry = "custom:${destFile.absolutePath}"
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
            if (it.startsWith("style:")) it.removePrefix("style:").toIntOrNull() else null
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
                val mutable = list.filter { !it.startsWith("style:") }.toMutableList()
                newSelected.forEach { i -> mutable.add("style:$i") }
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
            val bg = themeBackground(dir)
            if (bg != null) "image:${bg.absolutePath}" else "color:#EEEEEE"
        }
        val currentEntries = entries.mapNotNull {
            if (it.startsWith("pagtheme:")) it.removePrefix("pagtheme:") else null
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
                val mutable = entries.filter { !it.startsWith("pagtheme:") }.toMutableList()
                newSelected.forEach { i -> mutable.add("pagtheme:${themeDirs[i].absolutePath}") }
                val result = ArrayList(mutable)
                onChanged(result)
                ReadBookConfig.durConfig.wallpaperRotationImageList = result
                postReadConfigChanged(9)
            }
        )
    }

    private fun themeBackground(dir: File): File? =
        dir.listFiles { it.isFile && it.extension.equals("jpg", true) }
            ?.minByOrNull { it.name }

    private fun themePagFile(dir: File): File? =
        dir.listFiles { it.isFile && it.extension.equals("pag", true) }
            ?.minByOrNull { it.name }

    /** 预览 PAG 主题：背景图 + PAG 动画 + 示例文字（theme.json 字体色） */
    private fun pagThemePreview(dir: File) {
        val context = requireContext()
        val container = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                320.dpToPx()
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
            ImageLoader.load(context, bg.absolutePath).centerCrop().into(bgView)
        } else {
            bgView.setBackgroundColor(0xFFEEEEEE.toInt())
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
        alert(title = dir.name, message = "背景图 + PAG动画 + 字体颜色") {
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
            if (it.startsWith("asset:")) it.removePrefix("asset:")
            else if (!it.contains(":")) it
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
                    val assetName = if (e.startsWith("asset:")) e.removePrefix("asset:")
                        else if (!e.contains(":")) e else null
                    assetName == null || assetName !in currentAssetEntries
                }.toMutableList()
                newAssets.forEach { name -> mutable.add("asset:$name") }
                val result = ArrayList(mutable)
                onChanged(result)
                ReadBookConfig.durConfig.wallpaperRotationImageList = result
                postReadConfigChanged(9)
            },
            onDismissAction = { refreshTick++ }
        )
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
