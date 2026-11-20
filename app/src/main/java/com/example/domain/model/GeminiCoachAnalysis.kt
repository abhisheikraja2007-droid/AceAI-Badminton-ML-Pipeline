package com.example.domain.model

data class KineticAdjustment(
    val jointName: String,
    val measuredValue: Double,
    val targetValue: Double,
    val actionableCue: String
)

data class CourtDrill(
    val drillName: String,
    val setsAndReps: String,
    val focusMetric: String
)

data class GeminiCoachAnalysis(
    val biomechanicalGrade: String,
    val primaryFlawDetected: String,
    val physicsExplanation: String,
    val kineticChainAdjustments: List<KineticAdjustment>,
    val recommendedCourtDrill: CourtDrill,
    val voiceCoachSummary: String
)

