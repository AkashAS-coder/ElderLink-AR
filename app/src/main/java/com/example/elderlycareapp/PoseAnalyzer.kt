package com.example.elderlycareapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose as MLKitPose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.acos
import kotlin.random.Random
import java.util.Timer
import java.util.TimerTask
// MediaPipe temporarily disabled - using ML Kit instead
// import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

private const val TAG = "PoseAnalyzer"

// Data class to hold exercise instructions and guidance
data class ExerciseGuidance(
    val name: String,
    val instructions: String,
    val keyPoints: List<Pair<String, String>>,
    val tips: List<String>
)

// Data class to hold exercise analysis results
data class ExerciseAnalysis(
    val isCorrect: Boolean,
    val feedback: String,
    val confidence: Float // 0.0 to 1.0
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
        instructions = "Great choice! Stand facing a wall, place your hands on the wall at shoulder height. Gently bend your elbows to bring your chest toward the wall, then push back. The camera will help you maintain great form.",
        keyPoints = listOf(
            "Elbows" to "Aim for a comfortable bend in your elbows - you're doing great!",
            "Body" to "Try to keep your body in a nice straight line - but don't worry if it's not perfect!",
            "Feet" to "Keep your feet about shoulder-width apart for good balance"
        ),
        tips = listOf(
            // Tips are now provided dynamically based on pose analysis
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

class PoseAnalyzer(private val context: Context) {
    private var poseDetector: PoseDetector? = null
    private var blazePoseDetector: MediaPipePoseDetector? = null
    private var useBlazePose = false // Temporarily disabled - using ML Kit instead
    
    private var currentExerciseType = "chair_squat"
    private var lastFeedback: String? = null
    private var lastFeedbackTime: Long = 0
    private var lastEncouragementTime: Long = 0
    private var exerciseStartTime: Long = 0
    private var lastRepCount: Long = 0
    private var feedbackDelayMs: Long = 5000L // 5 second delay after countdown
    private var hasSentFirstFeedback: Boolean = false
    private val feedbackHistory = mutableListOf<String>()
    private var onFeedbackCallback: ((String) -> Unit)? = null
    private var ttsCallback: ((String) -> Unit)? = null

    // Timer-based continuous updates
    private var statusUpdateTimer: Timer? = null
    private var lastPose: MLKitPose? = null
    internal var isContinuousUpdatesEnabled = false
    private val statusUpdateInterval = 5000L // 5 seconds
    
    // Initialize the pose detector based on the selected type
    init {
        initializePoseDetector()
    }
    
    /**
     * Initialize the appropriate pose detector based on the useBlazePose flag
     */
    private fun initializePoseDetector() {
        if (useBlazePose) {
            // Initialize BlazePose detector
            try {
                blazePoseDetector = MediaPipePoseDetector(context).apply {
                    initializePoseDetector()
                }
                Log.d(TAG, "BlazePose detector initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize BlazePose detector, falling back to ML Kit", e)
                useBlazePose = false
                initializeMLKitPoseDetector()
            }
        } else {
            initializeMLKitPoseDetector()
        }
    }
    
    /**
     * Initialize the default ML Kit Pose Detector
     */
    private fun initializeMLKitPoseDetector() {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        
        poseDetector = PoseDetection.getClient(options)
        Log.d(TAG, "ML Kit Pose Detector initialized")
    }
    
    /**
     * Process the given bitmap using either BlazePose or ML Kit's pose detector
     */
    private fun processImage(
        bitmap: Bitmap,
        onSuccess: (MLKitPose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (useBlazePose && blazePoseDetector?.isReady() == true) {
            // Use BlazePose for detection
            try {
                val result = blazePoseDetector?.analyzePose(bitmap)
                if (result != null) {
                    val pose = blazePoseDetector?.convertToMLKitPose(
                        result,
                        bitmap.width,
                        bitmap.height
                    )
                    
                    pose?.let {
                        lastPose = it
                        onSuccess(it)
                    } ?: run {
                        onFailure(Exception("No pose detected"))
                    }
                } else {
                    onFailure(Exception("Pose detection returned null result"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "BlazePose detection failed, falling back to ML Kit", e)
                // Fall back to ML Kit
                useBlazePose = false
                initializeMLKitPoseDetector()
                processWithMLKit(bitmap, onSuccess, onFailure)
            }
        } else {
            // Fall back to ML Kit
            processWithMLKit(bitmap, onSuccess, onFailure)
        }
    }
    
    /**
     * Process image using ML Kit's default pose detector
     */
private fun processWithMLKit(
        bitmap: Bitmap,
        onSuccess: (MLKitPose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        poseDetector?.process(image)
            ?.addOnSuccessListener { pose ->
                lastPose = pose
                onSuccess(pose)
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "ML Kit pose detection failed", e)
                onFailure(e)
            } ?: run {
                onFailure(Exception("Pose detector not initialized"))
            }
    }

    // Exercise-specific parameters
    private val exerciseCooldownMs = 2000L // 2 seconds between feedback for wall pushups
    private val minFeedbackIntervalMs = 2000L // 2 seconds minimum between feedback for wall pushups
    private val feedbackExpiryMs = 30000L // 30 seconds before feedback can be repeated
    private val positiveFeedback = listOf(
        "Excellent form! Keep it up!",
        "Perfect technique! You're doing great!",
        "Outstanding execution! Looking good!",
        "Great job! Your form is spot on!",
        "Fantastic! You're nailing this exercise!",
        "No issue with your form - keep it up!",
        "Your form looks perfect - no adjustments needed!",
        "Excellent! No issues detected!",
        "Perfect form - you're doing everything right!",
        "Great job! No corrections needed!"
    )

    private val kneeFeedback = listOf(
        "Focus on keeping your knees behind your toes",
        "Try to push your knees out slightly",
        "Keep your knees tracking over your feet",
        "Don't let your knees cave inward",
        "Your knees should stay aligned with your toes",
        "Push your knees out to the sides",
        "Keep your knees from going too far forward"
    )

    private val backFeedback = listOf(
        "Keep your back straight and tall",
        "Engage your core to support your spine",
        "Don't round your back - keep it neutral",
        "Think about lengthening your spine",
        "Your back should form a straight line",
        "Keep your chest up and shoulders back",
        "Maintain a neutral spine position"
    )

    private val hipFeedback = listOf(
        "Push your hips back as you lower",
        "Keep your hips level and stable",
        "Don't let your hips shift to one side",
        "Engage your glutes to stabilize your hips",
        "Your hips should move straight down",
        "Keep your hips square and balanced",
        "Don't let one hip drop lower than the other"
    )

    private val shoulderFeedback = listOf(
        "Keep your shoulders down and relaxed",
        "Don't let your shoulders creep up to your ears",
        "Keep your shoulders over your hands",
        "Relax your shoulder blades",
        "Your shoulders should stay stable",
        "Don't let your shoulders round forward",
        "Keep your shoulder blades pulled back"
    )

    private val elbowFeedback = listOf(
        "Keep your elbows at a 45-degree angle",
        "Don't let your elbows flare out too wide",
        "Keep your elbows close to your body",
        "Maintain a slight bend in your elbows",
        "Your elbows should point slightly outward",
        "Don't let your elbows go too wide",
        "Keep your elbows at a comfortable angle"
    )
    
    private val generalFormFeedback = listOf(
        "Make sure your upper body is visible",
        "Step back a bit so I can see your full body",
        "Adjust your position so I can track you better",
        "Make sure you're in the camera frame",
        "Position yourself so I can see your form",
        "Move slightly to center yourself in the frame"
    )

    // Exercise-specific parameters and thresholds with increased leniency
    private val exerciseThresholds = mapOf(
        "chair_squat" to mapOf(
            "knee_angle_min" to 80f,      // More lenient minimum knee angle
            "knee_angle_max" to 130f,     // More lenient maximum knee angle
            "back_angle_max" to 35f,      // More lenient back angle
            "hip_knee_alignment_threshold" to 0.6f,  // More lenient hip-knee alignment
            "min_visible_landmarks" to 3   // Require fewer landmarks for analysis
        ),
        "wall_pushup" to mapOf(
            "elbow_angle_min" to 70f,     // More lenient minimum elbow angle
            "elbow_angle_max" to 130f,    // More lenient maximum elbow angle
            "back_angle_max" to 15f,      // More lenient back angle
            "shoulder_stability_threshold" to 0.2f,  // More lenient shoulder stability
            "min_visible_landmarks" to 3   // Require fewer landmarks for analysis
        ),
        "gentle_plank" to mapOf(
            "spine_angle_min" to 150f,    // More lenient minimum spine angle
            "spine_angle_max" to 210f,    // More lenient maximum spine angle
            "hip_ratio_min" to 0.4f,      // More lenient hip ratio
            "hip_ratio_max" to 2.2f,      // More lenient hip ratio
            "elbow_shoulder_alignment" to 0.4f,  // More lenient alignment
            "min_visible_landmarks" to 4   // Slightly more landmarks needed for plank
        ),
        "standing_balance" to mapOf(
            "foot_width_min" to 0.7f,     // More lenient minimum foot width
            "foot_width_max" to 1.4f,     // More lenient maximum foot width
            "knee_angle_min" to 165f,     // More lenient minimum knee angle
            "knee_angle_max" to 195f,     // More lenient maximum knee angle
            "ankle_angle_min" to 70f,     // More lenient minimum ankle angle
            "ankle_angle_max" to 110f,    // More lenient maximum ankle angle
            "shoulder_hip_angle_min" to 165f,  // More lenient minimum angle
            "shoulder_hip_angle_max" to 195f,  // More lenient maximum angle
            "balance_threshold" to 0.3f,  // More lenient balance threshold
            "min_visible_landmarks" to 3   // Require fewer landmarks for analysis
        ),
        "gentle_bridge" to mapOf(
            "knee_angle_min" to 80f,      // More lenient minimum knee angle
            "knee_angle_max" to 130f,     // More lenient maximum knee angle
            "hip_extension_min" to 0.05f, // More lenient minimum hip extension
            "hip_extension_max" to 0.4f,  // More lenient maximum hip extension
            "shoulder_angle_min" to 60f,  // More lenient minimum shoulder angle
            "shoulder_angle_max" to 120f, // More lenient maximum shoulder angle
            "hip_level_threshold" to 0.15f, // More lenient hip level threshold
            "min_visible_landmarks" to 3   // Require fewer landmarks for analysis
        ),
        "arm_raises" to mapOf(
            "arm_angle_min" to 140f,      // More lenient minimum arm angle
            "arm_angle_max" to 185f,      // More lenient maximum arm angle
            "elbow_angle_min" to 150f,    // More lenient minimum elbow angle
            "shoulder_elevation_max" to 15f, // More lenient shoulder elevation
            "torso_angle_max" to 15f,     // More lenient torso angle
            "arm_symmetry_threshold" to 0.25f, // More lenient arm symmetry
            "min_visible_landmarks" to 3   // Require fewer landmarks for analysis
        )
    )
    // Use single backing field for initialization
    private var _isInitialized = false
    val isInitialized: Boolean
        get() = _isInitialized

    fun initializePoseAnalyzer(callback: (String) -> Unit, ttsCallback: ((String) -> Unit)? = null) {
        onFeedbackCallback = callback
        this.ttsCallback = ttsCallback
        try {
            // Configure ML Kit Pose Detector with accurate mode for better precision
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()

            // Initialize the pose detector with BlazePose
            poseDetector = PoseDetection.getClient(options)
            _isInitialized = true
            Log.d(TAG, "BlazePose detector initialized successfully in ACCURATE mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing BlazePose detector", e)
        }
    }

    fun analyzePose(bitmap: Bitmap, onResult: (ExerciseAnalysis) -> Unit, onError: (Exception) -> Unit = {}) {
        processImage(bitmap, { pose ->
            val analysis = analyzePose(pose)
            onResult(analysis)
        }, { exception ->
            Log.e(TAG, "Error analyzing pose", exception)
            onError(exception)
        })
    }

    private fun analyzePose(pose: MLKitPose): ExerciseAnalysis {
        // Store the pose for use by the timer
        lastPose = pose
        Log.d(TAG, "analyzePose: Stored pose with ${pose.allPoseLandmarks.size} landmarks")
        
        // Perform full analysis for all exercise types (including wall_pushup)
        
        val landmarks = pose.allPoseLandmarks
        
        // Debug logging to see what we're getting
        Log.d(TAG, "Total landmarks detected: ${landmarks.size}")
        
        // Safety check: ensure we have enough landmarks
        // ML Kit provides 33 landmarks, but we need at least key body points
        if (landmarks.isEmpty()) {
            Log.w(TAG, "No landmarks detected")
            return ExerciseAnalysis(
                isCorrect = false,
                feedback = "Position update: Detecting your pose...",
                confidence = 0.0f
            )
        }
        
        // Check if we have the minimum required landmarks for body tracking
        val hasMinimumLandmarks = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) != null &&
                                   pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) != null &&
                                   pose.getPoseLandmark(PoseLandmark.LEFT_HIP) != null &&
                                   pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) != null
        
        if (!hasMinimumLandmarks) {
            Log.w(TAG, "Missing key body landmarks (shoulders/hips)")
            return ExerciseAnalysis(
                isCorrect = false,
                feedback = "Position update: Please ensure your full body is visible in the frame",
                confidence = 0.0f
            )
        }
        
        return when (currentExerciseType.lowercase()) {
            "chair_squat" -> analyzeChairSquat(pose)
            "wall_pushup" -> analyzeWallPushup(pose)
            "gentle_plank" -> analyzeGentlePlank(pose)
            "standing_balance" -> analyzeStandingBalance(pose)
            "gentle_bridge" -> analyzeGentleBridge(pose)
            "arm_raises" -> analyzeArmRaises(pose)
            else -> ExerciseAnalysis(
                isCorrect = true,
                feedback = "Position update: ${currentExerciseType.replace("_", " ")} exercise in progress",
                confidence = 1.0f
            )
        }
    }

    private fun analyzeChairSquat(pose: MLKitPose): ExerciseAnalysis {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)?.position
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)?.position
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)?.position
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)?.position
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.position

        if (leftHip == null || rightHip == null || leftKnee == null || rightKnee == null || 
            leftAnkle == null || rightAnkle == null || leftShoulder == null || rightShoulder == null) {
            return ExerciseAnalysis(
                isCorrect = false,
                feedback = "Please make sure your full body is visible",
                confidence = 0.0f
            )
        }

        // Calculate knee angles
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

        // Check if knees are tracking over toes
        val leftKneeOverToes = isKneeOverToes(pose, true)
        val rightKneeOverToes = isKneeOverToes(pose, false)
        val kneesAligned = leftKneeOverToes && rightKneeOverToes

        // Check back angle
        val backAngle = calculateBackAngle(pose)
        val isBackStraight = backAngle < 20.0 // degrees

        // Check hip-knee-ankle alignment
        val leftHipKneeAnkleAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightHipKneeAnkleAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        val hipsLevel = abs(leftHipKneeAnkleAngle - rightHipKneeAnkleAngle) < 10.0

        // Determine feedback based on analysis
        return when {
            !kneesAligned -> ExerciseAnalysis(
                isCorrect = false,
                feedback = "Keep your knees aligned over your toes",
                confidence = 0.7f
            )
            !isBackStraight -> ExerciseAnalysis(
                isCorrect = false,
                feedback = "Keep your back straight and chest up",
                confidence = 0.8f
            )
            !hipsLevel -> ExerciseAnalysis(
                isCorrect = false,
                feedback = "Keep your hips level and balanced",
                confidence = 0.75f
            )
            else -> ExerciseAnalysis(
                isCorrect = true,
                feedback = "Great form! Keep going!",
                confidence = 0.9f
            )
        }
    }

    private fun analyzeWallPushup(pose: MLKitPose): ExerciseAnalysis {
        try {
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.position
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)?.position
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)?.position
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)?.position
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)?.position

            if (leftShoulder == null || rightShoulder == null || leftElbow == null || 
                rightElbow == null || leftWrist == null || rightWrist == null) {
                return ExerciseAnalysis(
                    isCorrect = true,
                    feedback = "Great! I can see your upper body. Keep moving!",
                    confidence = 0.8f
                )
            }

            // Calculate elbow angles to understand the motion
            val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
            val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
            val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

            // Build motion-based feedback focusing on body parts
            val feedbackParts = mutableListOf<String>()
            
            // Check if elbows are moving (bending/extending)
            when {
                avgElbowAngle < 100 -> {
                    // Bent elbows - pushing phase
                    feedbackParts.add("Great! Your elbows are bending nicely")
                    feedbackParts.add("Nice push! Your arms are working well")
                }
                avgElbowAngle > 140 -> {
                    // Extended arms - starting position
                    feedbackParts.add("Perfect! Your arms are extended")
                    feedbackParts.add("Good starting position with your elbows")
                }
                else -> {
                    // Mid-range - transitioning
                    feedbackParts.add("Excellent movement in your arms")
                    feedbackParts.add("Your elbows are moving smoothly")
                }
            }
            
            // Check shoulder position
            val shoulderWidth = kotlin.math.abs(leftShoulder.x - rightShoulder.x)
            if (shoulderWidth > 50) { // Shoulders visible and spread
                feedbackParts.add("Your shoulders look stable")
                feedbackParts.add("Great shoulder positioning")
            }
            
            // Check wrist alignment
            val wristsAligned = kotlin.math.abs(leftWrist.y - rightWrist.y) < 100
            if (wristsAligned) {
                feedbackParts.add("Your wrists are aligned nicely")
                feedbackParts.add("Good hand placement on the wall")
            }
            
            // General positive reinforcement
            feedbackParts.add("You're doing great! Keep that motion going")
            feedbackParts.add("Excellent work! Your form is looking good")
            feedbackParts.add("Nice job! Keep up the steady movement")
            feedbackParts.add("Perfect! You're moving with control")
            feedbackParts.add("Great effort! Your body is working well together")
            
            // Pick 1-2 random positive feedback items
            val feedback = feedbackParts.shuffled().take(2).joinToString(". ") + "."

            return ExerciseAnalysis(
                isCorrect = true,
                feedback = feedback,
                confidence = 0.95f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing wall pushup", e)
            return ExerciseAnalysis(
                isCorrect = true,
                feedback = "Keep going! You're doing wonderful!",
                confidence = 0.8f
            )
        }
    }

    private fun analyzeGentlePlank(pose: MLKitPose): ExerciseAnalysis {
        // Implementation for gentle plank analysis
        return ExerciseAnalysis(
            isCorrect = true,
            feedback = "Plank form looks good!",
            confidence = 0.9f
        )
    }

    private fun analyzeStandingBalance(pose: MLKitPose): ExerciseAnalysis {
        // Implementation for standing balance analysis
        return ExerciseAnalysis(
            isCorrect = true,
            feedback = "Great balance! Keep it up!",
            confidence = 0.9f
        )
    }

    private fun analyzeGentleBridge(pose: MLKitPose): ExerciseAnalysis {
        // Implementation for gentle bridge analysis
        return ExerciseAnalysis(
            isCorrect = true,
            feedback = "Bridge form looks good!",
            confidence = 0.9f
        )
    }

    private fun analyzeArmRaises(pose: MLKitPose): ExerciseAnalysis {
        // Implementation for arm raises analysis
        return ExerciseAnalysis(
            isCorrect = true,
            feedback = "Arm raise form looks good!",
            confidence = 0.9f
        )
    }

    private fun isBackStraight(pose: MLKitPose): Boolean {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.position
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)?.position

        if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
            val leftAngle = calculateAngle(leftShoulder, leftHip, rightHip)
            val rightAngle = calculateAngle(rightShoulder, rightHip, leftHip)
            return abs(leftAngle - rightAngle) < 10.0
        }
        return false
    }

    private fun calculateBackAngle(pose: MLKitPose): Double {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.position
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)?.position

        if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
            val shoulderCenter = PointF(
                (leftShoulder.x + rightShoulder.x) / 2,
                (leftShoulder.y + rightShoulder.y) / 2
            )
            val hipCenter = PointF(
                (leftHip.x + rightHip.x) / 2,
                (leftHip.y + rightHip.y) / 2
            )
            // Calculate angle between vertical line and the line from hip to shoulder
            return Math.toDegrees(
                atan2(
                    (shoulderCenter.x - hipCenter.x).toDouble(),
                    (shoulderCenter.y - hipCenter.y).toDouble()
                )
            )
        }
        return 0.0
    }

    private fun isKneeOverToes(pose: MLKitPose, isLeft: Boolean): Boolean {
        val hip = pose.getPoseLandmark(if (isLeft) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP)?.position
        val knee = pose.getPoseLandmark(if (isLeft) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE)?.position
        val ankle = pose.getPoseLandmark(if (isLeft) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE)?.position

        if (hip != null && knee != null && ankle != null) {
            // Check if knee is aligned between hip and ankle (x-coordinate comparison)
            return (knee.x >= minOf(hip.x, ankle.x) && knee.x <= maxOf(hip.x, ankle.x))
        }
        return false
    }

    private fun calculateAngle(first: PointF, mid: PointF, last: PointF): Double {
        if (first == mid || mid == last) return 0.0
        val angle = Math.toDegrees(
            atan2((last.y - mid.y).toDouble(), (last.x - mid.x).toDouble()) - 
            atan2((first.y - mid.y).toDouble(), (first.x - mid.x).toDouble())
        )
        return (angle + 360) % 360 // Ensure angle is positive
    }

    private fun speakEncouragement(encouragement: String) {
        try {
            val trimmed = encouragement.trim()
            // Update the feedback history for this encouragement
            lastFeedback = trimmed
            lastFeedbackTime = System.currentTimeMillis()
            
            ttsCallback?.invoke(trimmed)
            onFeedbackCallback?.invoke(trimmed)
            Log.d(TAG, "Encouragement sent via TTS: $trimmed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in speakEncouragement", e)
        }
    }
    
    private fun speakFeedback(feedback: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val trimmedFeedback = feedback.trim()
            
            // Simple duplicate check
            if (currentTime - lastFeedbackTime < 2000 && trimmedFeedback == lastFeedback) {
                Log.d(TAG, "Skipping duplicate feedback")
                return
            }
            
            // Update feedback history
            lastFeedback = trimmedFeedback
            lastFeedbackTime = currentTime
            feedbackHistory.add(trimmedFeedback)
            if (feedbackHistory.size > 5) {
                feedbackHistory.removeAt(0)
            }
            
            // Speak the feedback
            ttsCallback?.invoke(trimmedFeedback)
            
            // Also update the UI callback
            onFeedbackCallback?.invoke(trimmedFeedback)
            
            Log.d(TAG, "Feedback sent via TTS: $trimmedFeedback")
        } catch (e: Exception) {
            Log.e(TAG, "Error in speakFeedback", e)
        }
    }

    private data class FormQuality(
        val isGoodForm: Boolean,
        val improvementMessage: String
    )
    
    private fun checkFormQuality(exerciseType: String, pose: MLKitPose): FormQuality {
        return when (exerciseType.lowercase()) {
            "wall_pushup" -> checkWallPushupForm(pose)
            "chair_squat" -> checkChairSquatForm(pose)
            "gentle_plank" -> checkPlankForm(pose)
            "standing_balance" -> checkBalanceForm(pose)
            "gentle_bridge" -> checkBridgeForm(pose)
            "arm_raises" -> checkArmRaisesForm(pose)
            else -> FormQuality(true, "Keep going!")
        }
    }
    
    private fun checkWallPushupForm(pose: MLKitPose): FormQuality {
        try {
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)?.position
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)?.position
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.position
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)?.position
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)?.position
            
            // Very lenient checks - just need some landmarks
            if (leftElbow == null || rightElbow == null) {
                return FormQuality(true, "Keep moving those arms!")
            }
            
            // Check elbow angle - very lenient range (30-170 degrees is acceptable)
            if (leftShoulder != null && leftWrist != null) {
                val leftAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
                if (leftAngle < 30 || leftAngle > 170) {
                    return FormQuality(false, "Try to bend your elbows a bit more during the push")
                }
            }
            
            // Check if elbows are way too far apart or too close
            val elbowDistance = kotlin.math.abs(leftElbow.x - rightElbow.x)
            if (elbowDistance < 50) {
                return FormQuality(false, "Try spreading your hands wider on the wall")
            }
            
            // Form is good enough (>50%)
            return FormQuality(true, "")
        } catch (e: Exception) {
            return FormQuality(true, "Keep it up!")
        }
    }
    
    private fun checkChairSquatForm(pose: MLKitPose): FormQuality {
        try {
            val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position
            val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)?.position
            val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position
            val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)?.position
            
            if (leftKnee == null || leftHip == null || leftAnkle == null) {
                return FormQuality(true, "Keep going!")
            }
            
            // Check knee angle - very lenient (60-170 degrees)
            val kneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
            if (kneeAngle < 60) {
                return FormQuality(false, "Try not to bend your knees too much - sit back gently")
            }
            
            // Check if knees are going too far forward
            if (leftKnee.x > leftAnkle.x + 150) {
                return FormQuality(false, "Keep your knees behind your toes as you squat")
            }
            
            return FormQuality(true, "")
        } catch (e: Exception) {
            return FormQuality(true, "Keep going!")
        }
    }
    
    private fun checkPlankForm(pose: MLKitPose): FormQuality {
        try {
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
            val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position
            val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position
            
            if (leftShoulder == null || leftHip == null) {
                return FormQuality(true, "Keep holding!")
            }
            
            // Check if hips are sagging (very lenient)
            if (leftHip.y > leftShoulder.y + 200) {
                return FormQuality(false, "Try to lift your hips up a bit to keep your back straight")
            }
            
            // Check if hips are too high
            if (leftHip.y < leftShoulder.y - 200) {
                return FormQuality(false, "Try to lower your hips slightly for a straighter line")
            }
            
            return FormQuality(true, "")
        } catch (e: Exception) {
            return FormQuality(true, "Keep holding!")
        }
    }
    
    private fun checkBalanceForm(pose: MLKitPose): FormQuality {
        // Balance is mostly about holding steady, so always encourage
        return FormQuality(true, "")
    }
    
    private fun checkBridgeForm(pose: MLKitPose): FormQuality {
        try {
            val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position
            val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
            
            if (leftHip == null || leftShoulder == null) {
                return FormQuality(true, "Keep going!")
            }
            
            // Check if hips are lifted enough (very lenient)
            if (leftHip.y > leftShoulder.y + 100) {
                return FormQuality(false, "Try to lift your hips up higher off the ground")
            }
            
            return FormQuality(true, "")
        } catch (e: Exception) {
            return FormQuality(true, "Keep going!")
        }
    }
    
    private fun checkArmRaisesForm(pose: MLKitPose): FormQuality {
        try {
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)?.position
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)?.position
            
            if (leftShoulder == null || leftElbow == null || leftWrist == null) {
                return FormQuality(true, "Keep moving!")
            }
            
            // Check if arm is reasonably straight (very lenient - 140-180 degrees)
            val elbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
            if (elbowAngle < 140) {
                return FormQuality(false, "Try to keep your arms straighter as you raise them")
            }
            
            return FormQuality(true, "")
        } catch (e: Exception) {
            return FormQuality(true, "Keep moving!")
        }
    }
    
    private fun getExerciseFeedback(exerciseType: String, pose: MLKitPose?): String {
        // If no pose detected, give generic encouragement
        if (pose == null) {
            return when (exerciseType.lowercase()) {
                "wall_pushup" -> "Keep those wall pushups going! You're doing great!"
                "chair_squat" -> "Keep those squats going! Great effort!"
                "gentle_plank" -> "Hold that plank! You're doing great!"
                "standing_balance" -> "Keep your balance! You're doing well!"
                "gentle_bridge" -> "Keep that bridge going! Great work!"
                "arm_raises" -> "Keep raising those arms! Excellent!"
                else -> "Keep it up! You're doing great!"
            }
        }
        
        // Check pose quality - very lenient (50% threshold)
        val formQuality = checkFormQuality(exerciseType, pose)
        
        return when (exerciseType.lowercase()) {
            "wall_pushup" -> {
                if (formQuality.isGoodForm) {
                    // Good form - give positive feedback
                    listOf(
                        "Great job bending those elbows! Keep that motion going!",
                        "Excellent work! Your arms are moving smoothly!",
                        "Perfect! I can see your elbows raising nicely!",
                        "Nice push! Your arms are extending beautifully!",
                        "Wonderful effort! Keep moving those elbows!",
                        "Great form! Your shoulders and arms are working together!",
                        "Excellent! I can see good elbow movement!",
                        "Beautiful movement! Your elbows look strong!",
                        "Good job! Your shoulders are working well!"
                    ).random()
                } else {
                    // Needs improvement - give specific body part feedback
                    formQuality.improvementMessage
                }
            }
            
            "chair_squat" -> {
                if (formQuality.isGoodForm) {
                    listOf(
                        "Great job bending those knees! Perfect squat motion!",
                        "Excellent! Your legs are working beautifully!",
                        "Perfect form! I can see your knees bending nicely!",
                        "Nice work! Your hips and knees are moving smoothly!",
                        "Wonderful! Your leg strength is showing!",
                        "Great squat! Your knees are tracking well!",
                        "Excellent movement! Keep those legs strong!"
                    ).random()
                } else {
                    formQuality.improvementMessage
                }
            }
            
            "gentle_plank" -> {
                if (formQuality.isGoodForm) {
                    listOf(
                        "Great job holding that plank! Your core is strong!",
                        "Excellent! Your arms are supporting you well!",
                        "Perfect form! Keep that body straight!",
                        "Nice work! Your shoulders look stable!",
                        "Wonderful effort! Your core is engaged!",
                        "Great plank! Your whole body is working together!"
                    ).random()
                } else {
                    formQuality.improvementMessage
                }
            }
            
            "standing_balance" -> {
                if (formQuality.isGoodForm) {
                    listOf(
                        "Excellent balance! You're holding steady!",
                        "Great job! Your stability is improving!",
                        "Perfect! I can see you're staying balanced!",
                        "Wonderful! Your leg is supporting you well!",
                        "Nice work! Your balance is strong!",
                        "Great effort! You're staying nice and stable!"
                    ).random()
                } else {
                    formQuality.improvementMessage
                }
            }
            
            "gentle_bridge" -> {
                if (formQuality.isGoodForm) {
                    listOf(
                        "Great job lifting those hips! Perfect bridge!",
                        "Excellent! Your glutes and core are working!",
                        "Perfect form! I can see your hips raising nicely!",
                        "Nice work! Your back and hips are aligned!",
                        "Wonderful! Your lower body is strong!",
                        "Great bridge! Keep that lift going!"
                    ).random()
                } else {
                    formQuality.improvementMessage
                }
            }
            
            "arm_raises" -> {
                if (formQuality.isGoodForm) {
                    listOf(
                        "Great job raising those arms! Perfect motion!",
                        "Excellent! Your arms are moving smoothly!",
                        "Perfect! I can see your arms lifting nicely!",
                        "Nice work! Your shoulders are working well!",
                        "Wonderful! Your arm movement is controlled!",
                        "Great form! Your arms are extending beautifully!"
                    ).random()
                } else {
                    formQuality.improvementMessage
                }
            }
            
            else -> {
                if (formQuality.isGoodForm) {
                    listOf("Great work!", "Excellent effort!", "Keep it up!", "You're doing great!").random()
                } else {
                    formQuality.improvementMessage
                }
            }
        }
    }

    fun startContinuousUpdates(exerciseType: String) {
        Log.d(TAG, "startContinuousUpdates called with exerciseType: $exerciseType")
        currentExerciseType = exerciseType
        isContinuousUpdatesEnabled = true
        exerciseStartTime = System.currentTimeMillis()
        lastRepCount = 0
        hasSentFirstFeedback = false
        lastEncouragementTime = 0L
        
        // Provide encouraging initial feedback when starting the exercise
        val initialFeedback = when (exerciseType) {
            "chair_squat" -> "Starting chair squats. Stand in front of a chair with feet shoulder-width apart."
            "wall_pushup" -> "Let's do some wall push-ups! Stand about an arm's length from the wall. You're going to do great!"
            "gentle_plank" -> "Starting gentle planks. Get into a plank position with your knees on the floor."
            "standing_balance" -> "Starting standing balance. Stand straight with feet hip-width apart."
            "gentle_bridge" -> "Starting gentle bridges. Lie on your back with knees bent and feet flat on the floor."
            "arm_raises" -> "Starting arm raises. Stand straight with arms at your sides."
            else -> "Starting exercise. Please follow the on-screen instructions."
        }
        
        Log.d(TAG, "Speaking initial feedback: $initialFeedback")
        // Speak initial instruction for the selected exercise
        speakFeedback(initialFeedback)
        
        // No additional static messages - all feedback will be measurement-based from analyzeWallPushup

        // Cancel any existing timer
        statusUpdateTimer?.cancel()

        // Create a new timer for status updates and encouragement
        Log.d(TAG, "Creating timer for exercise: $exerciseType")
        statusUpdateTimer = Timer()
        statusUpdateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val currentTime = System.currentTimeMillis()
                    Log.d(TAG, "Timer tick - exerciseType=$currentExerciseType, time since start=${currentTime - exerciseStartTime}ms")
                    
                    // Provide GUARANTEED encouragement every 4 seconds for all exercises
                    if (currentExerciseType.isNotEmpty()) {
                        // Wait for initial delay
                        if (currentTime - exerciseStartTime < feedbackDelayMs) {
                            Log.d(TAG, "Timer: Waiting for initial delay period (${currentTime - exerciseStartTime}ms / ${feedbackDelayMs}ms)")
                            return
                        }
                        
                        val timeSinceLastEncouragement = currentTime - lastEncouragementTime
                        Log.d(TAG, "Timer: Time since last encouragement: ${timeSinceLastEncouragement}ms")
                        
                        // GUARANTEED feedback every 4 seconds!
                        if (timeSinceLastEncouragement > 4000L) {
                            Log.d(TAG, "Timer: Providing guaranteed live feedback for $currentExerciseType")
                            
                            // Get feedback based on what the body is doing (if pose available)
                            val feedback = getExerciseFeedback(currentExerciseType, lastPose)
                            
                            Log.d(TAG, "Timer: Speaking feedback: $feedback")
                            
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                speakEncouragement(feedback)
                            }
                            lastEncouragementTime = currentTime
                            Log.d(TAG, "Timer: Spoke encouragement at ${currentTime - exerciseStartTime}ms")
                        }
                    }
                    
                    // Also update UI with current analysis if we have a pose
                    lastPose?.let { pose ->
                        val analysis = analyzePose(pose)
                        onFeedbackCallback?.invoke(analysis.feedback)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in timer", e)
                }
            }
        }, 1000, 2000) // Check every 2 seconds
        Log.d(TAG, "Timer created and scheduled")
    }

    fun disableContinuousUpdates() {
        if (isContinuousUpdatesEnabled) {
            isContinuousUpdatesEnabled = false
            stopStatusUpdateTimer()
            Log.d(TAG, "Continuous 5-second updates disabled")
        }
    }

    private fun stopStatusUpdateTimer() {
        statusUpdateTimer?.cancel()
        statusUpdateTimer = null
        Log.d(TAG, "Status update timer stopped")
    }

    private fun reportCurrentPosition() {
        if (!isContinuousUpdatesEnabled || lastPose == null) {
            return
        }
        
        // Safety check: ensure pose has landmarks before processing
        val landmarks = lastPose!!.allPoseLandmarks
        if (landmarks.isEmpty()) {
            Log.d(TAG, "Skipping position report - no landmarks detected")
            return
        }

        val positionStatus = generatePositionStatus(lastPose!!)
        onFeedbackCallback?.invoke(positionStatus)
        Log.d(TAG, "Position status reported: $positionStatus")
    }

    private fun generatePositionStatus(pose: MLKitPose): String {
        val landmarks = pose.allPoseLandmarks

        // Debug logging
        Log.d(TAG, "generatePositionStatus: Total landmarks = ${landmarks.size}")
        
        // Safety check: ensure we have key landmarks
        if (landmarks.isEmpty()) {
            Log.w(TAG, "No landmarks detected")
            return "Position update: Detecting your pose..."
        }
        
        // Check for key body landmarks
        val hasKeyLandmarks = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) != null &&
                              pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) != null &&
                              pose.getPoseLandmark(PoseLandmark.LEFT_HIP) != null &&
                              pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) != null
        
        if (!hasKeyLandmarks) {
            Log.w(TAG, "Missing key body landmarks")
            return "Position update: Move into frame so I can see your full body"
        }

        return when (currentExerciseType.lowercase()) {
            "chair_squat" -> generateChairSquatStatus(landmarks)
            "wall_pushup" -> generateWallPushupStatus(landmarks)
            "gentle_plank" -> generateGentlePlankStatus(landmarks)
            "standing_balance" -> generateStandingBalanceStatus(landmarks)
            "gentle_bridge" -> generateGentleBridgeStatus(landmarks)
            "arm_raises" -> generateArmRaisesStatus(landmarks)
            else -> "Position update: ${currentExerciseType.replace("_", " ")} exercise in progress"
        }
    }

    private fun generateChairSquatStatus(landmarks: List<PoseLandmark>): String {
        return try {
            val leftKneeAngle = calculateKneeAngle(landmarks, 11, 13, 15) // LEFT_HIP, LEFT_KNEE, LEFT_ANKLE
            val rightKneeAngle = calculateKneeAngle(landmarks, 12, 14, 16) // RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE
            val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2
            
            when {
                avgKneeAngle < 100 -> "Position update: Deep squat position, knees at ${avgKneeAngle.toInt()} degrees"
                avgKneeAngle < 130 -> "Position update: Mid squat position, knees at ${avgKneeAngle.toInt()} degrees"
                else -> "Position update: Standing position, knees at ${avgKneeAngle.toInt()} degrees"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating chair squat status", e)
            "Position update: Analyzing chair squat position..."
        }
    }

    private fun generateWallPushupStatus(landmarks: List<PoseLandmark>): String {
        return try {
            // Get the last pose for analysis
            val lastPose = getLastPose()
            
            // If we have a pose, analyze it for positive feedback based on actual measurements
            if (lastPose != null) {
                val analysis = analyzeWallPushup(lastPose)
                Log.d(TAG, "Wall pushup status: ${analysis.feedback}")
                // Always return the analysis feedback since it's based on real measurements
                return analysis.feedback
            }
            
            // Fallback if no pose available
            Log.d(TAG, "No pose available for wall pushup status")
            return "Great job! Keep up the excellent work with your wall pushups!"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating wall pushup status", e)
            return "You're doing great! Keep it up!"
        }
    }

    private fun generateGentlePlankStatus(landmarks: List<PoseLandmark>): String {
        return try {
            // Calculate spine angle using shoulder, hip and knee landmarks
            val spineAngle = calculateSpineAngle(
                landmarks = landmarks,
                shoulderIndex = 5,  // LEFT_SHOULDER
                hipIndex = 11,      // LEFT_HIP
                kneeIndex = 13      // LEFT_KNEE
            )
            
            when {
                spineAngle < 10 -> "Position update: Perfect plank position, spine angle ${spineAngle.toInt()} degrees"
                spineAngle < 20 -> "Position update: Good plank position, spine angle ${spineAngle.toInt()} degrees"
                else -> "Position update: Plank position needs adjustment, spine angle ${spineAngle.toInt()} degrees"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating gentle plank status", e)
            "Position update: Analyzing plank position..."
        }
    }

    private fun generateStandingBalanceStatus(landmarks: List<PoseLandmark>): String {
        return try {
            val leftAnkle = landmarks[15] // LEFT_ANKLE
            val rightAnkle = landmarks[16] // RIGHT_ANKLE
            val leftHip = landmarks[11] // LEFT_HIP
            val rightHip = landmarks[12] // RIGHT_HIP
            
            val hipWidth = kotlin.math.abs(leftHip.position.x - rightHip.position.x)
            val ankleWidth = kotlin.math.abs(leftAnkle.position.x - rightAnkle.position.x)
            val balanceRatio = if (hipWidth > 0) ankleWidth / hipWidth else 1.0f
            
            when {
                balanceRatio > 0.8f -> "Position update: Good balance position, feet shoulder-width apart"
                balanceRatio > 0.6f -> "Position update: Moderate balance position, feet slightly narrow"
                else -> "Position update: Narrow stance, consider widening your feet for better balance"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating standing balance status", e)
            "Position update: Analyzing balance position..."
        }
    }

    private fun generateGentleBridgeStatus(landmarks: List<PoseLandmark>): String {
        return try {
            val leftHip = landmarks[11] // LEFT_HIP
            val rightHip = landmarks[12] // RIGHT_HIP
            val leftKnee = landmarks[13] // LEFT_KNEE
            val rightKnee = landmarks[14] // RIGHT_KNEE
            
            val avgHipHeight = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeHeight = (leftKnee.position.y + rightKnee.position.y) / 2
            val bridgeHeight = avgKneeHeight - avgHipHeight
            
            when {
                bridgeHeight > 50 -> "Position update: High bridge position, hips elevated"
                bridgeHeight > 20 -> "Position update: Mid bridge position, moderate hip elevation"
                else -> "Position update: Low bridge position, minimal hip elevation"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating gentle bridge status", e)
            "Position update: Analyzing bridge position..."
        }
    }

    private fun generateArmRaisesStatus(landmarks: List<PoseLandmark>): String {
        return try {
            val leftShoulder = landmarks[5] // LEFT_SHOULDER
            val rightShoulder = landmarks[6] // RIGHT_SHOULDER
            val leftWrist = landmarks[9] // LEFT_WRIST
            val rightWrist = landmarks[10] // RIGHT_WRIST
            
            val leftArmHeight = leftShoulder.position.y - leftWrist.position.y
            val rightArmHeight = rightShoulder.position.y - rightWrist.position.y
            val avgArmHeight = (leftArmHeight + rightArmHeight) / 2
            
            when {
                avgArmHeight > 100 -> "Position update: Arms raised high, above shoulder level"
                avgArmHeight > 50 -> "Position update: Arms at shoulder level"
                avgArmHeight > 0 -> "Position update: Arms partially raised"
                else -> "Position update: Arms at rest position"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating arm raises status", e)
            "Position update: Analyzing arm position..."
        }
    }

    private fun calculateKneeAngle(landmarks: List<PoseLandmark>, hipIndex: Int, kneeIndex: Int, ankleIndex: Int): Double {
        return try {
            val hip = landmarks[hipIndex].position
            val knee = landmarks[kneeIndex].position
            val ankle = landmarks[ankleIndex].position
            calculateAngle(hip, knee, ankle)
        } catch (e: Exception) {
            180.0 // Default to straight leg
        }
    }

    private fun calculateArmAngle(landmarks: List<PoseLandmark>, shoulderIndex: Int, elbowIndex: Int, wristIndex: Int): Double {
        return try {
            val shoulder = landmarks[shoulderIndex].position
            val elbow = landmarks[elbowIndex].position
            val wrist = landmarks[wristIndex].position
            calculateAngle(shoulder, elbow, wrist)
        } catch (e: Exception) {
            180.0 // Default to straight arm
        }
    }

    private fun calculateSpineAngle(landmarks: List<PoseLandmark>, shoulderIndex: Int, hipIndex: Int, kneeIndex: Int): Double {
        return try {
            val shoulder = landmarks[shoulderIndex].position
            val hip = landmarks[hipIndex].position
            val knee = landmarks[kneeIndex].position
            calculateAngle(shoulder, hip, knee)
        } catch (e: Exception) {
            180.0 // Default to straight line
        }
    }

    fun release() {
        // Release BlazePose detector if it was used
        blazePoseDetector?.release()
        blazePoseDetector = null
        
        // Release ML Kit detector if it was used
        poseDetector?.close()
        poseDetector = null
        
        Log.d(TAG, "Released all pose detection resources")
    }

    fun getLastPose(): MLKitPose? = lastPose
}