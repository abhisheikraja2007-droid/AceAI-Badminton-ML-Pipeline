package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.domain.model.PoseFrameResult
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun KinematicOverlayCanvas(
    elbowAngleDeg: Double,
    shoulderRotationDeg: Double,
    kneeFlexionDeg: Double,
    velocityKmH: Double,
    isFault: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Draw Architectural Telemetry Grid
        drawTelemetryGrid(w, h)

        // 2. Draw Corner Calibration Brackets
        drawCornerBrackets(w, h)

        // 3. Compute dynamic 2D projection points for Athlete Skeleton
        val spineBase = Offset(w * 0.45f, h * 0.55f)
        val neck = Offset(w * 0.48f, h * 0.38f)
        val head = Offset(w * 0.50f, h * 0.28f)

        // Right Arm (Racket Arm)
        val shoulderR = Offset(w * 0.44f, h * 0.38f)

        // Elbow computed dynamically from shoulder rotation
        val shoulderRad = Math.toRadians(shoulderRotationDeg - 90.0)
        val upperArmLen = w * 0.16f
        val elbowR = Offset(
            shoulderR.x + (cos(shoulderRad) * upperArmLen).toFloat(),
            shoulderR.y - (sin(shoulderRad) * upperArmLen).toFloat()
        )

        // Forearm computed dynamically from elbow angle
        val forearmRad = shoulderRad + Math.toRadians(elbowAngleDeg - 180.0)
        val forearmLen = w * 0.18f
        val wristR = Offset(
            elbowR.x + (cos(forearmRad) * forearmLen).toFloat(),
            elbowR.y + (sin(forearmRad) * forearmLen).toFloat()
        )

        // Racket Head Tip
        val racketTip = Offset(
            wristR.x + (cos(forearmRad) * (w * 0.14f)).toFloat(),
            wristR.y + (sin(forearmRad) * (w * 0.14f)).toFloat()
        )

        // Left Arm (Counterbalance)
        val shoulderL = Offset(w * 0.54f, h * 0.39f)
        val elbowL = Offset(w * 0.64f, h * 0.42f)
        val wristL = Offset(w * 0.72f, h * 0.38f)

        // Legs
        val hipR = Offset(w * 0.42f, h * 0.58f)
        val kneeRad = Math.toRadians(kneeFlexionDeg - 90.0)
        val kneeR = Offset(
            hipR.x + (cos(kneeRad) * (w * 0.12f)).toFloat(),
            hipR.y + (w * 0.18f)
        )
        val ankleR = Offset(kneeR.x - w * 0.04f, h * 0.88f)

        val hipL = Offset(w * 0.50f, h * 0.58f)
        val kneeL = Offset(w * 0.58f, h * 0.74f)
        val ankleL = Offset(w * 0.65f, h * 0.88f)

        // 4. Draw Vector Kinematic Segments (2px Cyan, faulty segment shifts to Crimson)
        val armColor = if (isFault) FaultCrimson else CyberCyan
        val standardColor = CyberCyan

        // Torso & Head
        drawLine(standardColor, spineBase, neck, strokeWidth = 2.5f)
        drawLine(standardColor, neck, head, strokeWidth = 2.5f)
        drawLine(standardColor, neck, shoulderR, strokeWidth = 2.5f)
        drawLine(standardColor, neck, shoulderL, strokeWidth = 2.5f)
        drawLine(standardColor, spineBase, hipR, strokeWidth = 2.5f)
        drawLine(standardColor, spineBase, hipL, strokeWidth = 2.5f)

        // Left Arm
        drawLine(standardColor, shoulderL, elbowL, strokeWidth = 2f)
        drawLine(standardColor, elbowL, wristL, strokeWidth = 2f)

        // Legs
        drawLine(standardColor, hipR, kneeR, strokeWidth = 2f)
        drawLine(standardColor, kneeR, ankleR, strokeWidth = 2f)
        drawLine(standardColor, hipL, kneeL, strokeWidth = 2f)
        drawLine(standardColor, kneeL, ankleL, strokeWidth = 2f)

        // Kinetic Chain Impact Arm (Key Monitored Segment)
        drawLine(armColor, shoulderR, elbowR, strokeWidth = 3f)
        drawLine(armColor, elbowR, wristR, strokeWidth = 3.5f)

        // Racket Ray & Shaft
        drawLine(OpticNeonLime, wristR, racketTip, strokeWidth = 2f)
        drawCircle(
            color = OpticNeonLime,
            radius = 16f,
            center = racketTip,
            style = Stroke(width = 1.5f)
        )

        // 5. Draw Peak Velocity Raycast (Vector from Racket Tip)
        val smashVectorEnd = Offset(
            racketTip.x - (w * 0.32f),
            racketTip.y + (h * 0.28f)
        )
        drawLine(
            color = OpticNeonLime,
            start = racketTip,
            end = smashVectorEnd,
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )

        // 6. Draw Joint Pivot Nodes (Outer 6px #CCFF00 / #FF3366, Inner 2px #0B0F19)
        drawJointNode(head, 8f)
        drawJointNode(neck, 5f)
        drawJointNode(shoulderL, 5f)
        drawJointNode(elbowL, 5f)
        drawJointNode(wristL, 5f)
        drawJointNode(spineBase, 6f)
        drawJointNode(hipR, 5f)
        drawJointNode(kneeR, 5f)
        drawJointNode(ankleR, 5f)
        drawJointNode(hipL, 5f)
        drawJointNode(kneeL, 5f)
        drawJointNode(ankleL, 5f)

        // Highlight Active Monitored Nodes
        drawJointNode(shoulderR, 7f, isFault = isFault)
        drawJointNode(elbowR, 8f, isFault = isFault)
        drawJointNode(wristR, 6f, isFault = isFault)

        // 7. Angle Arc Reticle at Elbow Joint
        val arcRadius = 32f
        drawCircle(
            color = if (isFault) FaultCrimson else OpticNeonLime,
            radius = arcRadius,
            center = elbowR,
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )
        )
    }
}

