package io.legado.app.web.mcp

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.config.AppConfig
import io.legado.app.receiver.McpServerActionReceiver
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.utils.NetworkUtils

/**
 * AI MCP 服务常驻通知：开关开启时提醒对外暴露及其访问地址，便于一键跳转/关闭。
 */
object McpServerNotification {

    const val ACTION_STOP = "io.legado.app.action.MCP_SERVER_STOP"
    private const val NOTIFICATION_ID = 0x4D4350 // "MCP"

    /** 根据开关状态刷新常驻通知 */
    fun refresh(context: Context, enabled: Boolean) {
        if (!enabled) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        // Android 13+ 通知权限检查
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val ip = NetworkUtils.getLocalIPAddress().firstOrNull()?.hostAddress
            ?: context.getString(R.string.ai_mcp_server_ip_placeholder)
        val url = "http://$ip:${AppConfig.webPort}/mcp"

        val openPi = PendingIntent.getActivity(
            context, 1,
            Intent(context, ConfigActivity::class.java)
                .putExtra("configTag", ConfigTag.AI_CONFIG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, McpServerActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AppConst.channelIdAiMcp)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.ai_mcp_server_noti_title))
            .setContentText(url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.ai_mcp_server_noti_text, url)))
            .setContentIntent(openPi)
            .setOngoing(true) // 常驻不可滑动清除
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.ai_mcp_server_noti_stop), stopPi)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}