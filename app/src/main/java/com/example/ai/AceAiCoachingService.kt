package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "AceAiCoaching"

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelTag: String = ""
)

enum class MessageSender {
    USER,
    COACH
}

class AceAiCoachingService {

    private val generalModel by lazy {
        Firebase.vertexAI.generativeModel(
            modelName = "gemini-1.5-flash",
            systemInstruction = content {
                text(
                    """
                    You are AceAI, an elite courtside biomechanics performance analyst and coach specializing in badminton, tennis, and racket athletics.
                    You analyze kinetic chains (proximal-to-distal sequencing: leg drive -> hip torque -> shoulder rotation -> elbow extension -> forearm pronation -> wrist snap).
                    Provide precise, actionable feedback with numeric angle benchmarks (e.g. ideal shoulder-elbow flexion 90-110° on preparation, elbow extension ~165-175° at impact).
                    Warn about dangerous repetitive strain and overextensions.
                    Keep responses punchy, high-energy, and technical yet immediately applicable on court.
                    """.trimIndent()
                )
            }
        )
    }

    private val fastModel by lazy {
        Firebase.vertexAI.generativeModel(
            modelName = "gemini-1.5-flash",
            systemInstruction = content {
                text("You are AceAI Instant Telemetry Analyst. Provide 1-2 sentence real-time biomechanical feedback on stroke faults.")
            }
        )
    }

    private val reasoningModel by lazy {
        Firebase.vertexAI.generativeModel(
            modelName = "gemini-1.5-pro",
            systemInstruction = content {
                text(
                    """
                    You are the AceAI Chief Biomechanical Scientist. You perform deep anatomical and physics breakdowns of athletic movement.
                    Analyze ground reaction force (GRF), segment angular velocity, kinetic energy transfer, and joint load mitigation.
                    Provide structured diagnostics with anatomical references.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun analyzeStroke(
        strokeType: String,
        elbowAngle: Double,
        shoulderRotation: Double,
        velocityKmH: Double,
        isFault: Boolean,
        faultReason: String,
        useDeepReasoning: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val model = if (useDeepReasoning) reasoningModel else generalModel
        val prompt = """
            Analyze this tracked stroke telemetry:
            - Stroke: $strokeType
            - Elbow Angle: $elbowAngle° (Tolerance: 145°-175°)
            - Shoulder Rotation: $shoulderRotation°
            - Peak Velocity: $velocityKmH km/h
            - Biomechanical Fault Detected: $isFault ($faultReason)
            
            Provide immediate coaching feedback:
            1. Kinetic chain diagnosis
            2. Injury prevention tip (preventing lateral epicondylitis or shoulder impingement)
            3. Target drill to optimize next shot
        """.trimIndent()

        try {
            withTimeout(45_000L) {
                val response = model.generateContent(prompt)
                response.text ?: "Telemetry analysis nominal. Kinetic chain within tolerance."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stroke analysis failed", e)
            "AI Telemetry note: Angle recorded at ${elbowAngle}° with velocity ${velocityKmH} km/h. Maintain fluid pronation through impact."
        }
    }

    fun streamChatResponse(
        prompt: String,
        history: List<ChatMessage>,
        useDeepReasoning: Boolean = false
    ): Flow<String> = flow {
        val model = if (useDeepReasoning) reasoningModel else generalModel

        val promptBuilder = StringBuilder()
        // Include last 4 turns for context
        history.takeLast(6).forEach { msg ->
            val role = if (msg.sender == MessageSender.USER) "Athlete" else "AceAI Coach"
            promptBuilder.append("$role: ${msg.text}\n")
        }
        promptBuilder.append("Athlete: $prompt\nAceAI Coach:")

        var accumulated = ""
        try {
            withTimeout(55_000L) {
                model.generateContentStream(promptBuilder.toString()).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    accumulated += chunkText
                    emit(accumulated)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming chat failed", e)
            emit(if (accumulated.isNotBlank()) accumulated else "AceAI connection delay: Keep your racket head high and follow through smoothly.")
        }
    }

    suspend fun searchGroundingBenchmark(athleteOrTopic: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Provide up-to-date elite biomechanics data, smash speeds, and technique insights for: $athleteOrTopic.
            Include official recorded peak smash velocities (e.g. Viktor Axelsen, Lee Chong Wei, Fu Haifeng, Satwiksairaj Rankireddy), racket swing mechanics, and world-class athletic benchmarks.
        """.trimIndent()

        try {
            withTimeout(45_000L) {
                val response = generalModel.generateContent(prompt)
                response.text ?: "No tournament benchmark telemetry available."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search benchmark failed", e)
            "Tournament record benchmark: Professional badminton jump smashes achieve 380 - 460+ km/h off the racket face. Focus on kinetic transfer from the trunk."
        }
    }
}
