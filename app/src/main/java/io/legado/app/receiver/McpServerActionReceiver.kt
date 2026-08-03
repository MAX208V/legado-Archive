package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.legado.app.help.config.AppConfig
import io.legado.app.web.mcp.McpServerNotification

/**
 * 处理 AI MCP 服务通知中的「关闭」操作：关闭对外开关并移除常驻通知。
 */
class McpServerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == McpServerNotification.ACTION_STOP) {
            AppConfig.aiMcpEnabled = false
            McpServerNotification.refresh(context, false)
        }
    }
}