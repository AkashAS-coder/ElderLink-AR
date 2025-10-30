package com.example.elderlycareapp.model

/**
 * Data class representing a chat message in the conversation.
 * @property text The content of the message
 * @property isUser Whether the message is from the user (true) or the AI (false)
 * @property timestamp When the message was sent
 * @property roomId The ID of the chat room this message belongs to
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val roomId: String = ""
) {
    companion object {
        // Room IDs
        const val ROOM_EXERCISE = "exercise_chat"
        const val ROOM_COMPANION = "companion_chat"
        
        /**
         * Creates a user message in the specified room
         */
        fun userMessage(text: String, roomId: String = "") = ChatMessage(text, true, System.currentTimeMillis(), roomId)

        /**
         * Creates an AI message in the specified room
         */
        fun aiMessage(text: String, roomId: String = "") = ChatMessage(text, false, System.currentTimeMillis(), roomId)
    }
    
    /**
     * Returns true if this message belongs to the exercise chat room
     */
    fun isExerciseChat() = roomId == ROOM_EXERCISE
    
    /**
     * Returns true if this message belongs to the companion chat room
     */
    fun isCompanionChat() = roomId == ROOM_COMPANION
}
