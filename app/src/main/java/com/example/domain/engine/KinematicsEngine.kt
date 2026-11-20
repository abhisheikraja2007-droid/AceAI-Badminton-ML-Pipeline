package com.example.domain.engine

import android.util.Log
import com.example.domain.model.PoseFrameResult
import com.example.domain.model.PoseWorldLandmark
import com.example.domain.model.SmashEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real-time Biomechanical Kinematics Engine.
 *
 * Responsibilities:
 * 1. Maintains a 30-frame ring buffer of [PoseFrameResult] instances.
 * 2. Computes 3D Euclidean wrist velocity scaled with anthropometric forearm ratio & racket lever multiplier.
 * 3. Applies a 3-frame Exponential Moving Average (EMA) to eliminate MediaPipe landmark jitter.
 * 4. Calculates 3D vector dot-product angles for elbow extension and shoulder elevation.
 * 5. Evaluates neural stroke classification via [TFLiteStrokeClassifier] on velocity spikes (>110 km/h).
 * 6. Emits debounced (1200ms cooldown) [SmashEvent] streams with mechanical fault diagnostics.
 */
@Singleton
class KinematicsEngine @Inject constructor(
    private val strokeClassifier: TFLiteStrokeClassifier
) {
    companion object {
        private const val TAG = "KinematicsEngine"
        const val RING_BUFFER_CAPACITY = 30
        const val NUM_BIOMECHANICAL_FEATURES = 269
        const val DEFAULT_SMASH_SPEED_THRESHOLD_KMH = 110.0
        const val DEFAULT_SMASH_PROBABILITY_THRESHOLD = 0.80f
        const val DEFAULT_COOLDOWN_DEBOUNCE_MS = 1200L

        // Standard anthropometric defaults (meters)
        const val NOMINAL_FOREARM_LENGTH_METERS = 0.28
        const val DEFAULT_RACKET_LEVER_MULTIPLIER = 1.45

        // Biomechanical Ideal Standards (BWF Certification)
        const val IDEAL_ELBOW_MIN_DEG = 168.0
        const val IDEAL_ELBOW_MAX_DEG = 176.0
        const val IDEAL_SHOULDER_MIN_DEG = 125.0
    }

    // Configurable biomechanical parameters
    var smashThresholdKmh: Double = DEFAULT_SMASH_SPEED_THRESHOLD_KMH
    var smashProbabilityThreshold: Float = DEFAULT_SMASH_PROBABILITY_THRESHOLD
    var cooldownDurationMs: Long = DEFAULT_COOLDOWN_DEBOUNCE_MS
    var calibratedForearmLengthMeters: Double = NOMINAL_FOREARM_LENGTH_METERS
    var racketLeverMultiplier: Double = DEFAULT_RACKET_LEVER_MULTIPLIER
    var isRightHandDominant: Boolean = true

    // Ring buffer of the last 30 frames
    private val ringBuffer = ArrayDeque<PoseFrameResult>(RING_BUFFER_CAPACITY)

    // EMA smoothing state (3-frame EMA: alpha = 2 / (3 + 1) = 0.50)
    private val emaAlpha = 0.50
    private var smoothedWristVelocityKmh: Double = 0.0
    private var lastWristX = 0f
    private var lastWristY = 0f
    private var lastWristZ = 0f
    private var lastFrameTimestampMs = 0L

    // Debounce state tracking
    private var lastSmashTimestampMs = 0L

    // Reactive Smash Event Stream
    private val _smashEvents = MutableSharedFlow<SmashEvent>(
        replay = 1,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val smashEvents: SharedFlow<SmashEvent> = _smashEvents.asSharedFlow()

    data class LiveTelemetry(
        val instantaneousVelocityKmh: Double = 0.0,
        val smoothedVelocityKmh: Double = 0.0,
        val elbowExtensionDeg: Double = 0.0,
        val shoulderElevationDeg: Double = 0.0,
        val contactHeightMeters: Double = 0.0,
        val isSmashCandidate: Boolean = false,
        val timestampMs: Long = 0L
    )

    private val _liveTelemetry = MutableStateFlow(LiveTelemetry())
    val liveTelemetry: StateFlow<LiveTelemetry> = _liveTelemetry.asStateFlow()

    init {
        strokeClassifier.initialize()
    }

    /**
     * Primary ingestion pipeline processing real-time pose results at 60 FPS.
     */
    fun processPoseFrame(frame: PoseFrameResult) {
        if (!frame.hasPose) return

        // 1. Maintain 30-frame ring buffer
        synchronized(ringBuffer) {
            if (ringBuffer.size >= RING_BUFFER_CAPACITY) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(frame)
        }

        // Select dominant arm landmarks: Right hand (12, 14, 16, 24) vs Left hand (11, 13, 15, 23)
        val shoulderIdx = if (isRightHandDominant) 12 else 11
        val elbowIdx = if (isRightHandDominant) 14 else 13
        val wristIdx = if (isRightHandDominant) 16 else 15
        val hipIdx = if (isRightHandDominant) 24 else 23
        val noseIdx = 0

        val worldLms = frame.worldLandmarks
        val normLms = frame.normalizedLandmarks

        val shoulderW = worldLms.getOrNull(shoulderIdx) ?: return
        val elbowW = worldLms.getOrNull(elbowIdx) ?: return
        val wristW = worldLms.getOrNull(wristIdx) ?: return
        val hipW = worldLms.getOrNull(hipIdx) ?: return
        val noseW = worldLms.getOrNull(noseIdx)

        // 2. Compute 3D Instantaneous Wrist Velocity
        val currentTimestampMs = frame.timestampMs
        val dt = if (lastFrameTimestampMs > 0L) {
            ((currentTimestampMs - lastFrameTimestampMs) / 1000.0).coerceIn(0.005, 0.20)
        } else {
            1.0 / 60.0
        }

        val dx = (wristW.x - lastWristX).toDouble()
        val dy = (wristW.y - lastWristY).toDouble()
        val dz = (wristW.z - lastWristZ).toDouble()
        val deltaDistanceM = sqrt(dx * dx + dy * dy + dz * dz)

        // Measured forearm length in meters between elbow and wrist
        val measuredForearmM = calculateEuclideanDistance(elbowW, wristW).coerceAtLeast(0.10)
        val anthropometricScaleRatio = calibratedForearmLengthMeters / measuredForearmM

        // Velocity scaled by anthropometrics & racket lever multiplier
        val rawVelocityMs = if (lastFrameTimestampMs > 0L) {
            (deltaDistanceM / dt) * anthropometricScaleRatio * racketLeverMultiplier
        } else {
            0.0
        }
        val rawVelocityKmh = rawVelocityMs * 3.6

        // 3-Frame Exponential Moving Average (EMA) smoothing
        smoothedWristVelocityKmh = if (lastFrameTimestampMs > 0L) {
            emaAlpha * rawVelocityKmh + (1.0 - emaAlpha) * smoothedWristVelocityKmh
        } else {
            rawVelocityKmh
        }

        lastWristX = wristW.x
        lastWristY = wristW.y
        lastWristZ = wristW.z
        lastFrameTimestampMs = currentTimestampMs

        // 3. Compute 3D Biomechanical Angles using Vector Dot Products
        // Dominant Elbow Extension: Shoulder(A) -> Elbow(B) -> Wrist(C)
        val elbowAngleDeg = calculateAngle3D(
            ax = shoulderW.x, ay = shoulderW.y, az = shoulderW.z,
            bx = elbowW.x, by = elbowW.y, bz = elbowW.z,
            cx = wristW.x, cy = wristW.y, cz = wristW.z
        )

        // Dominant Shoulder Elevation: Hip(A) -> Shoulder(B) -> Elbow(C)
        val shoulderAngleDeg = calculateAngle3D(
            ax = hipW.x, ay = hipW.y, az = hipW.z,
            bx = shoulderW.x, by = shoulderW.y, bz = shoulderW.z,
            cx = elbowW.x, cy = elbowW.y, cz = elbowW.z
        )

        // Contact height relative to nose / hips in meters
        val contactHeightM = if (noseW != null) {
            (noseW.y - wristW.y).toDouble()
        } else {
            (hipW.y - wristW.y).toDouble()
        }

        val isSpeedSpike = smoothedWristVelocityKmh >= smashThresholdKmh
        _liveTelemetry.value = LiveTelemetry(
            instantaneousVelocityKmh = rawVelocityKmh,
            smoothedVelocityKmh = smoothedWristVelocityKmh,
            elbowExtensionDeg = elbowAngleDeg,
            shoulderElevationDeg = shoulderAngleDeg,
            contactHeightMeters = contactHeightM,
            isSmashCandidate = isSpeedSpike,
            timestampMs = currentTimestampMs
        )

        // 4. Trigger Logic & 1200ms Debounce Check
        val timeSinceLastSmash = currentTimestampMs - lastSmashTimestampMs
        if (isSpeedSpike && timeSinceLastSmash >= cooldownDurationMs) {
            evaluateSmashCandidate(
                impactVelocityKmh = smoothedWristVelocityKmh,
                impactElbowDeg = elbowAngleDeg,
                impactShoulderDeg = shoulderAngleDeg,
                contactHeightM = contactHeightM,
                timestampMs = currentTimestampMs
            )
        }
    }

    /**
     * Extracts the 30-frame feature window, queries TFLite, and emits a validated SmashEvent.
     */
    private fun evaluateSmashCandidate(
        impactVelocityKmh: Double,
        impactElbowDeg: Double,
        impactShoulderDeg: Double,
        contactHeightM: Double,
        timestampMs: Long
    ) {
        val windowSnapshot: List<PoseFrameResult>
        synchronized(ringBuffer) {
            if (ringBuffer.size < RING_BUFFER_CAPACITY) return
            windowSnapshot = ringBuffer.toList()
        }

        // Build [30, 269] feature sequence for TFLite
        val featureSequence = Array(RING_BUFFER_CAPACITY) { FloatArray(NUM_BIOMECHANICAL_FEATURES) }
        for (i in 0 until RING_BUFFER_CAPACITY) {
            val f = windowSnapshot[i]
            val row = featureSequence[i]

            // 1. Pack 33 normalized landmarks (99 features)
            for (j in 0 until 33) {
                val lm = f.normalizedLandmarks.getOrNull(j)
                row[j * 3 + 0] = lm?.x ?: 0f
                row[j * 3 + 1] = lm?.y ?: 0f
                row[j * 3 + 2] = lm?.z ?: 0f
            }

            // 2. Pack 33 world landmarks (99 features)
            val worldOffset = 99
            for (j in 0 until 33) {
                val wlm = f.worldLandmarks.getOrNull(j)
                row[worldOffset + j * 3 + 0] = wlm?.x ?: 0f
                row[worldOffset + j * 3 + 1] = wlm?.y ?: 0f
                row[worldOffset + j * 3 + 2] = wlm?.z ?: 0f
            }

            // 3. Dominant kinetic telemetry
            val metaOffset = 198
            row[metaOffset + 0] = impactVelocityKmh.toFloat()
            row[metaOffset + 1] = impactElbowDeg.toFloat()
            row[metaOffset + 2] = impactShoulderDeg.toFloat()
            row[metaOffset + 3] = contactHeightM.toFloat()
        }

        // Run TFLite Neural Inference
        val prediction = strokeClassifier.classify(featureSequence)

        if (prediction.strokeClass == TFLiteStrokeClassifier.CLASS_OVERHEAD_SMASH &&
            prediction.smashProbability >= smashProbabilityThreshold
        ) {
            // Apply 1200ms debounce cooldown
            lastSmashTimestampMs = timestampMs

            // Evaluate biomechanical flaw criteria
            val (isFault, faultReason) = evaluateBiomechanicalFault(impactElbowDeg, impactShoulderDeg, impactVelocityKmh)

            val event = SmashEvent(
                peakVelocityKmh = impactVelocityKmh,
                impactElbowAngleDeg = impactElbowDeg,
                impactShoulderAngleDeg = impactShoulderDeg,
                contactHeightMeters = contactHeightM,
                timestampMs = timestampMs,
                probability = prediction.smashProbability,
                isFault = isFault,
                faultReason = faultReason
            )

            Log.i(TAG, "⚡ Verified Smash: ${String.format("%.1f", impactVelocityKmh)} km/h | Elbow: ${String.format("%.1f", impactElbowDeg)}° | P=${String.format("%.2f", prediction.smashProbability)}")
            _smashEvents.tryEmit(event)
        }
    }

    /**
     * Calculates 3D angle between BA and BC at vertex B:
     * theta = arccos((BA · BC) / (|BA| * |BC|))
     */
    fun calculateAngle3D(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Double {
        val vbaX = (ax - bx).toDouble()
        val vbaY = (ay - by).toDouble()
        val vbaZ = (az - bz).toDouble()

        val vbcX = (cx - bx).toDouble()
        val vbcY = (cy - by).toDouble()
        val vbcZ = (cz - bz).toDouble()

        val dot = vbaX * vbcX + vbaY * vbcY + vbaZ * vbcZ
        val magBA = sqrt(vbaX * vbaX + vbaY * vbaY + vbaZ * vbaZ)
        val magBC = sqrt(vbcX * vbcX + vbcY * vbcY + vbcZ * vbcZ)

        val denominator = magBA * magBC
        if (denominator <= 1e-7) return 0.0

        val cosTheta = (dot / denominator).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosTheta))
    }

    private fun calculateEuclideanDistance(p1: PoseWorldLandmark, p2: PoseWorldLandmark): Double {
        val dx = (p1.x - p2.x).toDouble()
        val dy = (p1.y - p2.y).toDouble()
        val dz = (p1.z - p2.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun evaluateBiomechanicalFault(
        elbowAngleDeg: Double,
        shoulderAngleDeg: Double,
        velocityKmh: Double
    ): Pair<Boolean, String> {
        return when {
            elbowAngleDeg < IDEAL_ELBOW_MIN_DEG -> {
                true to "Collapsed Elbow (${String.format("%.1f", elbowAngleDeg)}° < 168°). Lever radius reduced."
            }
            shoulderAngleDeg < IDEAL_SHOULDER_MIN_DEG -> {
                true to "Low Shoulder Elevation (${String.format("%.1f", shoulderAngleDeg)}° < 125°). Contact behind body."
            }
            velocityKmh < 140.0 -> {
                true to "Sub-optimal Kinetic Whip Transfer (${String.format("%.1f", velocityKmh)} km/h)."
            }
            else -> {
                false to "Nominal Kinetic Sequencing"
            }
        }
    }

    fun reset() {
        synchronized(ringBuffer) {
            ringBuffer.clear()
        }
        smoothedWristVelocityKmh = 0.0
        lastFrameTimestampMs = 0L
        lastSmashTimestampMs = 0L
    }
}

