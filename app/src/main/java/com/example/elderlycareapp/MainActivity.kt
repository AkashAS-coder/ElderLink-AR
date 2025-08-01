@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.elderlycareapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AppDestinations {
    const val HOME = "home"
    const val EXERCISE_CHATBOT = "exercise_chatbot"
    const val COMPANION_CHATBOT = "companion_chatbot"
    const val CAMERA = "camera"
}

private val sharedOkHttpClient = OkHttpClient()

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

class MyImageAnalyzer(
    private val onFrameAnalyzed: (String) -> Unit,
    private val onGeminiAnalysis: ((Bitmap) -> Unit)? = null,
    private val isAnalysisActive: Boolean = false
) : ImageAnalysis.Analyzer {
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        Log.d("MyImageAnalyzer", "=== IMAGE ANALYZER CALLED ===")
        Log.d("MyImageAnalyzer", "Image timestamp: ${imageProxy.imageInfo.timestamp}")
        Log.d("MyImageAnalyzer", "Image rotation: ${imageProxy.imageInfo.rotationDegrees}")
        
        val image = imageProxy.image
        if (image != null) {
            Log.d("MyImageAnalyzer", "Image dimensions: ${image.width}x${image.height}")
            val analysisResult = "Timestamp: ${imageProxy.imageInfo.timestamp}, " +
                    "Rotation: ${imageProxy.imageInfo.rotationDegrees}, " +
                    "Width: ${image.width}, Height: ${image.height}"
            onFrameAnalyzed(analysisResult)
            
            Log.d("MyImageAnalyzer", "isAnalysisActive: $isAnalysisActive")
            Log.d("MyImageAnalyzer", "onGeminiAnalysis callback is null: ${onGeminiAnalysis == null}")
            
            // Send frame to Gemini AI for analysis if active
            if (isAnalysisActive && onGeminiAnalysis != null) {
                try {
                    Log.d("MyImageAnalyzer", "Converting frame to bitmap for Gemini analysis...")
                    val bitmap = imageProxyToBitmap(imageProxy)
                    Log.d("MyImageAnalyzer", "Bitmap created: ${bitmap.width}x${bitmap.height}")
                    Log.d("MyImageAnalyzer", "Calling onGeminiAnalysis callback...")
                    onGeminiAnalysis?.invoke(bitmap)
                    Log.d("MyImageAnalyzer", "onGeminiAnalysis callback completed")
                } catch (e: Exception) {
                    Log.e("MyImageAnalyzer", "Error converting image to bitmap", e)
                }
            } else if (isAnalysisActive) {
                Log.d("MyImageAnalyzer", "Analysis active but callback is null")
            } else {
                Log.d("MyImageAnalyzer", "Analysis not active, skipping Gemini analysis")
            }
        } else {
            Log.d("MyImageAnalyzer", "Image is null, skipping analysis")
        }
        imageProxy.close()
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "reminder_channel"
        const val NOTIFICATION_ID = 101
        const val REMINDER_REQUEST_CODE = 1001
    }

    private lateinit var tts: TextToSpeech
    private lateinit var poseAnalyzer: PoseAnalyzer
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Text-to-Speech
        tts = TextToSpeech(this, this)
        
        // Initialize ML Kit Pose Analyzer
        Log.d("MainActivity", "=== INITIALIZING POSE ANALYZER ===")
        poseAnalyzer = PoseAnalyzer(this)
        Log.d("MainActivity", "PoseAnalyzer instance created")
        
        poseAnalyzer.initializePoseAnalyzer { feedback ->
            Log.d("MainActivity", "Received feedback from pose analyzer: $feedback")
            runOnUiThread {
                onPoseAnalysisResult(feedback)
            }
        }
        Log.d("MainActivity", "Pose analyzer initialization call completed")
        
        createNotificationChannel()

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = AppDestinations.HOME) {
                    composable(AppDestinations.HOME) { HomeScreen(navController, this@MainActivity) }
                    composable(AppDestinations.EXERCISE_CHATBOT) { ExerciseChatbotScreen(navController) }
                    composable(AppDestinations.COMPANION_CHATBOT) { CompanionChatbotScreen(navController) }
                    composable(AppDestinations.CAMERA) { CameraScreen(navController) }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reminder Notifications"
            val descriptionText = "Channel for elderly care reminders"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            pendingIntentFlags
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    Log.w("MainActivity", "Cannot schedule exact alarms. App needs SCHEDULE_EXACT_ALARM permission and user allowance.")
                    Toast.makeText(context, "Cannot schedule exact alarms. Please enable permission in settings.", Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Toast.makeText(context, "Reminder set for ${"%02d".format(hour)}:${"%02d".format(minute)}", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            Log.e("MainActivity", "Failed to schedule reminder. Check SCHEDULE_EXACT_ALARM.", se)
            Toast.makeText(context, "Could not schedule reminder. Permission issue?", Toast.LENGTH_LONG).show()
        }
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("ReminderReceiver", "POST_NOTIFICATIONS permission not granted.")
                    return
                }
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this drawable exists
                .setContentTitle("Elderly Care Reminder")
                .setContentText("Time for your daily check-in or exercise!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                // The permission check here is redundant if done above, but safe.
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Log.w("ReminderReceiver", "Notification permission check failed just before notify.")
                    return
                }
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    private fun initializePoseAnalyzer() {
        try {
            poseAnalyzer = PoseAnalyzer(this)
            Log.d("MainActivity", "Pose analyzer initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize pose analyzer", e)
            Toast.makeText(this, "Failed to initialize pose analyzer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun startExerciseSession(exerciseType: String) {
        Log.d("MainActivity", "=== STARTING POSE ANALYSIS SESSION ===")
        Log.d("MainActivity", "Exercise type: $exerciseType")
        
        if (!::poseAnalyzer.isInitialized) {
            Log.e("MainActivity", "Pose analyzer not initialized")
            Toast.makeText(this, "Pose analyzer not ready. Please wait...", Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d("MainActivity", "Pose analyzer is initialized, proceeding...")
        
        try {
            Log.d("MainActivity", "Starting pose analysis for $exerciseType")
            
            Toast.makeText(this, "Pose analyzer ready for $exerciseType", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting pose analysis", e)
            Toast.makeText(this, "Failed to start pose analysis: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        Log.d("MainActivity", "=== POSE ANALYSIS SESSION START COMPLETE ===")
    }

    fun sendFrameForAnalysis(frame: Bitmap, exerciseType: String = "exercise") {
        Log.d("MainActivity", "=== SEND FRAME FOR ANALYSIS CALLED ===")
        Log.d("MainActivity", "Frame size: ${frame.width}x${frame.height}")
        Log.d("MainActivity", "Exercise type: $exerciseType")
        
        if (!::poseAnalyzer.isInitialized) {
            Log.e("MainActivity", "Pose analyzer not initialized")
            return
        }
        
        Log.d("MainActivity", "Pose analyzer is initialized, calling analyzePose...")
        
        // Use ML Kit pose analysis (real-time detection!)
        poseAnalyzer.analyzePose(frame, exerciseType)
        
        Log.d("MainActivity", "analyzePose call completed")
    }
    
    // Callback for pose analysis results
    fun onPoseAnalysisResult(feedback: String) {
        Log.d("MainActivity", "=== POSE ANALYSIS RESULT CALLBACK ===")
        Log.d("MainActivity", "Feedback received: $feedback")
        handleAnalysisResponse(feedback)
    }

    private fun handleAnalysisResponse(feedback: String) {
        Log.d("MainActivity", "=== HANDLING ANALYSIS RESPONSE ===")
        Log.d("MainActivity", "Feedback to handle: $feedback")
        
        if (feedback.isNotEmpty()) {
            Log.d("MainActivity", "Processing non-empty feedback")
            // Speak the feedback to the user
            tts.speak(feedback, TextToSpeech.QUEUE_FLUSH, null, null)
            
            // Show a brief toast for visual feedback
            Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show()
        } else {
            Log.d("MainActivity", "Empty feedback received, skipping processing")
        }
    }
    

    

    
    fun testPoseAnalysis() {
        Log.d("MainActivity", "=== POSE ANALYSIS TEST START ===")
        
        if (!::poseAnalyzer.isInitialized) {
            Log.e("MainActivity", "Pose analyzer not initialized")
            Toast.makeText(this, "Pose analyzer not ready. Please wait...", Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d("MainActivity", "Pose analyzer is initialized, testing...")
        
        // Create a test bitmap (1x1 pixel) for testing
        val testBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("MainActivity", "Testing pose analysis...")
                poseAnalyzer.analyzePose(testBitmap, "test")
                Log.d("MainActivity", "Pose analysis test completed successfully")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "✅ Pose analysis working!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Pose analysis test failed", e)
                Log.e("MainActivity", "Exception type: ${e.javaClass.simpleName}")
                Log.e("MainActivity", "Exception message: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Pose analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        Log.d("MainActivity", "=== POSE ANALYSIS TEST END ===")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("MainActivity", "TTS language not supported")
            }
        } else {
            Log.e("MainActivity", "TTS initialization failed")
        }
    }


    
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::poseAnalyzer.isInitialized) {
            poseAnalyzer.release()
        }
        super.onDestroy()
    }
}

// Utility function to convert ImageProxy to Bitmap
fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val yBuffer = imageProxy.planes[0].buffer
    val uBuffer = imageProxy.planes[1].buffer
    val vBuffer = imageProxy.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

@Composable
fun HomeScreen(navController: NavHostController, activity: MainActivity) {
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var pickedMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var reminderSetMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                activity.scheduleReminder(context.applicationContext, pickedHour, pickedMinute)
                reminderSetMessage = "Reminder set for ${"%02d".format(pickedHour)}:${"%02d".format(pickedMinute)}"
            } else {
                Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_LONG).show()
                reminderSetMessage = "Notification permission needed to set reminders."
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Elderly Care App", style = MaterialTheme.typography.headlineMedium) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { navController.navigate(AppDestinations.EXERCISE_CHATBOT) }, modifier = Modifier.fillMaxWidth()) {
                Text("Exercise Chatbot", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { navController.navigate(AppDestinations.COMPANION_CHATBOT) }, modifier = Modifier.fillMaxWidth()) {
                Text("Companion Chatbot", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(32.dp))
            Text("Set daily reminder time:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Pick Time (${"%02d".format(pickedHour)}:${"%02d".format(pickedMinute)})", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                            PackageManager.PERMISSION_GRANTED -> {
                                activity.scheduleReminder(context.applicationContext, pickedHour, pickedMinute)
                                reminderSetMessage = "Reminder set for ${"%02d".format(pickedHour)}:${"%02d".format(pickedMinute)}"
                            }
                            else -> {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    } else {
                        activity.scheduleReminder(context.applicationContext, pickedHour, pickedMinute)
                        reminderSetMessage = "Reminder set for ${"%02d".format(pickedHour)}:${"%02d".format(pickedMinute)}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !showTimePicker
            ) {
                Text("Set Reminder Notification", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            if (reminderSetMessage.isNotEmpty()) {
                Text(reminderSetMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            if (showTimePicker) {
                TimePickerDialog(
                    initialHour = pickedHour,
                    initialMinute = pickedMinute,
                    onTimeSelected = { hour, minute ->
                        pickedHour = hour
                        pickedMinute = minute
                        showTimePicker = false
                    },
                    onDismissRequest = { showTimePicker = false }
                )
            }
        }
    }
}

@Composable
fun ExerciseChatbotScreen(navController: NavHostController) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Exercise", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(8.dp),
                reverseLayout = true
            ) {
                items(messages.asReversed()) { msg -> // Use asReversed for chronological order
                    MessageBubble(msg)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    placeholder = { Text("Ask about neck, back, or leg exercises...", style = MaterialTheme.typography.bodyLarge) },
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        val userMessage = ChatMessage(inputText, true)
                        messages.add(userMessage)
                        // Remove hardcoded logic and use Gemini API
                        streamChatResponse(
                            inputText,
                            onChunkReceived = { chunk ->
                                messages.add(ChatMessage(chunk, false))
                            }
                        )
                        inputText = ""
                    }
                }) {
                    Text("Send", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(AppDestinations.CAMERA) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Guided Camera Exercise", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun CompanionChatbotScreen(navController: NavHostController) {
    var userInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Companion Chatbot", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                reverseLayout = true
            ) {
                items(messages.asReversed()) { msg ->
                    MessageBubble(msg)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask something...", style = MaterialTheme.typography.bodyLarge) },
                    enabled = !isLoading,
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (userInput.isNotBlank()) {
                            val userMsg = ChatMessage(userInput, true)
                            messages.add(userMsg)
                            val currentInput = userInput
                            isLoading = true
                            
                            streamChatResponse(
                                currentInput,
                                onChunkReceived = { chunk ->
                                    if (messages.lastOrNull()?.isUser == false) {
                                        val lastBotMessage = messages.removeAt(messages.lastIndex)
                                        messages.add(lastBotMessage.copy(text = lastBotMessage.text + chunk))
                                    } else {
                                        messages.add(ChatMessage(chunk, false))
                                    }
                                }
                            )
                            
                            userInput = ""
                            isLoading = false
                        }
                    },
                    enabled = !isLoading && userInput.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

fun streamChatResponse(userInput: String, onChunkReceived: (String) -> Unit) {
    // Replace this URL with your Render deployment URL
    val url = "https://elderlink-ar-backend.onrender.com/chat"
    val requestBody = JSONObject().put("prompt", userInput).toString()
        .toRequestBody("application/json".toMediaType())

    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onChunkReceived("Error: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (text.isNotEmpty()) {
                                onChunkReceived(text)
                            } else {
                                onChunkReceived("No response text received")
                            }
                        } else {
                            onChunkReceived("No content parts found in response")
                        }
                    } else {
                        onChunkReceived("No candidates found in response")
                    }
                } else {
                    onChunkReceived("Empty response body")
                }
            } catch (e: Exception) {
                onChunkReceived("Error parsing response: ${e.message}")
            }
        }
    })
}

@Composable
fun MessageBubble(chatMessage: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (chatMessage.isUser) 64.dp else 8.dp,
                end = if (chatMessage.isUser) 8.dp else 64.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
        horizontalArrangement = if (chatMessage.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = if (chatMessage.isUser) 16.dp else 0.dp,
                topEnd = if (chatMessage.isUser) 0.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (chatMessage.isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = chatMessage.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun CameraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val activity = context as MainActivity

    var hasCamPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
            if (!granted) {
                Toast.makeText(context, "Camera permission denied.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCamPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var analysisResultText by remember { mutableStateOf("Analysis results will appear here.") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var selectedExercise by remember { mutableStateOf("chair_squat") }
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    var exerciseSessionStarted by remember { mutableStateOf(false) }
    
    val imageAnalyzer = remember(exerciseSessionStarted) { 
        Log.d("MainActivity", "Creating new MyImageAnalyzer with exerciseSessionStarted: $exerciseSessionStarted")
        MyImageAnalyzer(
            onFrameAnalyzed = { result -> 
                analysisResultText = result 
            },
            onGeminiAnalysis = { bitmap ->
                Log.d("MainActivity", "=== ON GEMINI ANALYSIS CALLBACK TRIGGERED ===")
                Log.d("MainActivity", "Bitmap size: ${bitmap.width}x${bitmap.height}")
                Log.d("MainActivity", "exerciseSessionStarted: $exerciseSessionStarted")
                Log.d("MainActivity", "isAnalyzing: $isAnalyzing")
                Log.d("MainActivity", "Time since last analysis: ${System.currentTimeMillis() - lastAnalysisTime}ms")
                
                val currentTime = System.currentTimeMillis()
                if (exerciseSessionStarted && !isAnalyzing && (currentTime - lastAnalysisTime) >= 2000) {
                    Log.d("MainActivity", "All conditions met - calling sendFrameForAnalysis")
                    isAnalyzing = true
                    lastAnalysisTime = currentTime
                    activity.sendFrameForAnalysis(bitmap, selectedExercise)
                    // Reset analyzing flag after a delay to prevent too frequent analysis
                    // Frame rate: every 2 seconds as per best practices for cost management
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(2000) // 2 second delay between analyses
                        isAnalyzing = false
                        Log.d("MainActivity", "Analysis cooldown completed, isAnalyzing set to false")
                    }
                } else {
                    Log.d("MainActivity", "Conditions not met for analysis:")
                    Log.d("MainActivity", "  - exerciseSessionStarted: $exerciseSessionStarted")
                    Log.d("MainActivity", "  - !isAnalyzing: ${!isAnalyzing}")
                    Log.d("MainActivity", "  - time >= 2000: ${(currentTime - lastAnalysisTime) >= 2000}")
                }
            },
            isAnalysisActive = exerciseSessionStarted
        ) 
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Camera lens facing state
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI Exercise Coach", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Real-time Pose Detection Notice
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "🎯 Real-time Pose Detection",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Using ML Kit to analyze your actual form and provide live feedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            if (hasCamPermission) {
                // Exercise selection dropdown
                var expanded by remember { mutableStateOf(false) }
                val exercises = listOf(
                    "chair_squat" to "Chair Squat",
                    "wall_pushup" to "Wall Push-up", 
                    "gentle_plank" to "Gentle Plank",
                    "standing_balance" to "Standing Balance",
                    "gentle_bridge" to "Gentle Bridge",
                    "arm_raises" to "Arm Raises"
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Exercise:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                exercises.find { it.first == selectedExercise }?.second ?: "Select",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            exercises.forEach { (exerciseKey, exerciseName) ->
                                DropdownMenuItem(
                                    text = { Text(exerciseName) },
                                    onClick = {
                                        selectedExercise = exerciseKey
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Control buttons in a compact row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            Log.d("MainActivity", "=== START/STOP ANALYSIS BUTTON CLICKED ===")
                            Log.d("MainActivity", "Current exerciseSessionStarted: $exerciseSessionStarted")
                            Log.d("MainActivity", "Selected exercise: $selectedExercise")
                            
                            if (!exerciseSessionStarted) {
                                Log.d("MainActivity", "Starting exercise session...")
                                activity.startExerciseSession(selectedExercise)
                                exerciseSessionStarted = true
                                Log.d("MainActivity", "Exercise session started successfully")
                            } else {
                                Log.d("MainActivity", "Stopping exercise session...")
                                exerciseSessionStarted = false
                                Log.d("MainActivity", "Exercise session stopped")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!exerciseSessionStarted) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text(
                            if (!exerciseSessionStarted) "Start Analysis" else "Stop",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Button(
                        onClick = {
                            activity.testPoseAnalysis()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) {
                        Text(
                            "Test",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Button(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Text(
                            if (lensFacing == CameraSelector.LENS_FACING_BACK) "Front Cam" else "Back Cam",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { it.setAnalyzer(analysisExecutor, imageAnalyzer) }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraScreen", "Use case binding failed", exc)
                            analysisResultText = "Error: Could not bind camera. ${exc.localizedMessage}"
                        }
                        previewView
                    },
                    modifier = Modifier.weight(1f)
                )
                // Analysis status and exercise instructions
                if (exerciseSessionStarted) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAnalyzing) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isAnalyzing) "Analyzing your $selectedExercise form..." else "Ready for $selectedExercise analysis",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAnalyzing) MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (!isAnalyzing) {
                                Text(
                                    text = when (selectedExercise) {
                                        "chair_squat" -> "Sit down slowly, then stand up. Keep your back straight and use a chair for support."
                                        "wall_pushup" -> "Stand facing a wall, place hands on wall, and do gentle push-ups. Great for back safety!"
                                        "gentle_plank" -> "Hold a gentle plank position. Keep your back straight and breathe steadily."
                                        "standing_balance" -> "Stand on one leg, then the other. Hold onto a chair for support if needed."
                                        "gentle_bridge" -> "Lie on your back, bend knees, lift hips gently. Strengthens back safely."
                                        "arm_raises" -> "Raise your arms slowly to shoulder level. Great for posture and shoulder strength."
                                        else -> "Follow the on-screen guidance for safe exercise form."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = analysisResultText,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission is required.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var tempHour by remember { mutableStateOf(initialHour.toString().padStart(2, '0')) }
    var tempMinute by remember { mutableStateOf(initialMinute.toString().padStart(2, '0')) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Reminder Time", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column {
                        Text("Hour (0-23):", style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            value = tempHour,
                            onValueChange = {
                                val v = it.toIntOrNull()
                                if (v != null && v in 0..23) tempHour = it
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Column {
                        Text("Minute (0-59):", style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            value = tempMinute,
                            onValueChange = {
                                val v = it.toIntOrNull()
                                if (v != null && v in 0..59) tempMinute = it
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                    
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel", style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        onClick = {
                            val hour = tempHour.toIntOrNull() ?: initialHour
                            val minute = tempMinute.toIntOrNull() ?: initialMinute
                            onTimeSelected(hour, minute)
                        }
                    ) {
                        Text("OK", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}