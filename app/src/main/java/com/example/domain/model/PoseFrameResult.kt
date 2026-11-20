package com.example.domain.model

/**
 * 2D/3D Normalized screen landmark [0.0, 1.0] for Jetpack Compose Canvas rendering.
 */
data class PoseNormalizedLandmark(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 0f,
    val presence: Float = 0f
)

/**
 * Real-world 3D landmark in metric coordinates (meters) centered at hips origin
 * for the Kinematic Physics & Biomechanics Math Engine.
 */
data class PoseWorldLandmark(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 0f,
    val presence: Float = 0f
)

/**
 * Complete output bundle for a single analyzed camera frame.
 */
data class PoseFrameResult(
    val normalizedLandmarks: List<PoseNormalizedLandmark>,
    val worldLandmarks: List<PoseWorldLandmark>,
    val timestampMs: Long,
    val inferenceTimeMs: Long,
    val hasPose: Boolean = normalizedLandmarks.isNotEmpty()
) {
    companion object {
        val EMPTY = PoseFrameResult(
            normalizedLandmarks = emptyList(),
            worldLandmarks = emptyList(),
            timestampMs = 0L,
            inferenceTimeMs = 0L,
            hasPose = false
        )
    }
}

