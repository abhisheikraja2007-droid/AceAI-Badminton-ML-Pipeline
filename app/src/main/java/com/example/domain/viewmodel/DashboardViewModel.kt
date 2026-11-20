package com.example.domain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.SessionSummary
import com.example.domain.usecase.AnalyzeSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyzeSessionUseCase: AnalyzeSessionUseCase
) : ViewModel() {

    private val _sessionSummary = MutableStateFlow<SessionSummary?>(null)
    val sessionSummary: StateFlow<SessionSummary?> = _sessionSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadSessionAnalytics(sessionId: String, durationSec: Int = 1800) {
        viewModelScope.launch {
            _isLoading.value = true
            val summary = analyzeSessionUseCase.execute(sessionId, durationSec)
            _sessionSummary.value = summary
            _isLoading.value = false
        }
    }
}

