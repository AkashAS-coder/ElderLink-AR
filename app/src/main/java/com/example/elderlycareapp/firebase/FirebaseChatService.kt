package com.example.elderlycareapp.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Service class for handling chat-related Firebase operations.
 */
object FirebaseChatService {
    private const val TAG = "FirebaseChatService"
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    /**
     * Gets a summary of pain-related information from the chat history.
     * @return A string containing the pain summary
     */
    suspend fun getPainSummary(): String {
        return try {
            // Call Firebase Function to get pain summary
            val result = functions
                .getHttpsCallable("getPainSummary")
                .call()
                .await()
                .data as? String ?: "No pain summary available"

            Log.d(TAG, "Successfully retrieved pain summary")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pain summary", e)
            "Error retrieving pain summary: ${e.message}"
        }
    }

    /**
     * Clears the conversation context in Firebase.
     * @param userId The ID of the user whose context should be cleared
     */
    suspend fun clearConversationContext(userId: String) {
        try {
            // Call Firebase Function to clear conversation context
            functions
                .getHttpsCallable("clearConversationContext")
                .call(mapOf("userId" to userId))
                .await()

            Log.d(TAG, "Successfully cleared conversation context for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing conversation context", e)
            throw e
        }
    }

    /**
     * Sends a message to the AI and gets a response.
     * @param message The user's message
     * @param userId The ID of the user sending the message
     * @param context Additional context for the AI (values will be converted to strings)
     * @return The AI's response
     */
    suspend fun sendMessageToAI(
        message: String,
        userId: String,
        context: Map<String, Any> = emptyMap()
    ): String {
        return try {
            val data = mutableMapOf<String, String>(
                "message" to message,
                "userId" to userId
            )

            // Convert context values to strings and add them
            context.forEach { (key, value) ->
                data[key] = value.toString()
            }

            // Call Firebase Function to get AI response
            val result = functions
                .getHttpsCallable("chatWithAI")
                .call(data)
                .await()
                .data as? String ?: "I'm sorry, I couldn't process your request."

            Log.d(TAG, "Successfully got AI response")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to AI", e)
            "I'm sorry, there was an error processing your message: ${e.message}"
        }
    }

    /**
     * Saves a chat message to Firestore.
     * @param userId The ID of the user who sent the message
     * @param message The message content
     * @param isFromUser Whether the message is from the user (true) or the AI (false)
     */
    suspend fun saveChatMessage(userId: String, message: String, isFromUser: Boolean) {
        try {
            val messageData = hashMapOf(
                "message" to message,
                "isFromUser" to isFromUser,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("users")
                .document(userId)
                .collection("messages")
                .add(messageData)
                .await()

            Log.d(TAG, "Successfully saved chat message")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat message", e)
            throw e
        }
    }
}