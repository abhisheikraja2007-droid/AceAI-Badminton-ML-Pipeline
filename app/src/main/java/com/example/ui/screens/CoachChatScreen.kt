package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ai.ChatMessage
import com.example.ai.MessageSender
import com.example.ui.AceAiViewModel
import com.example.ui.components.ChipType
import com.example.ui.components.SecondaryHudButton
import com.example.ui.components.TelemetryStatusChip
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CoachChatScreen(
    viewModel: AceAiViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isCoachThinking.collectAsState()
    val useDeepReasoning by viewModel.useDeepReasoning.collectAsState()
    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "Explain the proximal-to-distal kinetic chain",
        "How to prevent elbow hyperextension during jump smash?",
        "What is the ideal shoulder rotation angle for power?",
        "Compare smash mechanics with Viktor Axelsen"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .padding(16.dp)
    ) {
        // --- 1. Screen Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACEAI // COACH",
                        color = OpticNeonLime,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TelemetryStatusChip(
                        text = if (useDeepReasoning) "GEMINI-4 HIGH" else "GEMINI-3.8 FLASH",
                        type = if (useDeepReasoning) ChipType.SPATIAL else ChipType.NOMINAL
                    )
                }
                Text(
                    text = "BIOMECHANICS INTEL & STROKE AUDIT",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // High Thinking Mode Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudContainer)
                    .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Thinking Mode",
                    tint = if (useDeepReasoning) CyberCyan else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "THINKING",
                    color = if (useDeepReasoning) CyberCyan else TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = useDeepReasoning,
                    onCheckedChange = { viewModel.toggleDeepReasoning() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OpticNeonLime,
                        checkedTrackColor = CyberCyan.copy(alpha = 0.5f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = OutlineMuted
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Quick Prompt Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { prompt ->
                SecondaryHudButton(
                    text = prompt,
                    onClick = { viewModel.sendChatMessage(prompt) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Chat Message Thread ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    onSpeak = { viewModel.ttsService.speak(msg.text) }
                )
            }

            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = CyberCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (useDeepReasoning) "AceAI Chief Scientist reasoning with Gemini 4 Argon..." else "AceAI Coach analyzing telemetry...",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 4. Input Row & Voice Transcription Input ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = {
                    Text(
                        "Ask AceAI Coach or dictate courtside query...",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = OutlineMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextSecondary,
                    cursorColor = OpticNeonLime,
                    focusedContainerColor = HudContainer,
                    unfocusedContainerColor = HudContainer
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_input_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Voice input / transcription simulator button
            IconButton(
                onClick = {
                    inputQuery = "How do I optimize pronation timing at contact?"
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudContainer)
                    .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                    .testTag("voice_input_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            IconButton(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        viewModel.sendChatMessage(inputQuery)
                        inputQuery = ""
                    }
                },
                enabled = inputQuery.isNotBlank() && !isThinking,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (inputQuery.isNotBlank()) OpticNeonLime else OutlineMuted)
                    .testTag("coach_send_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = CarbonBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) HudContainer.copy(alpha = 0.95f) else HudContainer
    val borderColor = if (isUser) OpticNeonLime else CyberCyan

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Sender Label
        Text(
            text = if (isUser) "ATHLETE" else "ACEAI COACH // ${message.modelTag.ifBlank { "gemini-3.8-flash" }}",
            color = if (isUser) OpticNeonLime else CyberCyan,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.95f)
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 19.sp
                )

                if (!isUser && message.text.isNotBlank() && message.text != "...") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "TTS Vocalize",
                                tint = OpticNeonLime,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
