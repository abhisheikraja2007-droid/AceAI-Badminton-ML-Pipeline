package com.example.domain.model

data class AthleteProfile(
    val athleteId: String,
    val name: String,
    val heightCm: Double,
    val forearmLengthCm: Double,
    val dominantHand: String = "right",
    val skillLevel: String = "intermediate"
)

