package com.sidequest.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Core technical bet of the whole product: "다음에" must update the widget
 * instantly, in the background, without ever launching the app. This class
 * is the proof that RemoteViews + a self-targeted PendingIntent broadcast
 * can do that on real Android, not just in an HTML mockup.
 */
class SideQuestWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SKIP = "com.sidequest.app.ACTION_SKIP"
        private const val PREFS = "side_quest_widget_state"
        private const val KEY_INDEX = "quest_index"

        private val QUESTS = listOf(
            "실내 · 5분" to "창문 하나만 활짝 열어보기",
            "실외 · 즉시" to "하늘 색 3초만 쳐다보기",
            "같이 · 즉시" to "옆에 있는 사람한테 아무 말이나 걸어보기",
            "머리 씀 · 15분+" to "안 읽은 책 아무 페이지나 펼쳐 읽기",
            "이동중 · 즉시" to "이어폰 빼고 주변 소리 들어보기",
            "그냥 멍 · 5분" to "아무것도 안 하고 가만히 앉아있기",
            "몸 씀 · 즉시" to "목 한 바퀴 천천히 돌려보기",
            "창작 · 15분+" to "아무 종이에나 낙서 하나 그려보기"
        )

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val index = prefs.getInt(KEY_INDEX, 0)
            val (tag, text) = QUESTS[index % QUESTS.size]

            val views = RemoteViews(context.packageName, R.layout.widget_medium)
            views.setTextViewText(R.id.widget_tag, tag)
            views.setTextViewText(R.id.widget_quest_text, text)

            val skipIntent = Intent(context, SideQuestWidgetProvider::class.java).apply {
                action = ACTION_SKIP
            }
            val skipPending = PendingIntent.getBroadcast(
                context, widgetId, skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_skip, skipPending)

            val acceptIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val acceptPending = PendingIntent.getActivity(
                context, widgetId, acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_accept, acceptPending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SideQuestWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SKIP) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val next = (prefs.getInt(KEY_INDEX, 0) + 1) % QUESTS.size
            prefs.edit().putInt(KEY_INDEX, next).apply()
            updateAllWidgets(context)
        }
    }
}
