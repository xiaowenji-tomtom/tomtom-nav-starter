package com.tomtom.demo.nav.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.feature.home.MainActivity

/**
 * ［feature:widget · WS5］桌面 Widget（规格书"多屏互动-widget"需求）：
 * 导航后台运行时显示关键引导信息，数据来自 GuidanceBus（经 feature/guidance 前台 Service 推送）。
 */
class NavWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, context.getString(R.string.widget_idle), null)
    }

    companion object {
        fun update(context: Context, text: String, remainingTimeSeconds: Long?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NavWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_nav).apply {
                setTextViewText(R.id.widget_instruction, text)
                setTextViewText(
                    R.id.widget_eta,
                    remainingTimeSeconds?.let { "剩余 ${it / 60} 分钟" }.orEmpty(),
                )
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
