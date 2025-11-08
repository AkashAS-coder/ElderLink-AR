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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.elderlycareapp.MainActivity
import com.google.mlkit.vision.pose.PoseLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
// Data class to hold exercise instructions and guidance
data class ExerciseGuidance(
    val name: String,
    val instructions: String,
    val keyPoints: List<Pair<String, String>>,
    val tips: List<String>
)

// Exercise guidance database
private val exerciseGuidanceMap = mapOf(
    "chair_squat" to ExerciseGuidance(
        name = "Chair Squats",
        instructions = "Stand in front of a chair with feet shoulder-width apart. Lower your body as if sitting down, then stand back up. The camera will track your knees, back, and hip position to help you maintain proper form.",
        keyPoints = listOf(
            "Knees" to "Keep knees behind your toes - don't let them go forward",
            "Back" to "Keep your back straight - don't round your spine",
            "Hips" to "Push your hips back as you lower down"
        ),
        tips = listOf(
            "Keep your weight in your heels",
            "Engage your core muscles",
            "Go as low as comfortable - don't force it"
        )
    ),
    "wall_pushup" to ExerciseGuidance(
        name = "Wall Push-ups",
        instructions = "Stand facing a wall, place hands on the wall at shoulder height. Bend your elbows to bring your chest to the wall, then push back. The camera will monitor your elbow position and body alignment.",
        keyPoints = listOf(
            "Elbows" to "Keep elbows at 45 degrees - not too wide or narrow",
            "Body" to "Keep your body in a straight line",
            "Feet" to "Keep feet shoulder-width apart for stability"
        ),
        tips = listOf(
            // All feedback is now provided dynamically based on real-time pose analysis
            // See PoseAnalyzer.analyzeWallPushup() for measurement-based feedback
        )
    ),
    "gentle_plank" to ExerciseGuidance(
        name = "Gentle Plank",
        instructions = "Start on your hands and knees, then extend one leg back at a time. Keep your body in a straight line from head to heels. The camera will check your shoulder, hip, and ankle alignment.",
        keyPoints = listOf(
            "Shoulders" to "Keep shoulders directly over your hands",
            "Hips" to "Keep hips level - don't let them sag or pike up",
            "Ankles" to "Keep ankles in line with your body"
        ),
        tips = listOf(
            "Engage your core to maintain the line",
            "Breathe normally - don't hold your breath",
            "Start with shorter holds and build up"
        )
    ),
    "standing_balance" to ExerciseGuidance(
        name = "Standing Balance",
        instructions = "Stand on one leg with the other slightly lifted. Keep your standing leg slightly bent and your core engaged. The camera will monitor your balance and posture.",
        keyPoints = listOf(
            "Standing Leg" to "Keep a slight bend in your standing knee",
            "Core" to "Engage your core for stability",
            "Arms" to "Use your arms for balance if needed"
        ),
        tips = listOf(
            "Focus on a fixed point ahead",
            "Start with short holds and increase gradually",
            "Switch legs and repeat"
        )
    ),
    "gentle_bridge" to ExerciseGuidance(
        name = "Gentle Bridge",
        instructions = "Lie on your back with knees bent and feet flat. Lift your hips up to create a bridge. The camera will check your hip alignment and core engagement.",
        keyPoints = listOf(
            "Hips" to "Lift hips up to create a straight line",
            "Knees" to "Keep knees over your ankles",
            "Core" to "Engage your core to support your back"
        ),
        tips = listOf(
            "Squeeze your glutes as you lift",
            "Don't overextend your back",
            "Lower down slowly and controlled"
        )
    ),
    "arm_raises" to ExerciseGuidance(
        name = "Arm Raises",
        instructions = "Stand with feet shoulder-width apart. Raise your arms out to the sides and up overhead, then lower them back down. The camera will monitor your shoulder and arm alignment.",
        keyPoints = listOf(
            "Shoulders" to "Keep shoulders down and relaxed",
            "Arms" to "Raise arms to shoulder height or higher",
            "Posture" to "Keep your back straight throughout"
        ),
        tips = listOf(
            "Move slowly and controlled",
            "Don't let your shoulders creep up to your ears",
            "Breathe naturally throughout the movement"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    onBack: () -> Unit,
    onCapture: (ImageProxy) -> Unit,
    onStop: () -> Unit,
    isExerciseActive: Boolean = true,
    exerciseType: String = "chair_squat",
    onLandmarksUpdated: (Map<Int, Pair<Float, Float>>) -> Unit = {},
    // Accept either Map<Int, Pair<Float,Float>> (no confidence) or Map<Int, Triple<Float,Float,Float>> (with confidence)
    externalPoseLandmarks: Map<Int, Any> = emptyMap()
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Exercise state
    val guidance = exerciseGuidanceMap[exerciseType] ?: exerciseGuidanceMap["chair_squat"]!!
    var showInstructions by remember { mutableStateOf(true) }
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableStateOf(5) }
    var isExerciseInProgress by remember { mutableStateOf(false) }
    var lastFeedbackTime by remember { mutableStateOf(0L) }
    var currentFeedback by remember { mutableStateOf<String?>(null) }
    // poseLandmarks holds raw landmarks from parent detector: x,y,confidence (normalized 0..1)
    var poseLandmarks by remember { mutableStateOf<Map<Int, Triple<Float, Float, Float>>>(emptyMap()) }

    // If parent passes updated detected landmarks, accept and display them.
    // Accept both Pair(x,y) and Triple(x,y,confidence). Convert to internal Triple form.
    LaunchedEffect(externalPoseLandmarks) {
        if (externalPoseLandmarks.isNotEmpty()) {
            val converted = mutableMapOf<Int, Triple<Float, Float, Float>>()
            for ((k, v) in externalPoseLandmarks) {
                when (v) {
                    is Triple<*, *, *> -> {
                        val x = (v.first as? Number)?.toFloat()
                        val y = (v.second as? Number)?.toFloat()
                        val c = (v.third as? Number)?.toFloat() ?: 1f
                        if (x != null && y != null) converted[k] = Triple(x, y, c)
                    }
                    is Pair<*, *> -> {
                        val x = (v.first as? Number)?.toFloat()
                        val y = (v.second as? Number)?.toFloat()
                        if (x != null && y != null) converted[k] = Triple(x, y, 1f)
                    }
                    else -> {
                        // Ignore other types
                    }
                }
            }
            poseLandmarks = converted
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    
    // Exercise introduction and countdown
    LaunchedEffect(Unit) {
        // Normal instruction/countdown flow for all exercises

        showInstructions = true
        activity?.speakAIResponse("These are the steps to do ${guidance.name}.")
        delay(4000)

        val sentences = guidance.instructions.split(Regex("(?<=[.!?])\\s+"))
        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.isNotEmpty()) {
                activity?.speakAIResponse(trimmed)
                delay(4000)
            }
        }

        activity?.speakAIResponse("The camera will track your body and show you a skeleton overlay. I will give you tips every 4 seconds about your form.")
        delay(4000)

        showInstructions = false
        showCountdown = true
        activity?.speakAIResponse("Get ready! Starting in 5 seconds...")
        delay(1000)
        for (value in 5 downTo 1) {
            countdownValue = value
            activity?.speakAIResponse(value.toString())
            delay(1000)
        }
        showCountdown = false
        
        android.util.Log.d("CameraScreen", "Countdown finished, about to call beginExerciseAnalysis")
        activity?.speakAIResponse("Begin!")
        isExerciseInProgress = true
        android.util.Log.d("CameraScreen", "Calling beginExerciseAnalysis now")
        // Notify activity to begin analysis/timers
        activity?.beginExerciseAnalysis()
        android.util.Log.d("CameraScreen", "beginExerciseAnalysis called")

        delay(4000)

        scope.launch {
            var idx = 0
            val tips = guidance.tips
            while (isExerciseInProgress) {
                if (tips.isNotEmpty()) {
                    currentFeedback = tips[idx % tips.size]
                    idx++
                }
                delay(4000)
            }
        }
    }
    
    // Handle feedback timing
    LaunchedEffect(currentFeedback) {
        currentFeedback?.let { feedback ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFeedbackTime >= 4000) {
                activity?.speakAIResponse(feedback)
                lastFeedbackTime = currentTime
                scope.launch {
                    delay(4000)
                    if (System.currentTimeMillis() - lastFeedbackTime >= 4000) {
                        currentFeedback = null
                    }
                }
            }
        }
    }
    
    // We'll maintain a smoothed landmark map (x,y,confidence) to reduce jitter and
    // make the skeleton follow the visible body size/shape reliably.
    var smoothedLandmarks by remember { mutableStateOf<Map<Int, Triple<Float, Float, Float>>>(emptyMap()) }

    // Smooth updates when raw poseLandmarks change (low-pass filter), preserve confidence
    LaunchedEffect(poseLandmarks) {
        if (poseLandmarks.isEmpty()) {
            smoothedLandmarks = emptyMap()
            onLandmarksUpdated(emptyMap())
        } else {
            val factor = 0.9f // Higher = more responsive, lower = smoother
            val newMap = mutableMapOf<Int, Triple<Float, Float, Float>>()
            for ((idx, triple) in poseLandmarks) {
                val tx = triple.first
                val ty = triple.second
                val tc = triple.third
                val prev = smoothedLandmarks[idx]
                val updated = if (prev != null) {
                    val ux = prev.first * (1 - factor) + tx * factor
                    val uy = prev.second * (1 - factor) + ty * factor
                    val uc = prev.third * (1 - factor) + tc * factor
                    Triple(ux, uy, uc)
                } else {
                    Triple(tx, ty, tc)
                }
                newMap[idx] = updated
            }
            smoothedLandmarks = newMap
            // Emit normalized smoothed coords (x,y) for external OpenCV use (drop confidence)
            onLandmarksUpdated(newMap.mapValues { Pair(it.value.first, it.value.second) })
        }
    }
    
    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Request camera permission if not granted
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, update state to trigger camera initialization
            hasCamPermission = true
        } else {
            // Permission denied
            Log.e("CameraScreen", "Camera permission denied")
        }
    }
    
    var permissionRequested by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            permissionRequested = true
        }
    }
    
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    
    // Set up camera preview and analysis - only after permission is explicitly handled
    LaunchedEffect(hasCamPermission) {
        if (!hasCamPermission) return@LaunchedEffect
        
        Log.d("CameraScreen", "Initializing camera with permission granted")
        
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()
            Log.d("CameraScreen", "Camera provider obtained successfully")
            
            // Build and bind the camera use cases
            val newImageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = newImageCapture
                
            val newImageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720)) // Higher resolution for better pose detection
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .apply {
                    setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                        // Forward frames to analyzer when exercise is active
                        Log.d("CameraScreen", "Image frame received, isExerciseInProgress=$isExerciseInProgress")
                        if (isExerciseInProgress) {
                            try {
                                Log.d("CameraScreen", "Processing image for pose analysis")
                                onCapture(image)
                                // Update pose landmarks will be handled by MainActivity
                            } catch (e: Exception) {
                                Log.e("CameraScreen", "Error in image analysis", e)
                            } finally {
                                image.close()
                            }
                        } else {
                            Log.d("CameraScreen", "Skipping frame - exercise not started yet")
                            image.close()
                        }
                    }
                }
                
            // Update state
            val newCamera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                },
                newImageCapture,
                newImageAnalyzer
            )
            
            camera = newCamera
            Log.d("CameraScreen", "Camera initialized successfully")
        } catch (e: Exception) {
            Log.e("CameraScreen", "Camera initialization failed", e)
        }
    }
    
    // Main UI
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(guidance.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        onStop()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onStop()
                        onBack()
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Exercise")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCamPermission) {
                // Camera preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Skeleton overlay removed per user request — no landmark drawing

                // Show instructions overlay if active
                if (showInstructions) {
                    ExerciseInstructionsOverlay(guidance = guidance, onDismiss = { showInstructions = false }, padding = padding)
                }

                // Show countdown overlay if active
                if (showCountdown) {
                    CountdownOverlay(countdownValue = countdownValue, padding = padding)
                }

                // Controls container (overlayed at bottom)
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flip camera button
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = {
                                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                                    scope.launch {
                                        cameraProvider?.unbindAll()
                                        delay(100)
                                        try {
                                            val newCamera = cameraProvider?.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) },
                                                imageCapture,
                                                ImageAnalysis.Builder().setTargetResolution(Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                                                    setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                                                        android.util.Log.d("CameraScreen", "Image analyzer running, isExerciseInProgress=$isExerciseInProgress")
                                                        if (isExerciseInProgress) {
                                                            android.util.Log.d("CameraScreen", "Processing image for pose detection")
                                                            try { onCapture(image) } catch (e: Exception) { Log.e("CameraScreen", "Error in image analysis", e) } finally { image.close() }
                                                        } else { 
                                                            android.util.Log.d("CameraScreen", "Skipping image - exercise not in progress")
                                                            image.close() 
                                                        }
                                                    }
                                                }
                                            )
                                            camera = newCamera
                                        } catch (e: Exception) {
                                            Log.e("CameraScreen", "Error switching camera", e)
                                        }
                                    }
                                },
                                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                            ) { Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
                            Text(text = "Flip", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Capture button
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                val currentImageCapture = imageCapture ?: return@Button
                                currentImageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) { onCapture(image) }
                                    override fun onError(exception: ImageCaptureException) { Log.e(TAG, "Image capture failed", exception) }
                                })
                            }, modifier = Modifier.size(72.dp), enabled = true) {
                                Icon(imageVector = Icons.Default.Camera, contentDescription = "Take Photo", modifier = Modifier.size(24.dp))
                            }
                            Text(text = "Capture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Stop exercise button
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = onStop, modifier = Modifier.size(72.dp), enabled = isExerciseActive, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop Exercise", modifier = Modifier.size(24.dp))
                            }
                            Text(text = "Stop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                // Permission required UI
                Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = "Camera permission required", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Request Permission") }
                }
            }
        }
    }

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
                cameraExecutor.shutdown()
                Log.d(TAG, "Camera resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up camera resources", e)
            }
        }
    }
}

@Composable
private fun ExerciseInstructionsOverlay(
    guidance: ExerciseGuidance,
    onDismiss: () -> Unit,
    padding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = guidance.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Instructions:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = guidance.instructions,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Key Points:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            guidance.keyPoints.forEach { (title, description) ->
                Text(
                    text = "• $title: $description",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Got it!")
            }
        }
    }
}

@Composable
private fun CountdownOverlay(
    countdownValue: Int,
    padding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = countdownValue.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 80.sp
        )
    }
}

private const val TAG = "CameraScreen"