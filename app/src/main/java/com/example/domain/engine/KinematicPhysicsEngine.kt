package com.example.domain.engine

import com.example.domain.model.StrokeKinematics
import com.example.domain.model.StrokeType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.acos
import kotlin.math.sqrt

@Singleton
class KinematicPhysicsEngine @Inject constructor() {

    companion object {
        const val IDEAL_ELBOW_MIN = 168.0
        const val IDEAL_ELBOW_MAX = 176.0
        const val IDEAL_SHOULDER_MIN = 125.0
        const val SMASH_VELOCITY_THRESHOLD_KMH = 180.0
    }

    /**
     * Calculates 3D angle at vertex B between A->B and C->B.
     */
    fun calculateAngle3D(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Double {
        val vbaX = ax - bx
        val vbaY = ay - by
        val vbaZ = az - bz

        val vbcX = cx - bx
        val vbcY = cy - by
        val vbcZ = cz - bz

        val dot = (vbaX * vbcX + vbaY * vbcY + vbaZ * vbcZ).toDouble()
        val magBA = sqrt((vbaX * vbaX + vbaY * vbaY + vbaZ * vbaZ).toDouble())
        val magBC = sqrt((vbcX * vbcX + vbcY * vbcY + vbcZ * vbcZ).toDouble())

        val denominator = magBA * magBC
        if (denominator <= 1e-7) return 0.0

        val cosTheta = (dot / denominator).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosTheta))
    }

    fun evaluateSmashFault(
        elbowAngleDeg: Double,
        shoulderAngleDeg: Double,
        velocityKmH: Double
    ): Pair<Boolean, String> {
        return when {
            elbowAngleDeg < IDEAL_ELBOW_MIN -> {
                true to "Collapsed Elbow (${String.format("%.1f", elbowAngleDeg)}° < 168°). Extends lever arm."
            }
            shoulderAngleDeg < IDEAL_SHOULDER_MIN -> {
                true to "Low Shoulder Elevation (${String.format("%.1f", shoulderAngleDeg)}° < 125°). Risk of impingement."
            }
            velocityKmH < SMASH_VELOCITY_THRESHOLD_KMH -> {
                true to "Sub-optimal Kinetic Transfer (${String.format("%.1f", velocityKmH)} km/h)."
            }
            else -> {
                false to "Nominal Kinetic Sequencing"
            }
        }
    }
}

