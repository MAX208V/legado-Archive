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
    val mode: String = ReadBookConfig.ROTATION_MODE_ALL
) {
    fun toJson(): String = JSONObject()
        .put("type", type)
        .put("src", src)
        .put("alpha", alpha)
        .put("mode", mode)
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
                mode = j.optString("mode", ReadBookConfig.ROTATION_MODE_ALL)
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

    /** 首次布局/方向变化后重建预置层（布局尺寸 0 时 buildBgDrawable 会退化为纯色） */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && width > 0 && height > 0) {
            refreshBgLayer()
            refreshRotationLayer()
        }
    }

    /** 重建图层（按 items 顺序从底到顶叠放；含预置项 __bg__ / __rotation__） */
    fun setLayers(items: List<String>) {
        clearLayers()
        removeAllViews()
        items.forEachIndexed { index, entry ->
            val layer = createLayer(entry) ?: return@forEachIndexed
            layers.add(layer)
            addView(layer.view, index)
            layer.load()
        }
    }

    fun hasLayers(): Boolean = layers.isNotEmpty()

    fun isEmpty(): Boolean = layers.isEmpty()

    /** 背景图片预置层刷新（样式/日夜切换后调用） */
    fun refreshBgLayer() {
        layers.filterIsInstance<BgPrefabLayer>().forEach { it.load() }
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
        override val view: AppCompatImageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = false
            isFocusable = false
        }

        override fun load() {
            val w = view.width.coerceAtLeast(SystemUtils.screenWidthPx)
            val h = view.height.coerceAtLeast(SystemUtils.screenHeightPx)
            view.setImageDrawable(rotationDrawable(w, h))
        }

        private fun rotationDrawable(w: Int, h: Int): android.graphics.drawable.Drawable? = runCatching {
            val cfg = ReadBookConfig
            val styleIdx = cfg.rotationStyleIndex
            when {
                styleIdx != null -> {
                    val sc = ReadBookConfig.getConfig(styleIdx)
                    ReadBookConfig.durConfig.buildBgDrawable(
                        w, h, sc.curBgType(), sc.curBgStr()
                    )
                }
                cfg.rotationBgType != null && cfg.rotationBgStr != null -> {
                    ReadBookConfig.durConfig.buildBgDrawable(
                        w, h, cfg.rotationBgType!!, cfg.rotationBgStr!!
                    )
                }
                else -> null
            }
        }.getOrNull()

        override fun release() {
            view.setImageDrawable(null)
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
                    setVolume(0f, 0f) // 壁纸静音
                    setOnPreparedListener {
                        prepared = true
                        applyCover()
                        if (!paused) it.start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                    setOnVideoSizeChangedListener { _, _, _ -> applyCover() }
                    setDataSource(item.src) // 本地路径 / http(s) URL 均支持
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
        private fun applyCover() {
            val mp = mediaPlayer ?: return
            val vw = mp.videoWidth
            val vh = mp.videoHeight
            if (vw <= 0 || vh <= 0) return
            val sw = view.width
            val sh = view.height
            if (sw <= 0 || sh <= 0) return
            val matrix = Matrix()
            val scale = maxOf(sw.toFloat() / vw, sh.toFloat() / vh)
            matrix.setScale(scale, scale)
            matrix.postTranslate((sw - vw * scale) / 2f, (sh - vh * scale) / 2f)
            view.setTransform(matrix)
        }
    }
}