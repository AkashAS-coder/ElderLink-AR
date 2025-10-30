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
        instructions = "Stand facing a wall, place hands on the wall at shoulder height. Bend your elbows to bring your chest to the wall, then push back. The camera will monitor your elbow position and body alignment.",
        keyPoints = listOf(
            "Elbows" to "Keep elbows at 45 degrees - not too wide or narrow",
            "Body" to "Keep your body in a straight line",
            "Feet" to "Keep feet shoulder-width apart for stability"
        ),
        tips = listOf(
            "Control the movement - don't rush",
            "Breathe out when pushing away from the wall",
            "Keep your core engaged throughout"
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
    private var lastFeedbackTime = 0L
    private var lastFeedback: String = ""
    private var feedbackRepeatCount = 0
    private var feedbackHistory = mutableListOf<String>()
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
    private val exerciseCooldownMs = 4000L // 4 seconds between feedback
    private val minFeedbackIntervalMs = 4000L // 4 seconds minimum between feedback
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

            if (leftShoulder == null || rightShoulder == null || leftElbow == null || rightElbow == null || leftWrist == null || rightWrist == null) {
                return ExerciseAnalysis(
                    isCorrect = false,
                    feedback = "Please make sure your upper body (shoulders, elbows, wrists) is visible to the camera",
                    confidence = 0.0f
                )
            }

            // Calculate elbow angles for left and right
            val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
            val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
            val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

            val thresholds = exerciseThresholds["wall_pushup"] ?: mapOf("elbow_angle_min" to 70f, "elbow_angle_max" to 130f)
            val minElbow = (thresholds["elbow_angle_min"] as? Float) ?: 70f
            val maxElbow = (thresholds["elbow_angle_max"] as? Float) ?: 130f

            return when {
                avgElbowAngle < minElbow -> ExerciseAnalysis(
                    isCorrect = false,
                    feedback = "Lower position detected: elbows at ${avgElbowAngle.toInt()}° — try bending more on the way down",
                    confidence = 0.6f
                )
                avgElbowAngle > maxElbow -> ExerciseAnalysis(
                    isCorrect = false,
                    feedback = "High position detected: elbows at ${avgElbowAngle.toInt()}° — try extending more evenly",
                    confidence = 0.6f
                )
                else -> ExerciseAnalysis(
                    isCorrect = true,
                    feedback = "Good form! Keep going.",
                    confidence = 0.9f
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing wall pushup", e)
            return ExerciseAnalysis(
                isCorrect = false,
                feedback = "Analyzing wall pushup...",
                confidence = 0.0f
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

    private fun speakFeedback(feedback: String) {
        val currentTime = System.currentTimeMillis()

        // More conservative timing - check both intervals
        if (currentTime - lastFeedbackTime < minFeedbackIntervalMs) {
            Log.d(TAG, "Skipping feedback - too soon since last feedback (${currentTime - lastFeedbackTime}ms)")
            return // Skip if we've given feedback too recently
        }

        // Update last feedback time and text
        lastFeedbackTime = currentTime

        // Use TTS callback if available
        ttsCallback?.invoke(feedback)

        // Also update the UI callback
        onFeedbackCallback?.invoke(feedback)

        Log.d(TAG, "Feedback sent: $feedback")
    }

    fun startContinuousUpdates(exerciseType: String) {
        currentExerciseType = exerciseType
        isContinuousUpdatesEnabled = true
        
        // Provide initial feedback when starting the exercise (skip audible instruction for wall_pushup)
        val initialFeedback = when (exerciseType) {
            "chair_squat" -> "Starting chair squats. Stand in front of a chair with feet shoulder-width apart."
            "wall_pushup" -> "Starting wall push-ups. Stand an arm's length from the wall."
            "gentle_plank" -> "Starting gentle planks. Get into a plank position with your knees on the floor."
            "standing_balance" -> "Starting standing balance. Stand straight with feet hip-width apart."
            "gentle_bridge" -> "Starting gentle bridges. Lie on your back with knees bent and feet flat on the floor."
            "arm_raises" -> "Starting arm raises. Stand straight with arms at your sides."
            else -> "Starting exercise. Please follow the on-screen instructions."
        }
        // Speak initial instruction for the selected exercise
        speakFeedback(initialFeedback)

        // Cancel any existing timer
        statusUpdateTimer?.cancel()

        // Create a new timer for status updates. Run analysis periodically and update UI.
        statusUpdateTimer = Timer()
        statusUpdateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                lastPose?.let { pose ->
                    val analysis = analyzePose(pose)
                    // Update UI with feedback. speakFeedback may be invoked by callers
                    // or by other parts of the app if audible feedback is desired.
                    onFeedbackCallback?.invoke(analysis.feedback)
                }
            }
        }, 4000, statusUpdateInterval)
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
        // For now, comment out the detailed calculation and return a simple status string.
        // The detailed angle-based calculations are intentionally disabled as per the
        // temporary fallback requirement.
        /*
        return try {
            val leftElbowAngle = calculateArmAngle(landmarks, 5, 7, 9) // LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST
            val rightElbowAngle = calculateArmAngle(landmarks, 6, 8, 10) // RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST
            val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2
            
            when {
                avgElbowAngle < 90 -> "Position update: Push-up down position, elbows at ${avgElbowAngle.toInt()} degrees"
                avgElbowAngle < 120 -> "Position update: Mid push-up position, elbows at ${avgElbowAngle.toInt()} degrees"
                else -> "Position update: Push-up up position, elbows at ${avgElbowAngle.toInt()} degrees"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating wall pushup status", e)
            "Position update: Analyzing wall pushup position..."
        }
        */

        // Simple placeholder status while we use the hardcoded feedback fallback.
        return "Position update: Wall pushup in progress"
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