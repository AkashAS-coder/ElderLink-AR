package com.example.elderlycareapp.ui.screens

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.elderlycareapp.MainActivity
import com.example.elderlycareapp.model.ChatMessage
import com.example.elderlycareapp.navigation.AppDestinations
import kotlinx.coroutines.launch

private val exercises = listOf(
    "Chair Squats" to "chair_squat",
    "Wall Push-ups" to "wall_pushup",
    "Gentle Plank" to "gentle_plank",
    "Standing Balance" to "standing_balance",
    "Arm Raises" to "arm_raises",
    "Leg Lifts" to "leg_lifts",
    "Seated Marches" to "seated_marches",
    "Heel Slides" to "heel_slides"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseChatbotScreen(
    navController: NavController,
    activity: MainActivity,
    onBack: () -> Unit,
    onEndExercise: () -> Unit,
    onSendMessage: (String) -> Unit,
    chatMessages: List<ChatMessage>,
    isProcessing: Boolean,
    roomId: String
) {
    var messageText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Speech Recognizer (user voice input)
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    // Handle speech recognition results
    val speechRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!results.isNullOrEmpty()) {
            messageText = results[0]
            if (messageText.isNotBlank()) {
                val selectedExercise = exercises.find { 
                    it.first.contains(messageText, ignoreCase = true)
                }
                selectedExercise?.let { (_, type) ->
                    // Navigate to camera screen with selected exercise
                    navController.navigate("${AppDestinations.CAMERA}/$type")
                } ?: run {
                    onSendMessage(messageText)
                }
                messageText = ""
            }
        }
        isListening = false
    }

    // Request record audio permission and start listening
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }
            try {
                speechRecognitionLauncher.launch(intent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
                e.printStackTrace()
            }
        } else {
            isListening = false
        }
    }
    
    // Scroll to the bottom when messages change (to show latest messages)
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            coroutineScope.launch {
                scrollState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }
    
    // No cleanup required for speech (voice removed)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onEndExercise) {
                        Text("End Exercise")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                state = scrollState
            ) {
                items(chatMessages) { message ->
                    ChatMessageItem(message = message)
                }
            }
            
            // Input field with voice support
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice removed — show only text input
                // Text input field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text("Type your message...") },
                    enabled = !isProcessing,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                // Send / Mic button
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row {
                        // Mic button (starts speech-to-text)
                        IconButton(
                            onClick = {
                                // If already listening, ignore
                                if (isListening) return@IconButton
                                when {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                                        // Launch speech intent
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                                        }
                                        try {
                                            speechRecognitionLauncher.launch(intent)
                                            isListening = true
                                        } catch (e: Exception) {
                                            isListening = false
                                            e.printStackTrace()
                                        }
                                    }
                                    else -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.Mic,
                                contentDescription = if (isListening) "Listening" else "Speak",
                                tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = messageText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.isUser
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isUser) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
