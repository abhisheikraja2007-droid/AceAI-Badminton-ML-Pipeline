package com.example.domain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.GeminiCoachService
import com.example.domain.audio.AudioCoachManager
import com.example.domain.model.GeminiCoachAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoachChatViewModel @Inject constructor(
    private val geminiCoachService: GeminiCoachService,
    private val audioCoachManager: AudioCoachManager
) : ViewModel() {

    val analysisState: StateFlow<GeminiCoachAnalysis?> = geminiCoachService.currentAnalysis
    val isAnalyzing: StateFlow<Boolean> = geminiCoachService.isGenerating
    val isSpeaking: StateFlow<Boolean> = audioCoachManager.isSpeaking

    fun loadDiagnosticsForSession(sessionId: String = "active_court_session") {
        viewModelScope.launch {
            geminiCoachService.generateSessionDiagnostics(sessionId)
        }
    }

    fun playVoiceSummary() {
        val summary = analysisState.value?.voiceCoachSummary ?: return
        audioCoachManager.playCoachSummary(summary)
    }

    fun stopVoiceSummary() {
        audioCoachManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        audioCoachManager.stop()
    }
}
