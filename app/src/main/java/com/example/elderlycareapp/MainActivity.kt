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
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis // Added
import androidx.camera.core.ImageProxy // Added
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat // Added for permission checks
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.Executors // Added
import java.util.concurrent.TimeUnit // Added for potential throttling example
import kotlin.text.format

object AppDestinations {
    const val HOME = "home"
    const val EXERCISE_CHATBOT = "exercise_chatbot"
    const val COMPANION_CHATBOT = "companion_chatbot"
    const val CAMERA = "camera"
}

private val sharedOkHttpClient = OkHttpClient()

data class ChatMessage(
    val text: String,
    val isUser: Boolean // Use Kotlin's Boolean
)

// Simple Image Analyzer (Ideally in its own file: MyImageAnalyzer.kt)
class MyImageAnalyzer(private val onFrameAnalyzed: (String) -> Unit) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L

    @SuppressLint("UnsafeOptInUsageError") // For imageProxy.image
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        // Optional: Implement frame throttling to avoid over-processing
        // if (currentTime - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) { // Example: process once per second

        val image = imageProxy.image // Get the underlying Image
        if (image != null) {
            // --- Your Image Processing Logic Goes Here ---
            val analysisResult = "Timestamp: ${imageProxy.imageInfo.timestamp}, " +
                    "Rotation: ${imageProxy.imageInfo.rotationDegrees}, " +
                    "Format: ${image.format}, " + // Requires @UnsafeOptInUsageError for image.format
                    "Size: ${image.width}x${image.height}"
            Log.d("MyImageAnalyzer", analysisResult)
            onFrameAnalyzed(analysisResult) // Pass some result back to the UI if needed
            // lastAnalyzedTimestamp = currentTime
        }

        imageProxy.close() // IMPORTANT: Close the ImageProxy
    }
}


class MainActivity : ComponentActivity() {

