package com.example.elderlycareapp.model

import java.util.*

data class ExerciseSession(
    val id: String = UUID.randomUUID().toString(),
    val exerciseType: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val feedbackHistory: MutableList<ExerciseFeedback> = mutableListOf(),
    var isCompleted: Boolean = false
) {
    fun addFeedback(feedback: String, timestamp: Long = System.currentTimeMillis()) {
        feedbackHistory.add(ExerciseFeedback(feedback, timestamp))
    }

    fun complete() {
        endTime = System.currentTimeMillis()
        isCompleted = true
    }
}

data class ExerciseFeedback(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
