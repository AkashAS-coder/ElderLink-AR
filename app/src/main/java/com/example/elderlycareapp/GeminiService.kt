package com.example.elderlycareapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)  // Reduced from 30s to 10s
            .readTimeout(15, TimeUnit.SECONDS)      // Reduced from 30s to 15s
            .retryOnConnectionFailure(true)
            .build()
    }
    
    @Volatile
    private var isInitialized = false
    private var apiKey: String = ""
    private var lastError: String? = null
    
    // Simple cache for common responses to improve speed
    private val responseCache = mutableMapOf<String, String>()
    private val maxCacheSize = 50
    
    suspend fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            // GeminiService is deprecated - use FirebaseChatService instead
            Log.w(TAG, "GeminiService is deprecated. Use FirebaseChatService instead.")
            throw IllegalStateException("GeminiService is deprecated. Use FirebaseChatService instead.")
            
        } catch (e: Exception) {
            val errorMsg = "Failed to initialize GeminiService: ${e.message}"
            Log.e(TAG, errorMsg, e)
            lastError = errorMsg
            throw e
        }
    }

    suspend fun streamChatResponse(userInput: String, onChunkReceived: (String) -> Unit) {
        // GeminiService is deprecated - use FirebaseChatService instead
        onChunkReceived("GeminiService is deprecated. Use FirebaseChatService instead.")
    }
    
    suspend fun getChatResponse(userInput: String): String {
        // GeminiService is deprecated - use FirebaseChatService instead
        throw IllegalStateException("GeminiService is deprecated. Use FirebaseChatService instead.")
    }
    
    private fun parseGeminiResponse(jsonString: String): String? {
        return try {
            val jsonObject = JSONObject(jsonString)
            jsonObject.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?: "I couldn't generate a response. Could you try rephrasing your question?"
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response", e)
            null
        }
    }
    
    suspend fun testApiConnection(): String {
        if (apiKey.isEmpty() || apiKey == "YOUR_ACTUAL_GEMINI_API_KEY") {
            return "Error: API key not configured. Please set GEMINI_API_KEY in local.properties"
        }
        
        return try {
            val testUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val testBody = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", "Hello")))
                }))
            }.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(testUrl)
                .post(testBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                "API connection successful! Response code: ${response.code}"
            } else {
                "API test failed. Code: ${response.code}, Error: ${response.body?.string() ?: "No details"}"
            }
        } catch (e: Exception) {
            "API test error: ${e.message}"
        }
    }
}
