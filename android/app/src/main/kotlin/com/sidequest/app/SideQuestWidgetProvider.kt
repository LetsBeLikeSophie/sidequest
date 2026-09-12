package com.sidequest.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

data class Quest(val tag: String, val text: String)

/**
 * Core technical bet of the whole product: "다음에" must update the widget
 * instantly, in the background, without ever launching the app. This class
 * is the proof that RemoteViews + a self-targeted PendingIntent broadcast
 * can do that on real Android, not just in an HTML mockup.
 *
 * Quest state (which index is "current") lives here so both the widget and
 * MainActivity's note sheet read/advance the same single source of truth.
 */
class SideQuestWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SKIP = "com.sidequest.app.ACTION_SKIP"

        fun currentQuest(context: Context): Quest =
            QuestSequencer.current(context, QuestPool.all(context))

        /** Moves to the next quest and repaints every placed widget. Used by both
         *  the widget's own "다음에" button and MainActivity when its note sheet closes. */
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
        if (intent.action == ACTION_SKIP) {
            advanceAndRefreshWidgets(context)
        }
    }
}
