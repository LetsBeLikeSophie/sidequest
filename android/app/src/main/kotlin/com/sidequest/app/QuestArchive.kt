package com.sidequest.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stand-in for the real backend archive ("서버에 조용히 아카이브"). Never shown
 * to the user — internal record only, kept locally until a real server exists.
 */
object QuestArchive {
    private const val PREFS = "side_quest_archive"
    private const val KEY_ENTRIES = "entries"

    fun save(context: Context, tag: String, questText: String, question: String, answer: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))

        val entry = JSONObject().apply {
            put("tag", tag)
            put("quest", questText)
            put("question", question)
            put("answer", answer)
            put("ts", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
        }
        all.put(entry)

        prefs.edit().putString(KEY_ENTRIES, all.toString()).apply()
    }
}
