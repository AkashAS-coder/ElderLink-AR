package com.example.elderlycareapp // Use your app's package name

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class MyImageAnalyzer(private val onFrameAnalyzed: (String) -> Unit) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = java.lang.System.currentTimeMillis()
        // Optional: Implement frame throttling to avoid over-processing
        // if (currentTime - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) { // Example: process once per second

        val image = imageProxy.image // Get the underlying Image
        if (image != null) {
            // --- Your Image Processing Logic Goes Here ---
            // For example, convert to Bitmap, pass to an ML model, etc.
            // This example just logs information.
            val analysisResult = "Timestamp: ${imageProxy.imageInfo.timestamp}, " +
                    "Rotation: ${imageProxy.imageInfo.rotationDegrees}, " +
                    "Format: ${image.format}, " +
                    "Size: ${image.width}x${image.height}"
            Log.d("MyImageAnalyzer", analysisResult)
            onFrameAnalyzed(analysisResult) // Pass some result back to the UI if needed
            // lastAnalyzedTimestamp = currentTime
        }

        // IMPORTANT: You MUST close the ImageProxy when you're done with it.
        // This releases the image and allows CameraX to provide the next one.
        imageProxy.close()
    }
}
