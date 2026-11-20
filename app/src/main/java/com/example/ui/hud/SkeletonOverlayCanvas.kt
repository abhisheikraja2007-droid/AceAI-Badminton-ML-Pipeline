package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PoseFrameResult
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.OpticNeonLime
import kotlin.math.roundToInt

@Composable
fun SkeletonOverlayCanvas(
    poseFrame: PoseFrameResult,
    elbowAngleDeg: Double,
    isRightHanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!poseFrame.hasPose || poseFrame.normalizedLandmarks.size < 33) return@Canvas

        val w = size.width
        val h = size.height
        val landmarks = poseFrame.normalizedLandmarks

        // Dominant arm dynamic coloring:
        // Neon-lime when elbow angle >= 165°, Amber/Red when collapsed < 150°
        val dominantArmColor = when {
            elbowAngleDeg >= 165.0 -> OpticNeonLime
            elbowAngleDeg < 150.0 -> FaultCrimson
            else -> Color(0xFFFFB300) // Transition Amber
        }

        val boneColor = CyberCyan.copy(alpha = 0.85f)
        val jointNodeColor = CyberCyan

        val dominantShoulderIdx = if (isRightHanded) 12 else 11
        val dominantElbowIdx = if (isRightHanded) 14 else 13
        val dominantWristIdx = if (isRightHanded) 16 else 15

        val standardConnections = listOf(
            // Torso Core
            11 to 12, 11 to 23, 12 to 24, 23 to 24,
            // Non-dominant arm
            (if (isRightHanded) 11 to 13 else 12 to 14),
            (if (isRightHanded) 13 to 15 else 14 to 16),
            (if (isRightHanded) 15 to 17 else 16 to 18),
            (if (isRightHanded) 15 to 19 else 16 to 20),
            // Right leg
            24 to 26, 26 to 28, 28 to 30, 28 to 32,
            // Left leg
            23 to 25, 25 to 27, 27 to 29, 27 to 31
        )

        // 1. Draw standard anti-aliased neon-cyan skeletal bones
        for ((startIdx, endIdx) in standardConnections) {
            val startLm = landmarks.getOrNull(startIdx) ?: continue
            val endLm = landmarks.getOrNull(endIdx) ?: continue

            if (startLm.visibility > 0.35f && endLm.visibility > 0.35f) {
                drawLine(
                    color = boneColor,
                    start = Offset(startLm.x * w, startLm.y * h),
                    end = Offset(endLm.x * w, endLm.y * h),
                    strokeWidth = 3f
                )
            }
        }

        // 2. Draw Dominant Kinetic Smash Arm with dynamic feedback color
        val shoulderLm = landmarks.getOrNull(dominantShoulderIdx)
        val elbowLm = landmarks.getOrNull(dominantElbowIdx)
        val wristLm = landmarks.getOrNull(dominantWristIdx)

        if (shoulderLm != null && elbowLm != null && shoulderLm.visibility > 0.35f && elbowLm.visibility > 0.35f) {
            val shoulderPos = Offset(shoulderLm.x * w, shoulderLm.y * h)
            val elbowPos = Offset(elbowLm.x * w, elbowLm.y * h)
            // Upper Arm
            drawLine(
                color = dominantArmColor,
                start = shoulderPos,
                end = elbowPos,
                strokeWidth = 6f
            )
        }

        if (elbowLm != null && wristLm != null && elbowLm.visibility > 0.35f && wristLm.visibility > 0.35f) {
            val elbowPos = Offset(elbowLm.x * w, elbowLm.y * h)
            val wristPos = Offset(wristLm.x * w, wristLm.y * h)
            // Forearm
            drawLine(
                color = dominantArmColor,
                start = elbowPos,
                end = wristPos,
                strokeWidth = 7f
            )

            // Racket Head Kinetic Projection Ray
            val dirX = wristPos.x - elbowPos.x
            val dirY = wristPos.y - elbowPos.y
            val racketTip = Offset(wristPos.x + dirX * 0.45f, wristPos.y + dirY * 0.45f)
            drawLine(
                color = dominantArmColor.copy(alpha = 0.9f),
                start = wristPos,
                end = racketTip,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
            )
            drawCircle(
                color = dominantArmColor,
                radius = 12f,
                center = racketTip,
                style = Stroke(width = 2f)
            )
        }

        // 3. Draw Joint Pivot Nodes
        landmarks.forEachIndexed { idx, lm ->
            if (lm.visibility > 0.35f) {
                val center = Offset(lm.x * w, lm.y * h)
                val isDominant = idx == dominantShoulderIdx || idx == dominantElbowIdx || idx == dominantWristIdx
                val outerRadius = if (isDominant) 8f else 4.5f
                val color = if (isDominant) dominantArmColor else jointNodeColor

                drawCircle(color = color, radius = outerRadius, center = center)
                drawCircle(color = CarbonBlack, radius = outerRadius * 0.45f, center = center)
            }
        }

        // 4. Render Live Degree Badge next to the Elbow Joint
        if (elbowLm != null && elbowLm.visibility > 0.35f && elbowAngleDeg > 0.0) {
            val elbowX = elbowLm.x * w
            val elbowY = elbowLm.y * h

            val badgeText = "${elbowAngleDeg.roundToInt()}°"
            val textLayout = textMeasurer.measure(
                text = badgeText,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            )

            val badgePadding = 8f
            val badgeWidth = textLayout.size.width + badgePadding * 2
            val badgeHeight = textLayout.size.height + badgePadding * 1.5f

            // Offset badge to the outer side of the elbow to avoid limb obstruction
            val offsetX = if (isRightHanded) 22f else -(badgeWidth + 22f)
            val badgeTopLeft = Offset(elbowX + offsetX, elbowY - badgeHeight / 2f)

            // Arc Reticle around elbow pivot
            drawCircle(
                color = dominantArmColor.copy(alpha = 0.5f),
                radius = 28f,
                center = Offset(elbowX, elbowY),
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
            )

            // Draw Dark Elevated Pill Background
            drawRoundRect(
                color = CarbonBlack.copy(alpha = 0.85f),
                topLeft = badgeTopLeft,
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // Draw Border in dynamic color
            drawRoundRect(
                color = dominantArmColor,
                topLeft = badgeTopLeft,
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.5f)
            )

            // Draw Degree Text
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(badgeTopLeft.x + badgePadding, badgeTopLeft.y + badgePadding * 0.75f)
            )
        }
    }
}

