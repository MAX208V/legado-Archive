package io.legado.app.help.glide

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

/**
 * Glide ModelLoader for [file:///android_asset/] URIs.
 *
 * Glide 的默认 UriLoader 会把 file:// URI 当文件系统路径打开，
 * 但 /android_asset/ 并非真实路径（资源打包在 APK 内部），因此抛出 FileNotFoundException。
 * 本 Loader 通过 [AssetManager.open] 正确读取 assets 资源并返回 InputStream，
 * 让 Glide 的 Downsampler 完成缩放与缓存。
 *
 * 注册为 Uri → InputStream，优先于默认 UriLoader（prepend），仅处理 file:///android_asset/ 前缀。
 */
class AssetUriLoader(private val context: Context) : ModelLoader<Uri, InputStream> {

    private val assetManager: AssetManager get() = context.assets

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: com.bumptech.glide.load.Options
    ): ModelLoader.LoadData<InputStream>? {
        if (!isAssetUri(model)) return null
        return ModelLoader.LoadData(
            ObjectKey(model.toString()),
            AssetFetcher(assetManager, getAssetPath(model))
        )
    }

    override fun handles(model: Uri): Boolean = isAssetUri(model)

    /**
     * 从 Uri 提取 assets 内部路径。
     * Uri 格式：file:///android_asset/bg/午后沙滩.jpg
     * 返回：bg/午后沙滩.jpg
     *
     * 注意：[Uri.getPath] 会自动解码 percent-encoding（如 %E5%8D%88 → 午），
     * AssetManager.open 接受 UTF-8 明文路径，因此可以直接使用。
     */
    private fun getAssetPath(uri: Uri): String {
        // /android_asset/bg/xxx.jpg → bg/xxx.jpg
        return uri.path?.removePrefix("/android_asset/") ?: ""
    }

    class AssetFetcher(
        private val assetManager: AssetManager,
        private val assetPath: String
    ) : DataFetcher<InputStream> {

        override fun loadData(
            priority: Priority,
            callback: DataFetcher.DataCallback<in InputStream>
        ) {
            try {
                val inputStream = assetManager.open(assetPath)
                callback.onDataReady(inputStream)
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() {}
        override fun cancel() {}
        override fun getDataClass(): Class<InputStream> = InputStream::class.java
        override fun getDataSource(): DataSource = DataSource.LOCAL
    }

    companion object {
        /** 是否为 file:///android_asset/ 形式的 URI */
        fun isAssetUri(uri: Uri): Boolean {
            return uri.scheme == "file" &&
                    uri.path?.startsWith("/android_asset/") == true
        }

        /** 判断字符串路径是否为 asset URI */
        fun isAssetPath(path: String?): Boolean {
            return path?.startsWith("file:///android_asset/") == true
        }
    }

    class Factory(private val context: Context) : ModelLoaderFactory<Uri, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> {
            return AssetUriLoader(context.applicationContext)
        }
        override fun teardown() {}
    }
}
