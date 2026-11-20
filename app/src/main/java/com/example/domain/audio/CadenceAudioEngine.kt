package com.example.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CadenceAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isPlaying = false
    private var cadenceJob: Job? = null
    private var bpm = 135

    fun startCadence(targetBpm: Int = 135) {
        if (isPlaying) return
        this.bpm = targetBpm
        isPlaying = true

        cadenceJob = CoroutineScope(Dispatchers.Default).launch {
            val intervalMs = (60_000L / bpm)
            while (isActive && isPlaying) {
                // Play cadence tick pulse
                delay(intervalMs)
            }
        }
    }

    fun stopCadence() {
        isPlaying = false
        cadenceJob?.cancel()
        cadenceJob = null
    }

    fun isCadenceActive(): Boolean = isPlaying
}

