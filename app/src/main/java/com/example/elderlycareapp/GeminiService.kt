package com.example.elderlycareapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object GeminiService {
    private const val API_KEY = "YOUR_API_KEY_HERE" // Replace this
    private val client = OkHttpClient()

    suspend fun streamChatResponse(userInput: String, onChunkReceived: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:streamGenerateContent?key=$API_KEY"

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userInput)))
                }))
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onChunkReceived("Error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.let { body ->
                        val source: BufferedSource = body.source()
                        try {
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line()
                                if (!line.isNullOrEmpty() && line.startsWith("data: ")) {
                                    val json = JSONObject(line.removePrefix("data: "))
                                    val candidates = json.optJSONArray("candidates") ?: continue
                                    val text = candidates.getJSONObject(0)
                                        .optJSONObject("content")
                                        ?.optJSONArray("parts")
                                        ?.optJSONObject(0)
                                        ?.optString("text")

                                    if (!text.isNullOrEmpty()) {
                                        onChunkReceived(text)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            onChunkReceived("Streaming error: ${e.message}")
                        }
                    }
                }
            })
        }
    }
}
