package com.sidequest.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

data class Quest(val tag: String, val text: String, val duration: String, val question: String)

/**
 * Core technical bet of the whole product: both widget buttons must act
 * instantly, in the background, without ever launching the app. This class
 * is the proof that RemoteViews + a self-targeted PendingIntent broadcast
 * can do that on real Android, not just in an HTML mockup.
 *
 * The widget only ever asks one question: 지우기 (not for me) or 하트
 * (interested). Neither claims you've actually done anything — "해볼래"
 * used to archive the instant it was tapped, which meant the app was
 * recording a "완료" before you'd done a single thing. 하트 now just bumps
 * interest and books a CallbackScheduler follow-up for later, when there's
 * actually something to ask about.
 *
 * Quest state (which index is "current") lives here so both the widget and
 * the callback flow read/advance the same single source of truth.
 */
class SideQuestWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SKIP = "com.sidequest.app.ACTION_SKIP"
        const val ACTION_HEART = "com.sidequest.app.ACTION_HEART"

        fun currentQuest(context: Context): Quest =
            QuestSequencer.current(context, QuestPool.all(context))

        /** Moves to the next quest and repaints every placed widget. */
        fun advanceAndRefreshWidgets(context: Context) {
            QuestSequencer.advance(context, QuestPool.all(context))
            refreshAllWidgets(context)
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SideQuestWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val quest = currentQuest(context)

            val views = RemoteViews(context.packageName, R.layout.widget_medium)
            views.setTextViewText(R.id.widget_tag, quest.tag)
            views.setTextViewText(R.id.widget_quest_text, quest.text)

            val skipIntent = Intent(context, SideQuestWidgetProvider::class.java).apply {
                action = ACTION_SKIP
            }
            val skipPending = PendingIntent.getBroadcast(
                context, widgetId, skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_skip, skipPending)

            val heartIntent = Intent(context, SideQuestWidgetProvider::class.java).apply {
                action = ACTION_HEART
            }
            val heartPending = PendingIntent.getBroadcast(
                context, widgetId, heartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_accept, heartPending)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    // Called once when the first widget instance is placed — a reasonable
    // moment to start the random notification chain (기획문서 2.1).
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        NotificationScheduler.ensureScheduled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SKIP -> advanceAndRefreshWidgets(context)
            ACTION_HEART -> {
                // capture the quest being hearted before advancing past it
                val quest = currentQuest(context)
                CallbackScheduler.scheduleCallback(context, quest)
                advanceAndRefreshWidgets(context)
            }
        }
    }
}