    companion object {
        const val CHANNEL_ID = "reminder_channel"
        const val NOTIFICATION_ID = 101
        const val REMINDER_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = AppDestinations.HOME) {
                    composable(AppDestinations.HOME) { HomeScreen(navController, this@MainActivity) }
                    composable(AppDestinations.EXERCISE_CHATBOT) { ExerciseChatbotScreen(navController) }
                    composable(AppDestinations.COMPANION_CHATBOT) { CompanionChatbotScreen(navController) }
                    composable(AppDestinations.CAMERA) { CameraScreen(navController) } // CameraScreen is now updated
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reminder Notifications" // TODO: Extract string resource
            val descriptionText = "Channel for elderly care reminders" // TODO: Extract string resource
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
            if (before(Calendar.getInstance())) { // If time is already passed for today, set for tomorrow
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
            Log.e("MainActivity", "Failed to schedule reminder due to SecurityException. Check SCHEDULE_EXACT_ALARM permission.", se)
            Toast.makeText(context, "Could not schedule reminder. Permission issue?", Toast.LENGTH_LONG).show()
        }
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("ReminderReceiver", "POST_NOTIFICATIONS permission not granted. Cannot show reminder.")
                    return
                }
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Elderly Care Reminder")
                .setContentText("Time for your daily check-in or exercise!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Log.w("ReminderReceiver", "Notification permission check failed just before notify. This is unexpected.")
                    return
                }
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }
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
                Toast.makeText(context, "Notification permission denied. Cannot set reminder.", Toast.LENGTH_LONG).show()
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
                title = { Text("Exercise Chatbot", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                items(messages.asReversed()) { msg ->
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
                        val replyText = when {
                            inputText.contains("neck", true) -> "Try slowly turning your head side to side."
                            inputText.contains("back", true) -> "Gently roll your shoulders and sit upright."
                            inputText.contains("leg", true) -> "Lift each leg slowly while seated."
                            else -> "Sorry, I can only suggest neck, back, or leg exercises for now."
                        }
                        messages.add(ChatMessage(replyText, false))
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
                Text("Open Fitness Camera", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun CompanionChatbotScreen(navController: NavHostController) {
    var userInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var isLoading by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Companion Chatbot", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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

            currentError?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
                            userInput = ""
                            isLoading = true
                            currentError = null
                            streamChatResponse(
                                prompt = currentInput,
                                apiKey = BuildConfig.GEMINI_API_KEY,
                                coroutineScope = coroutineScope,
                                onChunkReceived = { chunk ->
                                    if (messages.lastOrNull()?.isUser == false) {
                                        val lastBotMessage = messages.removeLast()
                                        messages.add(lastBotMessage.copy(text = lastBotMessage.text + chunk))
                                    } else {
                                        messages.add(ChatMessage(chunk, false))
                                    }
                                },
                                onError = { error ->
                                    currentError = error
                                    isLoading = false
                                },
                                onCompletion = {
                                    isLoading = false
                                }
                            )
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


fun streamChatResponse(
    prompt: String,
    apiKey: String?,
    coroutineScope: CoroutineScope,
    onChunkReceived: (String) -> Unit,
    onError: (String) -> Unit,
    onCompletion: () -> Unit
) {
    if (apiKey.isNullOrEmpty() || apiKey == "YOUR_ACTUAL_GEMINI_API_KEY" || apiKey == "") {
        Log.e("GeminiAPI", "API Key is missing or not set. Please ensure GEMINI_API_KEY is in your local.properties and build.gradle.")
        coroutineScope.launch(Dispatchers.Main) {
            onError("API Key is not configured correctly.")
            onCompletion()
        }
        return
    }

    val jsonBody = """
        {
          "contents": [ {"parts": [ {"text": "$prompt"} ]} ]
        }
    """.trimIndent()

    val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:streamGenerateContent?key=$apiKey")
        .post(requestBody)
        .build()

    sharedOkHttpClient.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("GeminiAPI", "API Call Failed: ${e.message}", e)
            coroutineScope.launch(Dispatchers.Main) {
                onError("Network Error: ${e.message ?: "Unknown error"}")
                onCompletion()
            }
        }

        override fun onResponse(call: Call, response: Response) {
            var streamingError: String? = null
            try {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("GeminiAPI", "API Error ${response.code}: $errorBody")
                    streamingError = "API Error (${response.code}): ${errorBody ?: "Unknown API error"}"
                    return
                }

                response.body?.source()?.use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (line != null && line.startsWith("data: ")) {
                            val jsonString = line.removePrefix("data: ").trim()
                            if (jsonString.isNotEmpty() && jsonString != "[DONE]") {
                                try {
                                    val json = JSONObject(jsonString)
                                    val candidates = json.optJSONArray("candidates")
                                    if (candidates != null && candidates.length() > 0) {
                                        val content = candidates.getJSONObject(0).optJSONObject("content")
                                        val parts = content?.optJSONArray("parts")
                                        if (parts != null && parts.length() > 0) {
                                            val text = parts.getJSONObject(0).optString("text")
                                            if (text.isNotEmpty()) {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    onChunkReceived(text)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: JSONException) {
                                    Log.e("GeminiAPI", "JSON Parsing Error in stream: ${e.message} for line: $jsonString")
                                }
                            }
                        }
                    }
                } ?: run {
                    streamingError = "Empty response body from API."
                }
            } catch (e: Exception) {
                Log.e("GeminiAPI", "Streaming Response Error: ${e.message}", e)
                streamingError = "Streaming Error: ${e.message ?: "Unknown error during streaming"}"
            } finally {
                response.close()
                coroutineScope.launch(Dispatchers.Main) {
                    streamingError?.let { onError(it) }
                    onCompletion()
                }
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

    var hasCamPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
            if (!granted) {
                Toast.makeText(context, "Camera permission denied. Camera cannot be used.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(key1 = true) { // Request permission if not already granted
        if (!hasCamPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // State to hold the latest analysis result for display
    var analysisResultText by remember { mutableStateOf("Analysis results will appear here.") }

    // Create an instance of your analyzer
    val imageAnalyzer = remember {
        MyImageAnalyzer { result ->
            // This callback runs on the main thread for UI updates
            analysisResultText = result
        }
    }

    // Dedicated executor for ImageAnalysis
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Ensure the executor is shut down when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            Log.d("CameraScreen", "Shutting down analysis executor.")
            analysisExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fitness Camera", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (hasCamPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProvider = cameraProviderFuture.get() // This is fine here as get() will block if needed, but consider listener for robustness

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()

                        // --- Setup ImageAnalysis Use Case ---
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(analysisExecutor, imageAnalyzer)
                            }
                        // --- End ImageAnalysis Setup ---

                        try {
                            cameraProvider.unbindAll() // Unbind previous use cases
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis // Add imageAnalysis here
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraScreen", "Use case binding failed", exc)
                            // Update UI to show error
                            analysisResultText = "Error: Could not bind camera use cases. ${exc.localizedMessage}"
                        }
                        previewView
                    },
                    modifier = Modifier.weight(1f) // Preview takes up most space
                )
                // Display analysis results
                Text(
                    text = analysisResultText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Reminder Time", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tempHour,
                        onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) tempHour = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Hour (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(" : ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedTextField(
                        value = tempMinute,
                        onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) tempMinute = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Minute (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val hour = tempHour.toIntOrNull()
                        val minute = tempMinute.toIntOrNull()
                        if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                            onTimeSelected(hour, minute)
                            onDismissRequest()
                        } else {
                            Toast.makeText(context, "Invalid time. Please enter HH (0-23) and MM (0-59).", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("OK", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
