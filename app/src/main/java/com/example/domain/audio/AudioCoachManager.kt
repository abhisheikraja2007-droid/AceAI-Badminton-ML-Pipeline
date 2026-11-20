package com.example.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.domain.model.SmashEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Thread-safe audio coaching manager wrapping Android's [TextToSpeech] engine
 * and [AudioFocusRequest] (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).
 *
 * Configured for ultra-low latency real-time court telemetry and post-drill AI summaries.
 */
@Singleton
class AudioCoachManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AudioCoachManager"
        private const val UTTERANCE_SMASH_CALLOUT = "utterance_smash_callout"
        private const val UTTERANCE_COACH_SUMMARY = "utterance_coach_summary"

        // Low-latency synthesis tuning parameters
        private const val SPEECH_RATE = 1.15f
        private const val SPEECH_PITCH = 1.0f
        private const val CRITICAL_ELBOW_ANGLE_THRESHOLD_DEG = 155.0
    }

    private val lock = Any()
    private var tts: TextToSpeech? = null
    private val isTtsInitialized = AtomicBoolean(false)
    private var hasAudioFocus = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioFocusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { focusChange ->
                synchronized(lock) {
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            hasAudioFocus = false
                            tts?.stop()
                            _isSpeaking.value = false
                        }
                    }
                }
            }
            .build()
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var summaryCompletionCallback: (() -> Unit)? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        synchronized(lock) {
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        synchronized(lock) {
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val result = engine.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Locale.US not supported, falling back to default locale.")
                        engine.language = Locale.getDefault()
                    }

                    engine.setSpeechRate(SPEECH_RATE)
                    engine.setPitch(SPEECH_PITCH)
                    engine.setAudioAttributes(audioAttributes)

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            releaseAudioFocus()
                            if (utteranceId == UTTERANCE_COACH_SUMMARY) {
                                summaryCompletionCallback?.invoke()
                                summaryCompletionCallback = null
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            releaseAudioFocus()
                            summaryCompletionCallback = null
                            Log.e(TAG, "TTS Utterance error on ID: $utteranceId")
                        }
                    })

                    isTtsInitialized.set(true)
                    Log.i(TAG, "TextToSpeech successfully initialized with rate=$SPEECH_RATE, pitch=$SPEECH_PITCH")
                }
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech engine. Status: $status")
            }
        }
    }

    /**
     * Mode 1: Immediate Smash Callout.
     * Triggered instantly by a [SmashEvent] using QUEUE_FLUSH so stale callouts are dropped.
     * Speaks concise telemetry and appends a rapid corrective cue if elbow extension is below 155°.
     */
    fun speakSmashCallout(event: SmashEvent) {
        if (!isTtsInitialized.get()) return

        val speedKmh = event.peakVelocityKmh.roundToInt()
        val speechText = if (event.impactElbowAngleDeg < CRITICAL_ELBOW_ANGLE_THRESHOLD_DEG) {
            "$speedKmh kilometers per hour. Extend elbow!"
        } else {
            "$speedKmh kilometers per hour!"
        }

        speakInternal(
            text = speechText,
            queueMode = TextToSpeech.QUEUE_FLUSH,
            utteranceId = UTTERANCE_SMASH_CALLOUT
        )
    }

    /**
     * Mode 2: AI Coach Summary Playback.
     * Reads out the structured voice_coach_summary from Gemini AI.
     */
    fun playCoachSummary(summaryText: String, onComplete: (() -> Unit)? = null) {
        if (!isTtsInitialized.get() || summaryText.isBlank()) return

        synchronized(lock) {
            summaryCompletionCallback = onComplete
        }

        speakInternal(
            text = summaryText,
            queueMode = TextToSpeech.QUEUE_FLUSH,
            utteranceId = UTTERANCE_COACH_SUMMARY
        )
    }

    private fun speakInternal(text: String, queueMode: Int, utteranceId: String) {
        synchronized(lock) {
            val engine = tts ?: return
            if (!isTtsInitialized.get()) return

            requestAudioFocus()

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            engine.speak(text, queueMode, params, utteranceId)
        }
    }

    private fun requestAudioFocus() {
        if (!hasAudioFocus && audioManager != null) {
            val result = audioManager.requestAudioFocus(audioFocusRequest)
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }

    private fun releaseAudioFocus() {
        synchronized(lock) {
            if (hasAudioFocus && audioManager != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
                hasAudioFocus = false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            tts?.stop()
            _isSpeaking.value = false
            releaseAudioFocus()
            summaryCompletionCallback = null
        }
    }

    /**
     * Proper lifecycle shutdown to prevent memory leaks and release system audio resources.
     */
    fun shutdown() {
        synchronized(lock) {
            stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized.set(false)
            Log.i(TAG, "AudioCoachManager cleanly shut down.")
        }
    }
}

