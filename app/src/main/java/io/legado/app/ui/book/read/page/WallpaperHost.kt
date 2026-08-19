package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import android.media.MediaMetadataRetriever
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.SystemUtils
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.http.getProxyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject

/**
 * 壁纸图层类型
 */
object WallpaperLayerType {
    const val IMAGE = 0        // 本地图片文件（含 PNG 透明镂空）
    const val VIDEO = 1        // 视频：本地文件路径或 http(s) 视频 URL
    const val URL_IMAGE = 2    // URL 图片（直链加载）
    const val URL_RESOLVE = 3  // URL 图片（解析：HTTP 请求后解码）

    // 预置图层标记（存储在图层列表字符串中，非 JSON）
    const val PREFAB_BG = "__bg__"            // Legado 原有背景图片项
    const val PREFAB_ROTATION = "__rotation__" // 轮换壁纸项（开启轮换时显示）
}

fun String.isWallpaperPrefab(): Boolean =
    this == WallpaperLayerType.PREFAB_BG || this == WallpaperLayerType.PREFAB_ROTATION

/**
 * 单个壁纸图层配置
 */
data class WallpaperItem(
    val type: Int,
    val src: String,
    val alpha: Int = 255,
    /** 显示模式：A=都可用(默认) / D=仅白天 / N=仅黑夜（与轮换条目模式一致） */
    val mode: String = ReadBookConfig.ROTATION_MODE_ALL,
    /** 视频壁纸是否出声（默认静音） */
    val soundOn: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("type", type)
        .put("src", src)
        .put("alpha", alpha)
        .put("mode", mode)
        .put("soundOn", soundOn)
        .toString()

    /** 当前日夜模式是否显示该图层 */
    fun visibleInCurrentMode(): Boolean {
        if (mode == ReadBookConfig.ROTATION_MODE_ALL) return true
        val isNight = AppConfig.isNightTheme
        return if (isNight) mode == ReadBookConfig.ROTATION_MODE_NIGHT
        else mode == ReadBookConfig.ROTATION_MODE_DAY
    }

    fun typeLabel(): String = when (type) {
        WallpaperLayerType.IMAGE -> "图片"
        WallpaperLayerType.VIDEO -> "视频"
        WallpaperLayerType.URL_IMAGE -> "URL图片"
        WallpaperLayerType.URL_RESOLVE -> "解析URL"
        else -> "未知"
    }

    companion object {
        fun fromJson(s: String): WallpaperItem? = runCatching {
            val j = JSONObject(s)
            WallpaperItem(
                type = j.optInt("type", 0),
                src = j.optString("src", ""),
                alpha = j.optInt("alpha", 255),
                mode = j.optString("mode", ReadBookConfig.ROTATION_MODE_ALL),
                soundOn = j.optBoolean("soundOn", false)
            )
        }.getOrNull()
    }
}

/**
 * 壁纸图层容器：作为阅读页背景的叠放层（index 0 最底层）。
 * 用于多图层叠加，例如底层视频 + 上层 PNG 镂空窗户，制造动态风景效果。
 */
class WallpaperHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val layers = mutableListOf<LayerView>()
    private val loadJobs = mutableListOf<Deferred<*>>()

    init {
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        setWillNotDraw(true)
    }

    private var firstLayoutDone = false

    /** 首次布局完成后重建预置层一次（布局尺寸 0 时 buildBgDrawable 会退化为纯色）；
     *  后续布局变化（insets/方向）不再重建，避免视频层反复重启导致画面抖动 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && !firstLayoutDone && width > 0 && height > 0) {
            firstLayoutDone = true
            refreshBgLayer()
            refreshRotationLayer()
        }
    }

    /** 重建图层（按 items 顺序从底到顶叠放；含预置项 __bg__ / __rotation__） */
    fun setLayers(items: List<String>) {
        clearLayers()
        removeAllViews()
        // 列表底部 = 最底层：从底部项开始逐个追加到最上。
        // layers 保持与 items 同序（add(0, ...)），UI index ↔ setLayerSound(index) 一致
        items.reversed().forEach { entry ->
            val layer = createLayer(entry) ?: return@forEach
            layers.add(0, layer)
            addView(layer.view, childCount)
            layer.load()
        }
    }

    fun hasLayers(): Boolean = layers.isNotEmpty()

    fun isEmpty(): Boolean = layers.isEmpty()

    /** 背景图片预置层刷新（样式/日夜切换后调用） */
    fun refreshBgLayer() {
        layers.filterIsInstance<BgPrefabLayer>().forEach { it.load() }
    }

    /** 图层视频声音开关（index 与 layers 顺序对应，即时生效） */
    fun setLayerSound(index: Int, soundOn: Boolean) {
        val layer = layers.getOrNull(index)
        if (layer is VideoLayerView) layer.setSound(soundOn)
    }

    /** 轮换壁纸预置层刷新（轮换切换后调用） */
    fun refreshRotationLayer() {
        layers.filterIsInstance<RotationPrefabLayer>().forEach { it.load() }
    }

    /** 阅读页 onStart：恢复播放所有视频层 */
    fun onActivityStart() {
        layers.forEach { it.start() }
    }

    /** 阅读页 onStop：暂停所有视频层（保留进度） */
    fun onActivityStop() {
        layers.forEach { it.pause() }
    }

    /** 阅读页 onDestroy：释放所有资源 */
    fun onDestroy() {
        loadJobs.forEach { it.cancel() }
        loadJobs.clear()
        clearLayers()
        removeAllViews()
        layers.clear()
    }

    private fun clearLayers() {
        layers.forEach { it.release() }
        layers.clear()
        loadJobs.forEach { it.cancel() }
        loadJobs.clear()
    }

    private fun createLayer(entry: String): LayerView? {
        return when (entry) {
            WallpaperLayerType.PREFAB_BG -> BgPrefabLayer(context)
            WallpaperLayerType.PREFAB_ROTATION -> RotationPrefabLayer(context)
            else -> {
                val item = WallpaperItem.fromJson(entry) ?: return null
                // 日夜模式过滤：当前模式不显示则跳过该层
                if (!item.visibleInCurrentMode()) return null
                when (item.type) {
                    WallpaperLayerType.VIDEO -> VideoLayerView(context, item)
                    else -> ImageLayerView(context, item)
                }
            }
        }
    }

    private interface LayerView {
        val view: android.view.View
        fun load()
        fun start() {}
        fun pause() {}
        fun release() {}
    }

    // ===== 预置：Legado 原有背景图片 =====
    private inner class BgPrefabLayer(context: Context) : LayerView {
        override val view: AppCompatImageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = false
            isFocusable = false
        }

        override fun load() {
            val w = view.width.coerceAtLeast(SystemUtils.screenWidthPx)
            val h = view.height.coerceAtLeast(SystemUtils.screenHeightPx)
            val d = runCatching {
                ReadBookConfig.durConfig.buildBgDrawable(
                    w, h,
                    ReadBookConfig.durConfig.curBgType(),
                    ReadBookConfig.durConfig.curBgStr()
                )
            }.getOrNull()
            view.setImageDrawable(d)
        }

        override fun release() {
            view.setImageDrawable(null)
        }
    }

    // ===== 预置：轮换壁纸（当前轮换条目，跟随轮换 Job 切换刷新） =====
    private inner class RotationPrefabLayer(context: Context) : LayerView {
        override val view: FrameLayout = FrameLayout(context).apply {
            isClickable = false
            isFocusable = false
        }
        private var imageView: AppCompatImageView? = null
        private var videoLayer: VideoLayerView? = null
        private var loadedEntry: String? = null

        override fun load() {
            val cfg = ReadBookConfig
            // 读当前生效的轮换纯条目（applyRotationEntry 写入）
            val entry = cfg.rotationCurrentEntry
            if (entry == null || !cfg.durConfig.wallpaperLayersEnabled) {
                releaseContent()
                loadedEntry = entry
                return
            }
            if (entry == loadedEntry) return
            loadedEntry = entry
            releaseContent()
            if (entry.startsWith("video:")) {
                renderVideoEntry(entry.removePrefix("video:"))
            } else {
                renderImageEntry(entry)
            }
        }

        private fun renderVideoEntry(path: String) {
            val item = WallpaperItem(WallpaperLayerType.VIDEO, path)
            val vl = VideoLayerView(view.context, item)
            view.addView(vl.view, 0)
            videoLayer = vl
            vl.load()
            vl.start()
        }

        private fun renderImageEntry(entry: String?) {
            val path = rotationEntryPath(entry)
            if (path != null) {
                val iv = AppCompatImageView(view.context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    isClickable = false
                    isFocusable = false
                }
                view.addView(iv, 0)
                imageView = iv
                ImageLoader.load(iv.context, path).centerCrop().into(iv)
            }
        }

        /** 轮换条目 → 可直接加载的路径（video 已剪掉；http 直用；custom/asset 转路径；style 仅支持网络图） */
        private fun rotationEntryPath(entry: String?): String? {
            if (entry == null || entry.startsWith("video:")) return null
            return when {
                entry.startsWith("custom:") -> entry.removePrefix("custom:")
                entry.startsWith("http") -> entry
                entry.startsWith("style:") -> {
                    val styleIdx = entry.removePrefix("style:").toIntOrNull() ?: 0
                    val cfg = ReadBookConfig.getConfig(styleIdx)
                    if (cfg.curBgStr().startsWith("http")) cfg.curBgStr() else null
                }
                else -> "file:///android_asset/bg/${entry.removePrefix("asset:")}"
            }
        }

        private fun releaseContent() {
            videoLayer?.release()
            view.removeAllViews()
            videoLayer = null
            imageView = null
        }

        override fun start() {
            videoLayer?.start()
        }

        override fun pause() {
            videoLayer?.pause()
        }

        override fun release() {
            releaseContent()
        }
    }

    // ===== 图片图层 =====
    private inner class ImageLayerView(
        context: Context,
        private val item: WallpaperItem
    ) : LayerView {
        override val view: AppCompatImageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = false
            isFocusable = false
            alpha = item.alpha / 255f
        }

        override fun load() {
            when (item.type) {
                WallpaperLayerType.URL_RESOLVE -> loadResolvedUrl()
                else -> ImageLoader.load(view.context, item.src)
                    .centerCrop()
                    .into(view)
            }
        }

        /** 解析 URL：先 HTTP 请求（携带 UA），成功后按字节解码为 Bitmap 展示 */
        private fun loadResolvedUrl() {
            val job = scope.async(Dispatchers.IO) {
                runCatching {
                    val client = getProxyClient()
                    val req = Request.Builder()
                        .url(item.src)
                        .header("User-Agent", AppConfig.userAgent)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        resp.body?.bytes()
                    }
                }.getOrNull()
            }.also { loadJobs.add(it) }
            scope.launch {
                val bytes = job.await()
                loadJobs.remove(job)
                if (bytes == null) {
                    // 解析失败回退直链加载
                    ImageLoader.load(view.context, item.src).centerCrop().into(view)
                } else {
                    val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                    if (bmp != null) {
                        view.setImageBitmap(bmp)
                    } else {
                        ImageLoader.load(view.context, item.src).centerCrop().into(view)
                    }
                }
            }
        }

        override fun release() {
            runCatching {
                com.bumptech.glide.Glide.with(view.context.applicationContext).clear(view)
            }
        }
    }

    // ===== 视频图层 =====
    private inner class VideoLayerView(
        context: Context,
        private val item: WallpaperItem
    ) : LayerView, TextureView.SurfaceTextureListener {
        var soundOn: Boolean = item.soundOn
            private set

        /** 切换视频声音（即时生效；不重建视图） */
        fun setSound(on: Boolean) {
            soundOn = on
            mediaPlayer?.setVolume(if (on) 1f else 0f, if (on) 1f else 0f)
        }

        private var mediaPlayer: MediaPlayer? = null
        private var surfaceReady = false
        private var prepared = false
        private var paused = false

        override val view: TextureView = TextureView(context).apply {
            isClickable = false
            isFocusable = false
            alpha = item.alpha / 255f
            surfaceTextureListener = this@VideoLayerView
        }

        override fun load() {
            // 等待 surface 可用后创建播放器
            if (view.isAvailable) {
                surfaceReady = true
                ensurePlayer()
            }
        }

        override fun start() {
            val mp = mediaPlayer ?: run {
                if (view.isAvailable) { surfaceReady = true; ensurePlayer() }
                return
            }
            paused = false
            if (prepared && !mp.isPlaying) mp.start()
        }

        override fun pause() {
            paused = true
            mediaPlayer?.let { if (it.isPlaying) it.pause() }
        }

        override fun release() {
            releasePlayer()
        }

        private fun releasePlayer() {
            runCatching {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
            }
            mediaPlayer = null
            prepared = false
        }

        private fun ensurePlayer() {
            if (mediaPlayer != null || !surfaceReady) return
            val mp = runCatching {
                MediaPlayer().apply {
                    setSurface(Surface(view.surfaceTexture!!))
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    isLooping = true
                    setVolume(if (soundOn) 1f else 0f, if (soundOn) 1f else 0f)
                    setOnPreparedListener {
                        prepared = true
                        applyCover()
                        if (!paused) it.start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                    setOnVideoSizeChangedListener { _, _, _ -> applyCover() }
                    if (item.src.startsWith("content://")) {
                        setDataSource(view.context, android.net.Uri.parse(item.src))
                    } else {
                        if (item.src.startsWith("content://")) {
                        setDataSource(view.context, android.net.Uri.parse(item.src))
                    } else {
                        setDataSource(item.src) // 本地路径 / http(s) URL 均支持
                    }
                    }
                    prepareAsync()
                }
            }.getOrNull()
            mediaPlayer = mp
        }

        // ===== SurfaceTextureListener =====
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            surfaceReady = true
            ensurePlayer()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            applyCover()
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            surfaceReady = false
            releasePlayer()
            return true // 释放 surface 自身
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

        /** 视频居中裁切（cover），适配任意视口方向 */
        private var videoRotation = 0
        private var lastCoverKey: String? = null

        /** 视频旋转角度（部分竖屏拍摄视频带 90/270 旋转元数据，不处理会显示不全/抖动） */
        private fun loadVideoRotation() {
            scope.launch(Dispatchers.IO) {
                val r = runCatching<Int> {
                    val mmr = MediaMetadataRetriever()
                    try {
                        if (item.src.startsWith("content://")) {
                            mmr.setDataSource(view.context, android.net.Uri.parse(item.src))
                        } else {
                            mmr.setDataSource(item.src)
                        }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    } finally {
                        runCatching { mmr.release() }
                    }
                }.getOrDefault(0)
                if (r != videoRotation) {
                    videoRotation = r
                    applyCover()
                }
            }
        }

        private fun applyCover() {
            val mp = mediaPlayer ?: return
            val vw = mp.videoWidth
            val vh = mp.videoHeight
            if (vw <= 0 || vh <= 0) return
            val sw = view.width
            val sh = view.height
            if (sw <= 0 || sh <= 0) return
            val rot = videoRotation
            val key = "$vw:$vh:$sw:$sh:$rot"
            if (key == lastCoverKey) return
            lastCoverKey = key
            // 旋转后的有效宽高（90/270 时宽高互换）
            val rW = if (rot == 90 || rot == 270) vh else vw
            val rH = if (rot == 90 || rot == 270) vw else vh
            val matrix = Matrix()
            val scale = maxOf(sw.toFloat() / rW, sh.toFloat() / rH)
            // 以视频中心为锚缩放 + 旋转（中心及不动点，无需重算包围盒）
            matrix.setScale(scale, scale, vw / 2f, vh / 2f)
            matrix.postRotate(rot.toFloat(), vw / 2f, vh / 2f)
            // 视频中心平移到视口中心
            matrix.postTranslate(sw / 2f - vw / 2f, sh / 2f - vh / 2f)
            view.setTransform(matrix)
        }
    }
}