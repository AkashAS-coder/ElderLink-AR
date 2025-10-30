package com.example.elderlycareapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.util.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object EmulatorCameraService {
    private const val TAG = "EmulatorCameraService"
    
    fun isEmulator(): Boolean {
        return try {
            android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.startsWith("unknown") ||
            android.os.Build.MODEL.contains("google_sdk") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.MODEL.contains("Android SDK built for x86") ||
            android.os.Build.MANUFACTURER.contains("Genymotion") ||
            (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == android.os.Build.PRODUCT
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if emulator", e)
            false
        }
    }
    
    suspend fun generateTestFrame(width: Int = 640, height: Int = 480): Bitmap {
        return withContext(Dispatchers.Default) {
            try {
                // Ensure minimum size for ML Kit compatibility (32x32)
                val actualWidth = maxOf(width, 64)
                val actualHeight = maxOf(height, 64)
                val bitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.FILL
                }
                
                // Fill background
                canvas.drawRect(Rect(0, 0, width, height), paint)
                
                // Draw some test patterns
                paint.color = Color.BLUE
                canvas.drawCircle(width / 2f, height / 2f, 50f, paint)
                
                paint.color = Color.RED
                canvas.drawRect(Rect(50, 50, 150, 150), paint)
                
                paint.color = Color.GREEN
                canvas.drawRect(Rect(width - 150, height - 150, width - 50, height - 50), paint)
                
                // Add timestamp
                paint.color = Color.BLACK
                paint.textSize = 30f
                val timestamp = System.currentTimeMillis()
                canvas.drawText("Emulator Frame: $timestamp", 20f, 40f, paint)
                
                // Simulate processing delay
                delay(100)
                
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error generating test frame", e)
                // Return a simple fallback bitmap
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
        }
    }
    
    fun getEmulatorCameraInfo(): String {
        return """
            Emulator Camera Mode Active
            
            This device appears to be running in an emulator environment.
            Camera functionality is limited to test frames.
            
            To test with real camera:
            1. Use a physical Android device
            2. Ensure camera permissions are granted
            3. Check that camera hardware is available
            
            Current test mode provides simulated frames for development.
        """.trimIndent()
    }
}
