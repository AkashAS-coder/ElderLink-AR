package com.example.elderlycareapp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.elderlycareapp.model.ChatMessage
import com.example.elderlycareapp.model.ExerciseSession
import com.example.elderlycareapp.ui.screens.CameraScreen
import com.example.elderlycareapp.ui.screens.CompanionChatbotScreen
import com.example.elderlycareapp.ui.screens.ExerciseChatbotScreen
import com.example.elderlycareapp.ui.screens.HomeScreen
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "ElderlyCareApp"

// Navigation destinations
object AppDestinations {
    const val HOME = "home"
    const val EXERCISE_CHATBOT = "exercise_chatbot"
    const val COMPANION_CHATBOT = "companion_chatbot"
    const val CAMERA = "camera/{exerciseType}"
    
    fun getCameraRoute(exerciseType: String = "default"): String {
        return "camera/$exerciseType"
    }
}

// Data classes
class ExerciseSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val exerciseType: String = "General",
    private val feedbackList: MutableList<String> = mutableListOf()
) {
    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    val feedback: List<String>
        get() = feedbackList.toList()

    val isCompleted: Boolean
        get() = endTime != null

    fun addFeedback(feedback: String) {
        feedbackList.add(feedback)
    }

    fun complete() {
        endTime = System.currentTimeMillis()
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    // Text-to-speech
    private lateinit var tts: TextToSpeech
    private var currentTtsPitch = 0.9f  // Slightly lower pitch for elderly users
    private var currentTtsRate = 0.7f  // Slower rate for better comprehension

    // Camera and Pose Analysis
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseAnalyzer: PoseAnalyzer
    private var currentExerciseType: String? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Camera state
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private lateinit var previewView: androidx.camera.view.PreviewView

    // Exercise
    private var currentExerciseSession: ExerciseSession? = null
    private var exerciseStartTime: Long = 0L
    private val exerciseFeedback: MutableList<String> = mutableListOf()
    private var isExerciseActive = false
    private var showExerciseScreen by mutableStateOf(false)
    private var currentExercise by mutableStateOf<String?>(null)

    // UI state
    private var _isExerciseInProgress = false
    var isExerciseInProgress: Boolean
        get() = _isExerciseInProgress
        private set(value) {
            _isExerciseInProgress = value
        }

    // Chat state
    private val _chatMessages = mutableStateListOf<ChatMessage>()
    
    // Get messages for a specific room
    internal fun getMessagesForRoom(roomId: String): List<ChatMessage> {
        return _chatMessages.filter { it.roomId == roomId }
    }

    // Clear chat for a specific room
    internal fun clearChat(roomId: String) {
        _chatMessages.removeAll { it.roomId == roomId }
    }
    
    // Processing state
    private val _isProcessing = mutableStateOf(false)
    val isProcessing: Boolean
        get() = _isProcessing.value

    private fun setProcessing(value: Boolean) {
        _isProcessing.value = value
    }

    internal fun startExerciseSession(exerciseType: String) {
        currentExerciseType = exerciseType
        exerciseStartTime = System.currentTimeMillis()
        exerciseFeedback.clear()
        isExerciseActive = false // Enable after countdown completes
        showExerciseScreen = true
        currentExerciseSession = ExerciseSession(
            id = System.currentTimeMillis().toString(),
            startTime = exerciseStartTime,
            exerciseType = exerciseType
        )
    }

    internal fun beginExerciseAnalysis() {
        if (isExerciseActive) return
        isExerciseActive = true
        exerciseStartTime = System.currentTimeMillis()
        currentExerciseType?.let { type ->
            if (::poseAnalyzer.isInitialized) {
                poseAnalyzer.startContinuousUpdates(type)
            }
        }
    }

    internal fun endExerciseSession() {
        currentExerciseSession?.let { session ->
            session.complete()
            exerciseFeedback.forEach { feedback ->
                session.addFeedback(feedback)
            }
            // Here you can save the session to your database
        }
        
        // Stop any ongoing TTS
        stopTTS()
        
        // Disable continuous updates
        if (::poseAnalyzer.isInitialized) {
            poseAnalyzer.disableContinuousUpdates()
        }

        isExerciseActive = false
        showExerciseScreen = false
        currentExerciseType = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Exercise Reminder"
            val descriptionText = "Channel for exercise reminders"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("exercise_reminder", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun processImageForPose(imageProxy: ImageProxy) {
        Log.d("CameraDebug", "Processing image. isExerciseActive: $isExerciseActive")
        
        // Only process images when exercise is active AND analysis is enabled
        if (!isExerciseActive) {
            Log.d("CameraDebug", "Skipping image processing - exercise not active")
            imageProxy.close()
            return
        }

        try {
            Log.d("CameraDebug", "Converting ImageProxy to Bitmap")
            val bitmap = imageProxy.toBitmap()
            Log.d("CameraDebug", "Bitmap created: ${bitmap.width}x${bitmap.height}")

            val exerciseTypeSafe = currentExerciseType ?: "chair_squat"
            Log.d("CameraDebug", "Exercise type: $exerciseTypeSafe")

            if (::poseAnalyzer.isInitialized) {
                Log.d("CameraDebug", "PoseAnalyzer initialized, analyzing pose...")
                
                // Process the image for pose detection
                poseAnalyzer.analyzePose(bitmap,
                    onResult = { analysis ->
                        Log.d("PoseFeedback", "Analysis result: ${analysis.feedback}")
                        analysisResultText = analysis.feedback
                    },
                    onError = { exception ->
                        Log.e("PoseFeedback", "Analysis error", exception)
                    }
                )
                
                // Update pose landmarks for visualization - only update if pose is detected
                poseAnalyzer.getLastPose()?.let { pose ->
                    val landmarks = mutableMapOf<Int, Pair<Float, Float>>()
                    val width = bitmap.width.coerceAtLeast(1)
                    val height = bitmap.height.coerceAtLeast(1)
                    val widthF = width.toFloat()
                    val heightF = height.toFloat()
                    
                    // Filter landmarks by visibility - extremely lenient for skeleton visualization
                    pose.allPoseLandmarks.forEach { landmark: com.google.mlkit.vision.pose.PoseLandmark ->
                        // Very low threshold for maximum skeleton visibility
                        if (landmark.inFrameLikelihood > 0.05f) { // Extremely lenient
                            val normalizedX = (landmark.position.x / widthF).coerceIn(0f, 1f)
                            val normalizedY = (landmark.position.y / heightF).coerceIn(0f, 1f)
                            landmarks[landmark.landmarkType] = Pair(normalizedX, normalizedY)
                        }
                    }
                    
                    // Update the camera screen with landmarks on UI thread
                    runOnUiThread {
                        if (landmarks.isNotEmpty()) {
                            poseLandmarks = landmarks
                            Log.d("PoseLandmarks", "Updated ${landmarks.size} visible landmarks")
                        } else {
                            Log.d("PoseLandmarks", "No visible landmarks detected")
                        }
                    }
                }
            } else {
                Log.e("CameraDebug", "PoseAnalyzer not initialized")
            }
        } catch (e: Exception) {
            Log.e("CameraDebug", "Error processing image", e)
        } finally {
            try {
                imageProxy.close()
            } catch (e: Exception) {
                Log.e("CameraDebug", "Error closing imageProxy", e)
            }
        }
    }
    
    // TTS state management
    private var isTtsSpeaking = false
    private var lastTtsTime = 0L
    private val minTtsInterval = 4000L // 4 seconds minimum between TTS calls
    private var lastFeedbackTime = 0L
    private val feedbackInterval = 4000L // Exactly 4 seconds between feedback
    
    public fun speakAIResponse(response: String) {
        if (response.isBlank()) return
        
        val currentTime = System.currentTimeMillis()
        
        // For exercise instructions and countdown, allow immediate speech
        val isExerciseInstruction = response.contains("Let's do") || 
                                  response.contains("Begin!") || 
                                  response.matches(Regex("\\d+")) ||
                                  response.contains("Welcome to the Exercise Assistant") ||
                                  response.contains("These are the steps") ||
                                  response.contains("Get ready!") ||
                                  response.contains("camera will track") ||
                                  response.contains("Starting") ||
                                  response.contains("Stand in front") ||
                                  response.contains("Stand facing") ||
                                  response.contains("Start on your hands") ||
                                  response.contains("Stand on one leg") ||
                                  response.contains("Lie on your back") ||
                                  response.contains("Stand with feet") ||
                                  response.contains("Starting in")
        
        // For feedback during exercise, enforce 4-second intervals
        // Add more comprehensive feedback detection
        val isExerciseFeedback = !isExerciseInstruction && (
            response.contains("knee", ignoreCase = true) || 
            response.contains("back", ignoreCase = true) || 
            response.contains("hip", ignoreCase = true) || 
            response.contains("shoulder", ignoreCase = true) || 
            response.contains("elbow", ignoreCase = true) ||
            response.contains("arm", ignoreCase = true) ||
            response.contains("foot", ignoreCase = true) ||
            response.contains("body", ignoreCase = true) ||
            response.contains("Great", ignoreCase = true) ||
            response.contains("Perfect", ignoreCase = true) ||
            response.contains("Excellent", ignoreCase = true) ||
            response.contains("Good job", ignoreCase = true) ||
            response.contains("no issue", ignoreCase = true) ||
            response.contains("Keep", ignoreCase = true) ||
            response.contains("Maintain", ignoreCase = true) ||
            response.contains("Focus", ignoreCase = true) ||
            response.contains("Try", ignoreCase = true) ||
            response.contains("Don't", ignoreCase = true) ||
            response.contains("Straight", ignoreCase = true) ||
            response.contains("Bend", ignoreCase = true) ||
            response.contains("Align", ignoreCase = true) ||
            response.contains("visible", ignoreCase = true) ||
            response.contains("frame", ignoreCase = true)
        )
        
        if (isExerciseFeedback) {
            // Enforce 4-second intervals for exercise feedback
            if (currentTime - lastFeedbackTime < feedbackInterval) {
                Log.d(TAG, "Skipping feedback - too soon (${currentTime - lastFeedbackTime}ms since last feedback)")
                return
            }
            lastFeedbackTime = currentTime
        }
        
        // Check if we should skip this TTS call due to timing (but allow exercise instructions)
        if (!isExerciseInstruction || currentTime - lastTtsTime >= 1000) {
            runOnUiThread {
                currentFeedback = response
                if (::tts.isInitialized) {
                    if (isExerciseInstruction) {
                        // Stop current speech and start new one for exercise instructions
                        tts.stop()
                        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
                    } else {
                        // Queue for regular feedback
                        tts.speak(response, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
                    }
                    lastTtsTime = currentTime
                    isTtsSpeaking = true
                    Log.d(TAG, "TTS started: $response")
                }
            }
        } else {
            Log.d(TAG, "Skipping TTS - too soon since last call (${currentTime - lastTtsTime}ms)")
        }
    }
    
    // Method to stop TTS when needed
    public fun stopTTS() {
        runOnUiThread {
            if (::tts.isInitialized && isTtsSpeaking) {
                tts.stop()
                isTtsSpeaking = false
                Log.d(TAG, "TTS stopped")
            }
        }
    }

    // Send a message to the Firebase chat service and append the AI response
    private fun sendChatMessage(message: String, roomId: String) {
        coroutineScope.launch {
            try {
                setProcessing(true)
                // Add user message to the chat
                _chatMessages.add(ChatMessage.userMessage(message, roomId))
                
                // Use FirebaseChatService for AI responses
                val response = FirebaseChatService.getChatResponse(message, roomId)
                
                // Add AI response to the chat
                _chatMessages.add(ChatMessage.aiMessage(response, roomId))
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending chat message", e)
                _chatMessages.add(ChatMessage.aiMessage("Sorry, I couldn't reach the chat service. Please try again.", roomId))
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Chat service error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                setProcessing(false)
            }
        }
    }

    // Analysis results
    private var _analysisResultText = ""
    var analysisResultText: String
        get() = _analysisResultText
        private set(value) {
            _analysisResultText = value
        }

    // User feedback
    private var _currentFeedback = ""
    var currentFeedback: String
        get() = _currentFeedback
        private set(value) {
            _currentFeedback = value
        }
    
    // Pose landmarks for visualization
    private var _poseLandmarks = mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap())
    var poseLandmarks: Map<Int, Pair<Float, Float>>
        get() = _poseLandmarks.value
        private set(value) {
            _poseLandmarks.value = value
        }

    // Analysis timing
    private var lastAnalysisTime = 0L
    private val analysisInterval = 1000L

    companion object {
        private const val TAG = "MainActivity"
        internal const val CHANNEL_ID = "elderly_care_reminders"
        internal const val NOTIFICATION_ID = 1
        private const val REMINDER_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
    }

    // Coroutine
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        try {
            // Initialize TTS
            tts = TextToSpeech(this, this)

            // Initialize camera executor
            cameraExecutor = Executors.newSingleThreadExecutor()

            // Initialize pose analyzer with TTS callback
            poseAnalyzer = PoseAnalyzer(this)
            poseAnalyzer.initializePoseAnalyzer(
                callback = { feedback ->
                    // Update UI with feedback
                    runOnUiThread {
                        currentFeedback = feedback
                    }
                },
                ttsCallback = { feedback ->
                    // Use TTS to speak the feedback
                    if (feedback.isNotBlank()) {
                        speakAIResponse(feedback)
                    }
                }
            )

            // Initialize Firebase chat service in background
            coroutineScope.launch {
                try {
                    FirebaseChatService.initialize(this@MainActivity)
                    Log.d(TAG, "FirebaseChatService initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize FirebaseChatService", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Firebase chat initialization failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }


            // Create notification channel
            createNotificationChannel()

            // Compose UI
            setContent {
                MaterialTheme {
                    // Navigation
                    val navController = rememberNavController()

                    // Local state for the UI
                    val chatMessages by remember { derivedStateOf { _chatMessages.toList() } }
                    val isProcessing by remember { derivedStateOf { _isProcessing.value } }

                    NavHost(navController = navController, startDestination = AppDestinations.HOME) {
                        composable(AppDestinations.HOME) {
                            HomeScreen(
                                navController = navController,
                                activity = this@MainActivity,
                                onStartExercise = { exerciseType ->
                                    // Just navigate to exercise chatbot without starting exercise automatically
                                    // Clear any previous exercise chat when opening
                                    clearChat(ChatMessage.ROOM_EXERCISE)
                                    // Add a welcome message to the exercise chat
                                    _chatMessages.add(ChatMessage.aiMessage(
                                        "Hello! I'm your exercise assistant. I can help you with exercise guidance, form tips, and motivation. What would you like to know about exercises?",
                                        ChatMessage.ROOM_EXERCISE
                                    ))
                                    navController.navigate(AppDestinations.EXERCISE_CHATBOT)
                                },
                                onStartCameraExercise = { exerciseType ->
                                    startExerciseSession(exerciseType)
                                    // Navigate to camera with exercise type parameter
                                    navController.navigate(AppDestinations.getCameraRoute(exerciseType))
                                },
                                onOpenCompanion = {
                                    // Clear any previous companion chat when opening
                                    clearChat(ChatMessage.ROOM_COMPANION)
                                    // Add a welcome message to the companion chat
                                    _chatMessages.add(ChatMessage.aiMessage(
                                        "Hello! I'm your companion. How can I assist you today?",
                                        ChatMessage.ROOM_COMPANION
                                    ))
                                    navController.navigate(AppDestinations.COMPANION_CHATBOT)
                                }
                            )
                        }

                        composable(AppDestinations.EXERCISE_CHATBOT) {
                            ExerciseChatbotScreen(
                                navController = navController,
                                activity = this@MainActivity,
                                onBack = { navController.popBackStack() },
                                onEndExercise = {
                                    endExerciseSession()
                                    navController.popBackStack()
                                },
                                onSendMessage = { message ->
                                    sendChatMessage(message, ChatMessage.ROOM_EXERCISE)
                                },
                                chatMessages = getMessagesForRoom(ChatMessage.ROOM_EXERCISE),
                                isProcessing = isProcessing,
                                roomId = ChatMessage.ROOM_EXERCISE
                            )
                        }

                        composable(AppDestinations.COMPANION_CHATBOT) {
                            CompanionChatbotScreen(
                                navController = navController,
                                activity = this@MainActivity,
                                onBack = { navController.popBackStack() },
                                onSendMessage = { message ->
                                    sendChatMessage(message, ChatMessage.ROOM_COMPANION)
                                },
                                chatMessages = getMessagesForRoom(ChatMessage.ROOM_COMPANION),
                                isProcessing = isProcessing,
                                roomId = ChatMessage.ROOM_COMPANION
                            )
                        }

                        composable(
                            route = AppDestinations.CAMERA,
                            arguments = listOf(
                                navArgument("exerciseType") {
                                    type = NavType.StringType
                                    defaultValue = "default"
                                }
                            )
                        ) { backStackEntry ->
                            val exerciseType = backStackEntry.arguments?.getString("exerciseType") ?: "default"
                            CameraScreen(
                                exerciseType = exerciseType,
                                navController = navController,
                                onBack = { 
                                    // End exercise session and stop all analysis/voiceover
                                    endExerciseSession()
                                    navController.popBackStack() 
                                },
                                onCapture = { image -> processImageForPose(image) },
                                onStop = {
                                    // End exercise session and stop all analysis/voiceover
                                    endExerciseSession()
                                    navController.popBackStack()
                                },
                                isExerciseActive = isExerciseActive,
                                externalPoseLandmarks = poseLandmarks
                            )
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during onCreate initialization", e)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // Minimal fallback UI so the app doesn't crash to home
            setContent {
                MaterialTheme {
                    androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        androidx.compose.material3.Text(text = "App failed to initialize. See logs.")
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS: Language not supported")
            } else {
                tts.setPitch(currentTtsPitch)
                tts.setSpeechRate(currentTtsRate)
                
                // Set up TTS listener to track when speech finishes
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started: $utteranceId")
                        isTtsSpeaking = true
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS finished: $utteranceId")
                        isTtsSpeaking = false
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error: $utteranceId")
                        isTtsSpeaking = false
                    }
                })
                
                Log.d(TAG, "TTS initialized with pitch: $currentTtsPitch, rate: $currentTtsRate")
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        cameraExecutor.shutdown()
        if (::poseAnalyzer.isInitialized) {
            poseAnalyzer.release()
        }
    }

    // ActivityResultLauncher for camera permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Camera permission granted, start camera
            if (::previewView.isInitialized) {
                startCamera()
            }
        } else {
            Toast.makeText(
                this, 
                "Camera permission is required for exercise analysis",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Camera setup
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Set up the capture use case to allow taking photos
            imageCapture = ImageCapture.Builder().build()

            // Set up the image analysis use case for pose detection
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageForPose(imageProxy)
                    }
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer
                    )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    // Note: imageProxy instances are closed inside the analyzer callback (processImageForPose)
    }

    // Extension function to convert ImageProxy to Bitmap
    // Renamed to toBitmapCompat to avoid shadowing the member function
    private fun ImageProxy.toBitmapCompat(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y buffer is first
        yBuffer.get(nv21, 0, ySize)

        // Interleave U and V
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            this.width,
            this.height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, this.width, this.height),
            100,
            out
        )
        val imageBytes = out.toByteArray()

        // Rotate the bitmap if needed
        val matrix = Matrix()
        matrix.postRotate(this.imageInfo.rotationDegrees.toFloat())

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).let {
            Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
        }
    }

    // Helper class for image compression
    private class JpegCompressor(
        private val data: ByteArray,
        private val format: Int,
        private val width: Int,
        private val height: Int,
        private val strides: IntArray?
    ) {
        fun compressToJpeg(
            rect: android.graphics.Rect,
            quality: Int,
            stream: java.io.OutputStream
        ) {
            val yuvImage = android.graphics.YuvImage(
                data, format, width, height, strides
            )
            yuvImage.compressToJpeg(rect, quality, stream)
        }
    }

    // Function to set reminder
    private fun setReminder(hour: Int, minute: Int, requestCode: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            action = Constants.REMINDER_ACTION
            putExtra("requestCode", requestCode)
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Constants.REMINDER_ACTION) {
                val builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Elderly Care Reminder")
                    .setContentText("Time for your daily check-in or exercise!")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

                NotificationManagerCompat.from(context)
                    .notify(MainActivity.NOTIFICATION_ID, builder.build())
            }
        }
    }
}