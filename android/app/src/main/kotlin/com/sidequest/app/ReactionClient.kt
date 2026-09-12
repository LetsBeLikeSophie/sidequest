package com.sidequest.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to the server-side reaction proxy (see /server in the repo root) —
 * the Anthropic key lives only on that server and never ships in this app.
 * A network hiccup here just means no reaction, same as the 45% "no
 * reaction" case — this is meant to be low-stakes and silent either way.
 */
object ReactionClient {
    private const val ENDPOINT = "https://itssophie.dev/sidequest/api/react"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetchReaction(note: String, onResult: (String?) -> Unit) {
        executor.execute {
            val reaction = try {
                val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().put("note", note).toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.isNull("reaction")) null else json.getString("reaction")
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            mainHandler.post { onResult(reaction) }
        }
    }
}
