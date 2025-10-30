package com.example.elderlycareapp

import android.content.Context
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max

// Extension function to make String.titlecase() available
private fun String.titlecase(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

object FirebaseChatService {
    private const val TAG = "FirebaseChatService"
    private var functions: FirebaseFunctions? = null
    private var firestore: FirebaseFirestore? = null
    private var isInitialized = false
    
    // Conversation context management
    private val conversationContexts = mutableMapOf<String, MutableList<ChatMessage>>()
    private const val MAX_CONTEXT_LENGTH = 15
    private val PAIN_KEYWORDS = setOf(
        "pain", "hurt", "ache", "sore", "discomfort", "stiff", "tender",
        "headache", "backache", "knee", "shoulder", "hip", "joint",
        "swelling", "inflammation", "sharp", "dull", "throbbing"
    )
    
    // Data classes for chat and pain context
    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val painLevel: Int = 0,
        val painLocation: String = "",
        val painType: String = ""
    )
    
    data class PainContext(
        val location: String,
        val type: String,
        val severity: Int,
        val frequency: String,
        val lastMentioned: Long,
        val conversationCount: Int = 1
    )
    
    private val painContexts = mutableMapOf<String, PainContext>()
    
    fun isInitialized(): Boolean = isInitialized
    
    suspend fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            functions = FirebaseFunctions.getInstance()
            firestore = FirebaseFirestore.getInstance()
            isInitialized = true
            Log.d(TAG, "Firebase Chat Service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseChatService", e)
            throw e
        }
    }
    
    suspend fun getChatResponse(
        userInput: String,
        conversationId: String = "default",
        context: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext "Error: Service not initialized. Please call initialize() first."
        }
        
        try {
            // Add user message to context
            val userMessage = ChatMessage(
                text = userInput,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            addMessageToContext(conversationId, userMessage)
            
            // Get conversation context
            val conversation = getConversationContext(conversationId).toMutableList()
            
            // Prepare messages in the format expected by the Cloud Function
            val messagesPayload = mutableListOf<Map<String, Any>>()
            
            // Add conversation history first (excluding current message)
            conversation.reversed().forEach { msg ->
                if (msg.text.trim() != userInput.trim() && msg.text.isNotBlank()) {
                    val role = if (msg.isUser) "user" else "assistant"
                    messagesPayload.add(mapOf(
                        "role" to role,
                        "content" to msg.text.trim(),
                        "timestamp" to msg.timestamp
                    ))
                }
            }
            
            // Add the current user message last
            if (userInput.isNotBlank()) {
                messagesPayload.add(mapOf(
                    "role" to "user",
                    "content" to userInput.trim(),
                    "timestamp" to System.currentTimeMillis()
                ))
            }

            // Validate we have at least one message
            if (messagesPayload.isEmpty()) {
                Log.e(TAG, "No valid messages to send to chat service")
                return@withContext "I'm sorry, I didn't receive any message to process."
            }

            val data = hashMapOf<String, Any>(
                "messages" to messagesPayload.map { msg ->
                    mutableMapOf<String, Any>().apply {
                        putAll(msg)
                        // Ensure timestamp is a number, not a Long
                        msg["timestamp"]?.let { put("timestamp", (it as Number).toLong()) }
                    }
                },
                "conversationId" to conversationId,
                "context" to context,
                "timestamp" to System.currentTimeMillis()
            )
            
            val functionsInstance = functions ?: return@withContext "Error: Firebase Functions not available"
            
            try {
                Log.d(TAG, "Sending request to chatWithAI with data: $data")
                
                val result = functionsInstance
                    .getHttpsCallable("chatWithAI")
                    .call(data)
                    .await()

                val response = when (val d = result?.data) {
                    is String -> d
                    is Map<*, *> -> d["response"] as? String 
                        ?: d.values.firstOrNull()?.toString() 
                        ?: "I'm not sure how to respond to that."
                    else -> result?.data?.toString() ?: "I didn't receive a valid response."
                }
                
                Log.d(TAG, "Received response: $response")

                // Sanitize and format the response before storing/returning
                val sanitizedResponse = sanitizeResponse(response)

                // Add AI response to context
                val aiMessage = ChatMessage(
                    text = sanitizedResponse,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                addMessageToContext(conversationId, aiMessage)

                // Save conversation
                saveConversationToFirestore(conversationId, getConversationContext(conversationId))

                sanitizedResponse
            } catch (e: Exception) {
                val errorMessage = "I'm having trouble connecting to the chat service. Please try again in a moment."
                Log.e(TAG, "Error calling chatWithAI function", e)
                
                // Add error message to context
                val sanitizedError = sanitizeResponse(errorMessage)
                val errorMessageObj = ChatMessage(
                    text = sanitizedError,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                addMessageToContext(conversationId, errorMessageObj)

                sanitizedError
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getChatResponse", e)
            sanitizeResponse("I'm having trouble connecting to the chat service. Please try again later.")
        }
    }

    // Summarize responses by word count (default maxWords = 200). This version preserves
    // paragraph breaks: it splits text into paragraphs (blank-line separated), collapses
    // internal whitespace within paragraphs, and joins paragraphs with a double-newline
    // so UI components will render an empty line between paragraphs.
    private fun sanitizeResponse(raw: String, maxWords: Int = 200): String {
        try {
            if (raw.isBlank()) return ""

            // Remove simple formatting markers first
            val withoutAsterisks = raw.replace("*", "")

            // Normalize line endings to \n
            val normalizedNewlines = withoutAsterisks.replace("\r\n", "\n").replace('\r', '\n')

            // Split into paragraphs on blank lines, trim and collapse internal whitespace in each paragraph
            val paragraphs = normalizedNewlines
                .split(Regex("\\n\\s*\\n+"))
                .map { p -> p.trim().replace(Regex("\\s+"), " ") }
                .filter { it.isNotEmpty() }

            if (paragraphs.isEmpty()) return ""

            var candidate = paragraphs.joinToString("\n\n")

            // Word-count truncation while preserving paragraph separators
            val allWords = candidate.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (allWords.size > maxWords) {
                var wordCount = 0
                var endIndex = -1
                val matcher = Regex("\\S+").findAll(candidate)
                for (m in matcher) {
                    wordCount++
                    if (wordCount == maxWords) {
                        endIndex = m.range.last
                        break
                    }
                }

                var truncated = if (endIndex >= 0) candidate.substring(0, endIndex + 1) else allWords.take(maxWords).joinToString(" ")

                // Try to extend to the nearest sentence end (., !, ?) after the truncated part using the original text
                val origIndex = normalizedNewlines.indexOf(truncated)
                if (origIndex >= 0) {
                    val afterStart = origIndex + truncated.length
                    if (afterStart < normalizedNewlines.length) {
                        val nextSentenceEnd = normalizedNewlines.indexOfAny(charArrayOf('.', '!', '?'), startIndex = afterStart)
                        if (nextSentenceEnd >= 0) {
                            // Extend to the end of that sentence, but don't go excessively far
                            truncated = normalizedNewlines.substring(0, nextSentenceEnd + 1)
                        }
                    }
                }

                // Re-split truncated text into paragraphs and collapse internal whitespace again
                val finalParagraphs = truncated
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .split(Regex("\\n\\s*\\n+"))
                    .map { p -> p.trim().replace(Regex("\\s+"), " ") }
                    .filter { it.isNotEmpty() }

                candidate = finalParagraphs.joinToString("\n\n")
            }

            // Decide whether the candidate looks like an explicit list or short multiple lines
            val explicitListMarker = Regex("""(?m)^\s*(?:[-*]|\d+\.)\s+""").containsMatchIn(candidate)
            val lines = candidate.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            val shortLines = lines.size >= 2 && lines.map { it.split(Regex("\\s+")).filter { w -> w.isNotEmpty() }.size }.average() <= 12.0

            // Prefer paragraph-style output. Even when the source looks like a list, prefer
            // plain paragraphs separated by a blank line (no numbering).
            val result = run {
                val items = if (explicitListMarker || shortLines) {
                    if (lines.isNotEmpty()) lines else candidate.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    listOf(candidate)
                }

                items.joinToString("\n\n") { it.trim().replace(Regex(" {2,}"), " ") }
            }

            // Final cleaning: remove control characters while preserving common punctuation and ensure
            // blank lines are represented as \n\n
            val cleanedResult = result
                .replace(Regex("""[^\p{L}\p{N}\s\.,!?:;'"()\-—/%&@#\+={}\[\]]+"""), "")
                .replace(Regex(" {2,}"), " ")
                .replace(Regex("""\n\s*\n+"""), "\n\n")

            return cleanedResult.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing response", e)
            return raw.replace("*", "").replace(Regex("""[^\p{L}\p{N}\s\.,!?:;'"()\-—/%&@#\+={}\[\]]+"""), "").trim()
        }
    }
    
    private fun addMessageToContext(conversationId: String, message: ChatMessage) {
        try {
            val messageList = conversationContexts.getOrPut(conversationId) { mutableListOf() }
            messageList.add(message)
            
            // Keep only the last MAX_CONTEXT_LENGTH messages
            while (messageList.size > MAX_CONTEXT_LENGTH) {
                messageList.removeAt(0)
            }
            
            // Analyze for pain context if needed
            if (PAIN_KEYWORDS.any { keyword -> 
                message.text.contains(keyword, ignoreCase = true) 
            }) {
                analyzePainContext(message.text, conversationId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in addMessageToContext", e)
        }
    }
    
    private fun analyzePainContext(message: String, conversationId: String) {
        try {
            // Check for pain keywords
            val hasPainKeywords = PAIN_KEYWORDS.any { keyword -> 
                message.contains(keyword, ignoreCase = true) 
            }
            if (!hasPainKeywords) return
            
            // Extract pain details
            val painLevel = extractPainLevel(message)
            val painLocation = extractPainLocation(message)
            val painType = extractPainType(message)
            
            // Create a unique key for this pain context
            val contextKey = "${painLocation}_${painType}_$conversationId"
            
            // Update or create pain context
            val existingContext = painContexts[contextKey]
            
            if (existingContext != null) {
                // Update existing pain context
                painContexts[contextKey] = existingContext.copy(
                    severity = maxOf(existingContext.severity, painLevel),
                    lastMentioned = System.currentTimeMillis(),
                    conversationCount = existingContext.conversationCount + 1
                )
            } else {
                // Create new pain context
                painContexts[contextKey] = PainContext(
                    location = painLocation,
                    type = painType,
                    severity = painLevel,
                    frequency = "recent",
                    lastMentioned = System.currentTimeMillis(),
                    conversationCount = 1
                )
            }
            
            Log.d(TAG, "Updated pain context: $contextKey")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing pain context", e)
        }
    }
    
    private fun extractPainLocation(message: String): String {
        return try {
            val locations = listOf(
                "head", "forehead", "temple", "face",
                "neck", "shoulder", "back", "spine",
                "arm", "elbow", "wrist", "hand", "finger",
                "chest", "rib", "stomach", "abdomen",
                "hip", "thigh", "knee", "leg", "ankle", "foot", "toe"
            )
            
            for (location in locations) {
                if (message.contains(location, ignoreCase = true)) {
                    return location
                }
            }
            
            return "general"
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting pain location", e)
            "general"
        }
    }
    
    private fun extractPainLevel(message: String): Int {
        return try {
            // Look for pain level in the format "pain level X" or "level X" where X is a number 1-10
            val regex = "\\b(?:pain\\s*)?level\\s*(10|[1-9])\\b".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(message)
            val level = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            level.coerceIn(0, 10) // Ensure the level is between 0 and 10
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting pain level", e)
            0
        }
    }
    
    private fun extractPainType(message: String): String {
        return try {
            val types = mapOf(
                "sharp" to listOf("sharp", "stabbing", "piercing"),
                "dull" to listOf("dull", "aching", "throbbing"),
                "burning" to listOf("burning", "hot", "fire"),
                "stiff" to listOf("stiff", "rigid", "tight"),
                "tender" to listOf("tender", "sensitive", "sore")
            )
            
            for ((type, keywords) in types) {
                if (keywords.any { keyword -> 
                    message.contains(keyword, ignoreCase = true)
                }) {
                    return type
                }
            }
            
            return "general"
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting pain type", e)
            "general"
        }
    }
    
    fun getPainContext(): Map<String, PainContext> = painContexts.toMap()
    
    fun getPainSummary(): String {
        return try {
            if (painContexts.isEmpty()) return "No pain issues have been discussed yet."
            
            val summary = StringBuilder("Based on our conversations, here's what I know about your pain:\n\n")
            
            painContexts.values
                .sortedByDescending { it.lastMentioned }
                .forEach { context ->
                    with(context) {
                        summary.append("• ${location.replaceFirstChar { it.uppercase() }} pain: $type, severity $severity/10\n")
                        summary.append("  Last mentioned: ${formatTimeAgo(lastMentioned)}\n")
                        summary.append("  Discussed $conversationCount time${if (conversationCount > 1) "s" else ""}\n\n")
                    }
                }
            
            summary.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating pain summary", e)
            "Unable to generate pain summary at this time."
        }
    }
    
    private fun formatTimeAgo(timestamp: Long): String {
        return try {
            val diff = System.currentTimeMillis() - timestamp
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            
            when {
                days > 30 -> "${days / 30} month${if (days > 60) "s" else ""} ago"
                days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
                hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
                minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
                else -> "just now"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting time", e)
            "recently"
        }
    }
    
    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    
    private fun getConversationContext(conversationId: String): List<ChatMessage> {
        return try {
            conversationContexts[conversationId]?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting conversation context", e)
            emptyList()
        }
    }
    
    private suspend fun saveConversationToFirestore(
        conversationId: String,
        messages: List<ChatMessage>
    ) {
        val firestoreInstance = firestore ?: run {
            Log.w(TAG, "Firestore not initialized, cannot save conversation")
            return
        }

        return withContext(Dispatchers.IO) {
            try {
                // Convert messages to a format that can be serialized to Firestore
                val messagesData = messages.map { message ->
                    hashMapOf<String, Any>(
                        "text" to message.text,
                        "isUser" to message.isUser,
                        "timestamp" to message.timestamp,
                        "painLevel" to message.painLevel,
                        "painLocation" to message.painLocation,
                        "painType" to message.painType
                    )
                }

                // Create the conversation document
                val conversationData = hashMapOf<String, Any>(
                    "conversationId" to conversationId,
                    "lastUpdated" to System.currentTimeMillis(),
                    "messageCount" to messages.size,
                    "messages" to messagesData
                )

                // Save to Firestore
                firestoreInstance.collection("conversations")
                    .document(conversationId)
                    .set(conversationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully saved conversation to Firestore: $conversationId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error saving conversation to Firestore", e)
                    }
                    .await()

            } catch (e: Exception) {
                Log.e(TAG, "Error in saveConversationToFirestore", e)
                // Don't rethrow to prevent breaking the chat flow
            }
        }
    }
    
    suspend fun testFirebaseConnection(): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext "Firebase not initialized. Please call initialize() first."
        }
        
        return@withContext try {
            val functionsInstance = functions
            val firestoreInstance = firestore
            
            if (functionsInstance == null && firestoreInstance == null) {
                return@withContext "Both Firebase Functions and Firestore are not available"
            }
            
            val results = mutableListOf<Pair<String, Boolean>>()
            
            // Test Firestore if available
            firestoreInstance?.let { fs ->
                try {
                    val doc = fs.collection("test").document("test").get().await()
                    results.add("Firestore" to true)
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore test failed", e)
                    results.add("Firestore" to false)
                }
            }
            
            // Test Functions if available
            functionsInstance?.let { fn ->
                try {
                    val result = fn.getHttpsCallable("testConnection").call().await()
                    val success = when (val data = result?.data) {
                        is String -> data == "OK"
                        else -> false
                    }
                    results.add("Functions" to success)
                } catch (e: Exception) {
                    Log.e(TAG, "Functions test failed", e)
                    results.add("Functions" to false)
                }
            }
            
            // Generate result message
            val successCount = results.count { it.second }
            val totalTests = results.size
            
            when {
                successCount == totalTests -> "All tests passed (${results.joinToString { it.first }})"
                successCount > 0 -> "Partial success: ${
                    results.filter { it.second }.joinToString(", ") { it.first }
                } passed, ${
                    results.filterNot { it.second }.joinToString(", ") { it.first }
                } failed"
                else -> "All tests failed: ${results.joinToString { it.first }}"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in testFirebaseConnection", e)
            "Connection test failed: ${e.message ?: "Unknown error"}"
        }
    }
}
