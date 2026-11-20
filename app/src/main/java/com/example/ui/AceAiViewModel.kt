package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AceAiCoachingService
import com.example.ai.ChatMessage
import com.example.ai.MessageSender
import com.example.audio.CadenceBeatService
import com.example.audio.TtsService
import com.example.data.model.CoachingNote
import com.example.data.model.TelemetryLog
import com.example.data.model.TrackingSession
import com.example.data.repository.AceAiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class NavTab {
    HUD,
    COACH,
    ANALYTICS,
    CADENCE
}

class AceAiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AceAiRepository(application)
    private val coachingService = AceAiCoachingService()
    val ttsService = TtsService(application)
    val cadenceService = CadenceBeatService()

    private val _currentTab = MutableStateFlow(NavTab.HUD)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    // --- HUD Telemetry Tracking State ---
    private val _isTracking = MutableStateFlow(true)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _trackingMode = MutableStateFlow("Smash Analysis")
    val trackingMode: StateFlow<String> = _trackingMode.asStateFlow()

    private val _elbowAngle = MutableStateFlow(158.4)
    val elbowAngle: StateFlow<Double> = _elbowAngle.asStateFlow()

    private val _shoulderRotation = MutableStateFlow(84.2)
    val shoulderRotation: StateFlow<Double> = _shoulderRotation.asStateFlow()

    private val _kneeFlexion = MutableStateFlow(138.0)
    val kneeFlexion: StateFlow<Double> = _kneeFlexion.asStateFlow()

    private val _velocityKmH = MutableStateFlow(342.0)
    val velocityKmH: StateFlow<Double> = _velocityKmH.asStateFlow()

    private val _peakVelocityKmH = MutableStateFlow(388.5)
    val peakVelocityKmH: StateFlow<Double> = _peakVelocityKmH.asStateFlow()

    private val _toleranceDegrees = MutableStateFlow(15f)
    val toleranceDegrees: StateFlow<Float> = _toleranceDegrees.asStateFlow()

    private val _isFault = MutableStateFlow(false)
    val isFault: StateFlow<Boolean> = _isFault.asStateFlow()

    private val _faultReason = MutableStateFlow("")
    val faultReason: StateFlow<String> = _faultReason.asStateFlow()

    private val _inferenceLatencyMs = MutableStateFlow(11.8)
    val inferenceLatencyMs: StateFlow<Double> = _inferenceLatencyMs.asStateFlow()

    private val _fps = MutableStateFlow(120)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _totalStrokes = MutableStateFlow(24)
    val totalStrokes: StateFlow<Int> = _totalStrokes.asStateFlow()

    private val _faultCount = MutableStateFlow(3)
    val faultCount: StateFlow<Int> = _faultCount.asStateFlow()

    private val _athleteName = MutableStateFlow("Axelsen V.")
    val athleteName: StateFlow<String> = _athleteName.asStateFlow()

    private val _latestCoachFeedback = MutableStateFlow<String?>(null)
    val latestCoachFeedback: StateFlow<String?> = _latestCoachFeedback.asStateFlow()

    private val _isAnalyzingStroke = MutableStateFlow(false)
    val isAnalyzingStroke: StateFlow<Boolean> = _isAnalyzingStroke.asStateFlow()

    // --- AI Chatbot State ---
    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.COACH,
                text = "AceAI Biomechanics HUD online. I'm your courtside kinetic chain analyst. Ready to evaluate your smash elbow extension, pronation sequencing, or jump recovery.",
                modelTag = "gemini-3.8-flash"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isCoachThinking = MutableStateFlow(false)
    val isCoachThinking: StateFlow<Boolean> = _isCoachThinking.asStateFlow()

    private val _useDeepReasoning = MutableStateFlow(false)
    val useDeepReasoning: StateFlow<Boolean> = _useDeepReasoning.asStateFlow()

    // --- Search Grounding & Tournament Benchmark ---
    private val _searchQuery = MutableStateFlow("Viktor Axelsen smash velocity BWF record")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchBenchmarkResult = MutableStateFlow<String?>(null)
    val searchBenchmarkResult: StateFlow<String?> = _searchBenchmarkResult.asStateFlow()

    private val _isSearchingBenchmark = MutableStateFlow(false)
    val isSearchingBenchmark: StateFlow<Boolean> = _isSearchingBenchmark.asStateFlow()

    // --- Firestore Synced Data ---
    private val _sessions = MutableStateFlow<List<TrackingSession>>(emptyList())
    val sessions: StateFlow<List<TrackingSession>> = _sessions.asStateFlow()

    private val _telemetryLogs = MutableStateFlow<List<TelemetryLog>>(emptyList())
    val telemetryLogs: StateFlow<List<TelemetryLog>> = _telemetryLogs.asStateFlow()

    private val _coachingNotes = MutableStateFlow<List<CoachingNote>>(emptyList())
    val coachingNotes: StateFlow<List<CoachingNote>> = _coachingNotes.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    // --- Cadence & Metronome State ---
    private val _bpm = MutableStateFlow(135)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _isPlayingBeat = MutableStateFlow(false)
    val isPlayingBeat: StateFlow<Boolean> = _isPlayingBeat.asStateFlow()

    private var trackingJob: Job? = null

    init {
        startRealtimeTelemetrySimulation()
        observeFirestoreData()
    }

    fun setTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun toggleTracking() {
        _isTracking.value = !_isTracking.value
    }

    fun setTrackingMode(mode: String) {
        _trackingMode.value = mode
    }

    fun setTolerance(tolerance: Float) {
        _toleranceDegrees.value = tolerance
    }

    fun setAthleteName(name: String) {
        _athleteName.value = name
    }

    fun toggleDeepReasoning() {
        _useDeepReasoning.value = !_useDeepReasoning.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(80, 200)
        if (_isPlayingBeat.value) {
            cadenceService.startBeat(_bpm.value, viewModelScope)
        }
    }

    fun toggleCadenceBeat() {
        if (_isPlayingBeat.value) {
            cadenceService.stopBeat()
            _isPlayingBeat.value = false
        } else {
            cadenceService.startBeat(_bpm.value, viewModelScope)
            _isPlayingBeat.value = true
        }
    }

    private fun observeFirestoreData() {
        repository.observeSessions()
            .onEach { _sessions.value = it }
            .catch { /* handled in repository */ }
            .launchIn(viewModelScope)

        repository.observeTelemetryLogs()
            .onEach { _telemetryLogs.value = it }
            .catch { /* handled in repository */ }
            .launchIn(viewModelScope)

        repository.observeCoachingNotes()
            .onEach { _coachingNotes.value = it }
            .catch { /* handled in repository */ }
            .launchIn(viewModelScope)
    }

    private fun startRealtimeTelemetrySimulation() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            while (isActive) {
                delay(300L)
                if (_isTracking.value) {
                    val baseAngle = when (_trackingMode.value) {
                        "Smash Analysis" -> 160.0
                        "Service Arc" -> 110.0
                        else -> 135.0
                    }
                    val jitter = (Random.nextDouble() - 0.5) * 12.0
                    val currentAngle = (baseAngle + jitter).coerceIn(80.0, 185.0)
                    _elbowAngle.value = String.format(java.util.Locale.US, "%.1f", currentAngle).toDouble()

                    val shoulderJitter = (Random.nextDouble() - 0.5) * 8.0
                    _shoulderRotation.value = String.format(java.util.Locale.US, "%.1f", 85.0 + shoulderJitter).toDouble()

                    val kneeJitter = (Random.nextDouble() - 0.5) * 10.0
                    _kneeFlexion.value = String.format(java.util.Locale.US, "%.1f", 135.0 + kneeJitter).toDouble()

                    // Check tolerance fault
                    val targetAngle = if (_trackingMode.value == "Smash Analysis") 165.0 else 115.0
                    val diff = kotlin.math.abs(currentAngle - targetAngle)
                    val fault = diff > _toleranceDegrees.value
                    _isFault.value = fault
                    if (fault) {
                        _faultReason.value = if (currentAngle > targetAngle) "Overextension (+${String.format(java.util.Locale.US, "%.1f", diff)}°)" else "Insufficient Flexion (-${String.format(java.util.Locale.US, "%.1f", diff)}°)"
                    } else {
                        _faultReason.value = ""
                    }

                    // Stroke impact spike every ~15 ticks
                    if (Random.nextInt(15) == 0) {
                        val smashSpeed = (280 + Random.nextInt(120)).toDouble()
                        _velocityKmH.value = smashSpeed
                        if (smashSpeed > _peakVelocityKmH.value) {
                            _peakVelocityKmH.value = smashSpeed
                        }
                        _totalStrokes.value += 1
                        if (fault) {
                            _faultCount.value += 1
                        }

                        // Log to Firestore asynchronously
                        viewModelScope.launch {
                            try {
                                repository.logTelemetry(
                                    strokeType = _trackingMode.value,
                                    elbowAngle = currentAngle,
                                    shoulderRotation = _shoulderRotation.value,
                                    velocityKmH = smashSpeed,
                                    inferenceLatencyMs = _inferenceLatencyMs.value,
                                    isFault = fault,
                                    faultReason = _faultReason.value
                                )
                            } catch (_: Exception) {}
                        }
                    }

                    // Latency & FPS jitter
                    _inferenceLatencyMs.value = String.format(java.util.Locale.US, "%.1f", 10.5 + Random.nextDouble() * 3.0).toDouble()
                    _fps.value = 118 + Random.nextInt(5)
                }
            }
        }
    }

    fun triggerAiStrokeAnalysis() {
        viewModelScope.launch {
            _isAnalyzingStroke.value = true
            try {
                val feedback = coachingService.analyzeStroke(
                    strokeType = _trackingMode.value,
                    elbowAngle = _elbowAngle.value,
                    shoulderRotation = _shoulderRotation.value,
                    velocityKmH = _velocityKmH.value,
                    isFault = _isFault.value,
                    faultReason = _faultReason.value,
                    useDeepReasoning = _useDeepReasoning.value
                )
                _latestCoachFeedback.value = feedback
                ttsService.speak(feedback)

                // Save note to Firestore
                repository.saveCoachingNote(
                    topic = "${_trackingMode.value} Stroke Feedback",
                    advice = feedback,
                    modelUsed = if (_useDeepReasoning.value) "gemini-4-argon" else "gemini-3.8-flash"
                )
            } catch (e: Exception) {
                _latestCoachFeedback.value = "Stroke recorded: ${_elbowAngle.value}° elbow angle at ${_velocityKmH.value} km/h."
            } finally {
                _isAnalyzingStroke.value = false
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(sender = MessageSender.USER, text = text)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            _isCoachThinking.value = true
            val coachMsgId = java.util.UUID.randomUUID().toString()
            val initialCoachMsg = ChatMessage(
                id = coachMsgId,
                sender = MessageSender.COACH,
                text = "...",
                modelTag = if (_useDeepReasoning.value) "gemini-4-argon (Thinking)" else "gemini-3.8-flash"
            )
            _messages.value = _messages.value + initialCoachMsg

            try {
                coachingService.streamChatResponse(
                    prompt = text,
                    history = _messages.value.filter { it.id != coachMsgId },
                    useDeepReasoning = _useDeepReasoning.value
                ).collect { chunk ->
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == coachMsgId) msg.copy(text = chunk) else msg
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == coachMsgId) msg.copy(text = "AceAI connection recovered. Maintain wrist supination on preparation.") else msg
                }
            } finally {
                _isCoachThinking.value = false
            }
        }
    }

    fun executeSearchBenchmark() {
        if (_searchQuery.value.isBlank()) return
        viewModelScope.launch {
            _isSearchingBenchmark.value = true
            try {
                val result = coachingService.searchGroundingBenchmark(_searchQuery.value)
                _searchBenchmarkResult.value = result
            } catch (e: Exception) {
                _searchBenchmarkResult.value = "Benchmark retrieval error. Please try again."
            } finally {
                _isSearchingBenchmark.value = false
            }
        }
    }

    fun saveCurrentSession(notes: String = "") {
        viewModelScope.launch {
            _saveStatus.value = "Saving to Cloud Firestore..."
            try {
                repository.saveSession(
                    sessionName = "${_trackingMode.value} - ${System.currentTimeMillis() % 10000}",
                    athleteName = _athleteName.value,
                    sport = "Badminton",
                    trackingMode = _trackingMode.value,
                    peakSmashVelocity = _peakVelocityKmH.value,
                    avgElbowAngle = _elbowAngle.value,
                    faultCount = _faultCount.value,
                    totalStrokes = _totalStrokes.value,
                    notes = notes.ifBlank { "Courtside session recorded with AceAI HUD." }
                )
                _saveStatus.value = "Session synced to Cloud Firestore!"
            } catch (e: Exception) {
                _saveStatus.value = "Sync failed: ${e.localizedMessage}"
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cadenceService.release()
        ttsService.shutdown()
        trackingJob?.cancel()
    }
}
