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
}

/**
 * 单个壁纸图层配置
 */
data class WallpaperItem(
    val type: Int,
    val src: String,
    val alpha: Int = 255
) {
    fun toJson(): String = JSONObject()
        .put("type", type)
        .put("src", src)
        .put("alpha", alpha)
        .toString()

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
                alpha = j.optInt("alpha", 255)
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

    /** 重建图层（按 items 顺序从底到顶叠放） */
    fun setLayers(items: List<WallpaperItem>) {
        clearLayers()
        removeAllViews()
        items.forEachIndexed { index, item ->
            val layer = createLayer(item)
            layers.add(layer)
            addView(layer.view, index)
            layer.load()
        }
    }

    fun hasLayers(): Boolean = layers.isNotEmpty()

    fun isEmpty(): Boolean = layers.isEmpty()

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

    private fun createLayer(item: WallpaperItem): LayerView {
        return when (item.type) {
            WallpaperLayerType.VIDEO -> VideoLayerView(context, item)
            else -> ImageLayerView(context, item)
        }
    }

    private interface LayerView {
        val view: android.view.View
        fun load()
        fun start() {}
        fun pause() {}
        fun release() {}
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
            com.bumptech.glide.Glide.with(view.context).clear(view)
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