private fun DrawScope.drawJointNode(center: Offset, outerRadius: Float, isFault: Boolean = false) {
    val outerColor = if (isFault) FaultCrimson else OpticNeonLime
    drawCircle(
        color = outerColor,
        radius = outerRadius,
        center = center
    )
    drawCircle(
        color = CarbonBlack,
        radius = outerRadius * 0.45f,
        center = center
    )
}

private fun DrawScope.drawTelemetryGrid(w: Float, h: Float) {
    val gridColor = OutlineMuted.copy(alpha = 0.45f)
    val numLinesX = 6
    val numLinesY = 8

    for (i in 1..numLinesX) {
        val x = w * (i.toFloat() / (numLinesX + 1))
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.75f
        )
    }
    for (i in 1..numLinesY) {
        val y = h * (i.toFloat() / (numLinesY + 1))
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 0.75f
        )
    }
}

private fun DrawScope.drawCornerBrackets(w: Float, h: Float) {
    val bracketColor = CyberCyan.copy(alpha = 0.7f)
    val len = 24f
    val pad = 16f

    // Top-Left
    drawLine(bracketColor, Offset(pad, pad), Offset(pad + len, pad), strokeWidth = 2f)
    drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + len), strokeWidth = 2f)

    // Top-Right
    drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad - len, pad), strokeWidth = 2f)
    drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad, pad + len), strokeWidth = 2f)

    // Bottom-Left
    drawLine(bracketColor, Offset(pad, h - pad), Offset(pad + len, h - pad), strokeWidth = 2f)
    drawLine(bracketColor, Offset(pad, h - pad), Offset(pad, h - pad - len), strokeWidth = 2f)

    // Bottom-Right
    drawLine(bracketColor, Offset(w - pad, h - pad), Offset(w - pad - len, h - pad), strokeWidth = 2f)
    drawLine(bracketColor, Offset(w - pad, h - pad), Offset(w - pad, h - pad - len), strokeWidth = 2f)
}

/**
 * 60 FPS Jetpack Compose Canvas rendering the 33 MediaPipe pose landmarks and connections
 * directly from PoseFrameResult.
 */
@Composable
fun RealtimePoseSkeletonCanvas(
    poseResult: PoseFrameResult,
    isFault: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!poseResult.hasPose || poseResult.normalizedLandmarks.size < 33) return@Canvas

        val w = size.width
        val h = size.height

        val connections = listOf(
            // Torso
            11 to 12, 11 to 23, 12 to 24, 23 to 24,
            // Right arm (kinetic smash arm)
            12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22,
            // Left arm
            11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21,
            // Right leg
            24 to 26, 26 to 28, 28 to 30, 28 to 32,
            // Left leg
            23 to 25, 25 to 27, 27 to 29, 27 to 31
        )

        val landmarks = poseResult.normalizedLandmarks
        val boneColor = if (isFault) FaultCrimson else CyberCyan
        val smashArmColor = if (isFault) FaultCrimson else OpticNeonLime

        // Draw Skeletal Limbs
        for ((startIdx, endIdx) in connections) {
            val startLm = landmarks[startIdx]
            val endLm = landmarks[endIdx]
            if (startLm.visibility > 0.4f && endLm.visibility > 0.4f) {
                val start = Offset(startLm.x * w, startLm.y * h)
                val end = Offset(endLm.x * w, endLm.y * h)
                val isSmashArm = (startIdx == 12 && endIdx == 14) || (startIdx == 14 && endIdx == 16)
                val color = if (isSmashArm) smashArmColor else boneColor
                val stroke = if (isSmashArm) 4f else 2.5f
                drawLine(color = color, start = start, end = end, strokeWidth = stroke)
            }
        }

        // Draw Joint Pivot Nodes
        landmarks.forEachIndexed { idx, lm ->
            if (lm.visibility > 0.4f) {
                val center = Offset(lm.x * w, lm.y * h)
                val isKeySmashJoint = idx == 12 || idx == 14 || idx == 16
                val radius = if (isKeySmashJoint) 7f else 4f
                val color = if (isKeySmashJoint && isFault) FaultCrimson else if (isKeySmashJoint) OpticNeonLime else CyberCyan

                drawCircle(color = color, radius = radius, center = center)
                drawCircle(color = CarbonBlack, radius = radius * 0.45f, center = center)
            }
        }
    }
}
