package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrackingSession
import com.example.ui.AceAiViewModel
import com.example.ui.components.ChipType
import com.example.ui.components.EdgeDiagnosticSlot
import com.example.ui.components.PrimaryKineticButton
import com.example.ui.components.SecondaryHudButton
import com.example.ui.components.TelemetryPairRow
import com.example.ui.components.TelemetryStatusChip
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AnalyticsScreen(
    viewModel: AceAiViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val telemetryLogs by viewModel.telemetryLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResult by viewModel.searchBenchmarkResult.collectAsState()
    val isSearching by viewModel.isSearchingBenchmark.collectAsState()
    val peakVelocity by viewModel.peakVelocityKmH.collectAsState()
    val totalStrokes by viewModel.totalStrokes.collectAsState()
    val faultCount by viewModel.faultCount.collectAsState()

    var showNewSessionDialog by remember { mutableStateOf(false) }
    var sessionNotesInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Header Array ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ACEAI // CLOUD TELEMETRY",
                            color = OpticNeonLime,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.05.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TelemetryStatusChip(
                            text = "FIRESTORE SYNC",
                            type = ChipType.NOMINAL
                        )
                    }
                    Text(
                        text = "AGGREGATED KINEMATIC SESSIONS & BENCHMARKS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // --- 2. Macro Diagnostics Grid ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EdgeDiagnosticSlot(
                    title = "Peak Velocity",
                    token = "{{GLOBAL_MAX}}",
                    value = "${peakVelocity.toInt()}",
                    unit = "KM/H",
                    modifier = Modifier.weight(1f)
                )
                EdgeDiagnosticSlot(
                    title = "Cloud Sessions",
                    token = "{{SESSIONS_COUNT}}",
                    value = "${sessions.size}",
                    unit = "SYNCED",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 3. Google Search Grounding Section ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudContainer)
                    .border(1.dp, CyberCyan, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Grounding",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GOOGLE SEARCH GROUNDING // BWF TOURNAMENT DATA",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Query official world-record smash speeds and kinetic chain benchmarks with Gemini 3.8 Flash Search Grounding.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search player or stroke benchmark...", color = TextMuted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = OutlineMuted,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextSecondary,
                                focusedContainerColor = CarbonBlack,
                                unfocusedContainerColor = CarbonBlack
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_grounding_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SecondaryHudButton(
                            text = if (isSearching) "SEARCHING..." else "SEARCH",
                            onClick = { viewModel.executeSearchBenchmark() },
                            active = isSearching,
                            tag = "search_grounding_btn"
                        )
                    }

                    if (isSearching) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = CyberCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retrieving verified BWF World Tour telemetry...", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    searchResult?.let { result ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(CarbonBlack.copy(alpha = 0.7f))
                                .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = result,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Saved Cloud Sessions List ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVED CLOUD SESSIONS (${sessions.size})",
                    color = OpticNeonLime,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp
                )
                SecondaryHudButton(
                    text = "+ SYNC SESSION",
                    onClick = { viewModel.saveCurrentSession("Courtside high-performance tracking session.") },
                    tag = "quick_sync_btn"
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(HudContainer)
                        .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Empty",
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NO SESSIONS COMMITTED TO CLOUD YET",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ SYNC SESSION' or use HUD 'SYNC TO FIRESTORE' to commit telemetry.",
                            color = TextMuted.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SessionCardItem(session)
            }
        }

        // --- 5. High-Frequency Telemetry Log History ---
        item {
            Text(
                text = "STREAMED TELEMETRY BUFFER (${telemetryLogs.size} EVENTS)",
                color = CyberCyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.05.sp
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudContainer.copy(alpha = 0.85f))
                    .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Column {
                    if (telemetryLogs.isEmpty()) {
                        Text(
                            text = "Awaiting stroke events from live camera HUD...",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        telemetryLogs.take(8).forEach { log ->
                            TelemetryPairRow(
                                label = "${log.strokeType} // ${if (log.isFault) "FAULT: " + log.faultReason else "NOMINAL"}",
                                value = "${log.velocityKmH.toInt()} KM/H • ${log.elbowAngle}°",
                                isHighlight = !log.isFault
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SessionCardItem(session: TrackingSession) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(HudContainer)
            .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.sessionName.uppercase(),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                TelemetryStatusChip(
                    text = "${session.peakSmashVelocity.toInt()} KM/H",
                    type = ChipType.NOMINAL
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ATHLETE: ${session.athleteName} // ${session.sport}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "MODE: ${session.trackingMode}",
                    color = CyberCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AVG ELBOW: ${session.avgElbowAngle}°",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "STROKES: ${session.totalStrokes}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "FAULTS: ${session.faultCount}",
                    color = if (session.faultCount > 0) FaultCrimson else OpticNeonLime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            if (session.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = session.notes,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
