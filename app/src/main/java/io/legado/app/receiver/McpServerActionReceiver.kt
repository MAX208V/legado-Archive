package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.legado.app.help.config.AppConfig
import io.legado.app.web.mcp.McpServer
import io.legado.app.web.mcp.McpServerNotification
import io.legado.app.web.mcp.McpServerService

/**
 * 处理 AI MCP 服务通知中的「关闭」操作：停止前台服务与独立服务器、关闭开关并移除常驻通知。
 */
class McpServerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == McpServerNotification.ACTION_STOP) {
            McpServer.stop()
            McpServerService.stop(context) // 服务 onDestroy 会连带 stopForeground 移除通知
            AppConfig.aiMcpEnabled = false
            McpServerNotification.refresh(context, false)
        }
    }
}