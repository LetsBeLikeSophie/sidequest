package com.sidequest.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Talks to the same server as ReactionClient (see /server/answerStore.js +
 * moderation.js) for the "다른 사람 한마디" pool (기획문서 2.5). Submitting
 * never reports moderation status back to the caller — the server itself
 * always answers {ok:true} regardless of verdict, so there's nothing to
 * leak here even if we wanted to.
 */
object CommunityClient {
    private const val SUBMIT_ENDPOINT = "https://itssophie.dev/sidequest/api/submit-answer"
    private const val RANDOM_ENDPOINT = "https://itssophie.dev/sidequest/api/random-answer"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Fire-and-forget — the sheet doesn't wait on this to close. */
    fun submitAnswer(quest: String, question: String, answer: String) {
        executor.execute {
            try {
                val connection = URL(SUBMIT_ENDPOINT).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject()
                    .put("quest", quest)
                    .put("question", question)
                    .put("answer", answer)
                    .toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                connection.responseCode // drain the response so the connection completes cleanly
            } catch (e: Exception) {
                // Same "no big deal" stance as ReactionClient — a dropped submission
                // just means this one quest doesn't join the community pool this time.
            }
        }
    }

    fun fetchRandomAnswer(quest: String, onResult: (String?) -> Unit) {
        executor.execute {
            val answer = try {
                val encodedQuest = URLEncoder.encode(quest, "UTF-8")
                val connection = URL("$RANDOM_ENDPOINT?quest=$encodedQuest").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.isNull("answer")) null else json.getString("answer")
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            mainHandler.post { onResult(answer) }
        }
    }
}
