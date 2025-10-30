package com.example.elderlycareapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.pose.PoseLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Helper function to draw a joint connection with adaptive width based on confidence
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConnection(
    start: Offset,
    end: Offset,
    startConfidence: Float,
    endConfidence: Float,
    baseWidth: Float,
    baseAlpha: Float = 0.8f
) {
    val confidence = (startConfidence + endConfidence) / 2f
    drawLine(
        color = Color.Cyan.copy(alpha = baseAlpha * confidence),
        start = start,
        end = end,
        strokeWidth = baseWidth * confidence
    )
}

/**
 * Helper function to draw a landmark point with adaptive size based on confidence
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLandmark(
    center: Offset,
    confidence: Float,
    baseRadius: Float,
    baseAlpha: Float = 0.9f
) {
    // Inner dot
    drawCircle(
        color = Color.Green.copy(alpha = baseAlpha * confidence),
        radius = baseRadius * confidence,
        center = center
    )
    // Outer ring
    drawCircle(
        color = Color.White.copy(alpha = baseAlpha * confidence),
        radius = (baseRadius + 2f) * confidence,
        center = center,
        style = Stroke(width = 2f)
    )
}

/**
 * Draw the MLKit pose skeleton with proper anatomical connections and dynamic sizing
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPoseSkeleton(
    landmarks: Map<Int, Triple<Float, Float, Float>>, // x, y, confidence
    size: androidx.compose.ui.geometry.Size
) {
    // Compute visible body bounds for dynamic sizing
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    
    landmarks.values.forEach { (x, y, _) ->
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }
    
    val bboxWidth = ((maxX - minX) * size.width).coerceAtLeast(1f)
    val bboxHeight = ((maxY - minY) * size.height).coerceAtLeast(1f)
    
    // Scale visual elements based on body size
    val baseRadius = ((bboxWidth + bboxHeight) * 0.005f).coerceIn(4f, 12f)
    val baseWidth = (bboxWidth * 0.008f).coerceIn(2f, 8f)

    // Define MLKit anatomical connections
    val connections = listOf(
        // Face outline
        PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE_OUTER,
        PoseLandmark.RIGHT_EYE_OUTER to PoseLandmark.RIGHT_EYE,
        PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE_INNER,
        PoseLandmark.RIGHT_EYE_INNER to PoseLandmark.NOSE,
        PoseLandmark.NOSE to PoseLandmark.LEFT_EYE_INNER,
        PoseLandmark.LEFT_EYE_INNER to PoseLandmark.LEFT_EYE,
        PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE_OUTER,
        
        // Upper body core
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        
        // Arms
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        
        // Legs
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
        
        // Feet
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
        PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
        PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX
    )

    // Draw connections with confidence-based styling
    connections.forEach { (startId, endId) ->
        val start = landmarks[startId]
        val end = landmarks[endId]
        if (start != null && end != null) {
            drawConnection(
                start = Offset(start.first * size.width, start.second * size.height),
                end = Offset(end.first * size.width, end.second * size.height),
                startConfidence = start.third,
                endConfidence = end.third,
                baseWidth = baseWidth
            )
        }
    }

    // Draw landmarks with confidence-based styling
    landmarks.forEach { (_, landmark) ->
        val (x, y, confidence) = landmark
        drawLandmark(
            center = Offset(x * size.width, y * size.height),
            confidence = confidence,
            baseRadius = baseRadius
        )
    }
}