package io.legado.app.web.mcp

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi

/**
 * 「对外 MCP 服务」前台服务。
 *
 * 独立 NanoHTTPD（McpServer）本身不承载任何 Android 生命周期；
 * 由本前台服务持有并保活：后台/熄屏时进程不会被 Doze/厂商省电随意杀，
 * 进程被系统回收后 START_STICKY 自动重启恢复。
 *
 * 前台服务类型 dataSync（与 RelayService 一致）：
 * - Android 14+ 需在 Manifest 声明 FOREGROUND_SERVICE_DATA_SYNC 权限（已添加）；
 * - 注：dataSync 在 Android 15+ 有 6 小时运行上限，超时后系统停止服务，
 *   用户重新打开「对外 MCP 服务」开关即可恢复（specialUse 在非 Play 渠道
 *   Android 16 上被系统拒绝授予权限，故改用 dataSync）。
 *
 * 启停入口：
 * - 打开「对外 MCP 服务」开关 → startForeground()（AiConfigFragment）
 * - App 启动恢复（上次开关开启）→ startForeground()（App.kt onCreate）
 * - 通知「关闭」按钮 → McpServerActionReceiver → stop()
 * - 关闭开关 → stop()（AiConfigFragment）
 */
class McpServerService : BaseService() {

    companion object {
        private const val TAG = "McpServerService"

        /** 以前台服务方式启动（Android 8+ 用 startForegroundService） */
        fun startForeground(context: Context) {
            context.startForegroundServiceCompat(Intent(context, McpServerService::class.java))
        }

        /** 停止服务（会连带停 McpServer 并移除常驻通知） */
        fun stop(context: Context) {
            context.stopService(Intent(context, McpServerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 进程被杀后 START_STICKY 重启（intent 可能为 null）也能恢复服务
        ensureServer()
        return super.onStartCommand(intent, flags, startId) // BaseService 内统一 startForeground
    }

    private fun ensureServer() {
        if (McpServer.running) return
        if (McpServer.start(AppConfig.aiMcpPort)) {
            LogUtils.d(TAG) { "MCP server ensured on port ${AppConfig.aiMcpPort}" }
        } else {
            LogUtils.d(TAG) { "MCP server start failed, stopping service" }
            toastOnUi(R.string.ai_mcp_server_out_start_failed)
            stopSelf()
        }
    }

    override fun onDestroy() {
        LogUtils.d(TAG) { "McpServerService destroy, stopping MCP server" }
        if (McpServer.running) {
            McpServer.stop()
        }
        // 兼容方法：API 24+ STOP_FOREGROUND_REMOVE，旧版本等效 stopForeground(true)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** 前台服务通知：显示可访问地址 + 「关闭」按钮 */
    override fun startForegroundNotification() {
        // targetSdk 34+ 必须在 startForeground 显式指定前台服务类型，
        // 否则 specialUse 类型无法与 FOREGROUND_SERVICE_SPECIAL_USE 权限正确关联（Android 16 直接抛 SecurityException）
        ServiceCompat.startForeground(
            this,
            McpServerNotification.NOTIFICATION_ID,
            McpServerNotification.buildServiceNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}