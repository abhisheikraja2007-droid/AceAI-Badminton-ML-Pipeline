package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.onGoogleSignInClicked
import com.example.ui.components.ChipType
import com.example.ui.components.PrimaryKineticButton
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
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { runCatching { CredentialManager.create(context) }.getOrNull() }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(HudContainer.copy(alpha = 0.95f))
                .border(1.dp, CyberCyan, RoundedCornerShape(6.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon & Header
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Security",
                tint = OpticNeonLime,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ACEAI // HUD ACCESS",
                color = OpticNeonLime,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.05.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "BIOMECHANICS TELEMETRY PORTAL",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(18.dp))

            TelemetryStatusChip(
                text = "ZERO-TRUST FIRESTORE & APP CHECK",
                type = ChipType.SPATIAL
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Authenticate with your Google Account to access real-time edge biomechanics tracking, Gemini 4 Argon reasoning, and cloud telemetry synchronization.",
                color = TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = OpticNeonLime,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Authenticating with Google Credential Manager...",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                PrimaryKineticButton(
                    text = "SIGN IN WITH GOOGLE",
                    onClick = {
                        if (credentialManager == null) {
                            errorMessage = "Google Play Services Credential Manager is initializing or unavailable. You can start session as Guest."
                            return@PrimaryKineticButton
                        }
                        isLoading = true
                        errorMessage = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { err ->
                                isLoading = false
                                errorMessage = err
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tag = "google_sign_in_button"
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrimaryKineticButton(
                    text = "START SESSION AS GUEST",
                    onClick = {
                        isLoading = true
                        com.example.auth.signInAnonymously(
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = {
                                isLoading = false
                                onContinueAsGuest()
                            },
                            scope = coroutineScope
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tag = "guest_mode_button"
                )
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Auth Notice: $err",
                    color = FaultCrimson,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
