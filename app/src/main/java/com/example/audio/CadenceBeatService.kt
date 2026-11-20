package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class CadenceBeatService {
    private var job: Job? = null
    var isPlaying = false
        private set

    // Generate high-frequency futuristic courtside acoustic click
    private val sampleRate = 44100
    private val clickDurationMs = 30
    private val numSamples = (sampleRate * clickDurationMs) / 1000
    private val clickBuffer = ShortArray(numSamples)

    init {
        val freq = 1200.0 // 1.2 kHz crisp high-tech click
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = 1.0 - (i.toDouble() / numSamples) // Decaying click
            val sample = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE * 0.8).toInt()
            clickBuffer[i] = sample.toShort()
        }
    }

    private val audioTrack: AudioTrack by lazy {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(clickBuffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun startBeat(bpm: Int, scope: CoroutineScope) {
        stopBeat()
        isPlaying = true
        val intervalMs = (60_000L / bpm).coerceIn(200L, 2000L)

        job = scope.launch(Dispatchers.Default) {
            try {
                audioTrack.play()
                while (isActive && isPlaying) {
                    audioTrack.write(clickBuffer, 0, clickBuffer.size, AudioTrack.WRITE_BLOCKING)
                    delay(intervalMs)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack.pause()
                    audioTrack.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopBeat() {
        isPlaying = false
        job?.cancel()
        job = null
        try {
            audioTrack.pause()
            audioTrack.flush()
        } catch (_: Exception) {}
    }

    fun release() {
        stopBeat()
        try {
            audioTrack.release()
        } catch (_: Exception) {}
    }
}
