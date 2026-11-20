package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOutUser
import com.example.ui.AceAiViewModel
import com.example.ui.NavTab
import com.example.ui.coach.AiCoachScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.hud.CourtHudScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CadenceScreen
import com.example.ui.screens.CoachChatScreen
import com.example.ui.screens.LiveHudScreen
import com.example.ui.theme.AceAiTheme
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Mandatory App Check configuration with launch intent before any UI or Firebase ops
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            AceAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CarbonBlack
                ) {
                    AceAiMainContainer()
                }
            }
        }
    }
}

@Composable
fun AceAiMainContainer() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { runCatching { CredentialManager.create(context) }.getOrNull() }

    var currentUser by remember {
        mutableStateOf(runCatching { Firebase.auth.currentUser }.getOrNull())
    }
    var isGuestMode by remember { mutableStateOf(false) }

    // Listen to Firebase auth state changes safely
    DisposableEffect(Unit) {
        val auth = runCatching { Firebase.auth }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { state ->
            currentUser = state.currentUser
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
        }
    }

    // Attempt silent auto-sign in on startup safely
    LaunchedEffect(Unit) {
        if (!isGuestMode && currentUser == null && credentialManager != null) {
            runCatching {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = { currentUser = runCatching { Firebase.auth.currentUser }.getOrNull() },
                    onUnauthenticated = { /* stay on Auth screen */ },
                    scope = coroutineScope
                )
            }
        }
    }

    if (!isGuestMode && currentUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = runCatching { Firebase.auth.currentUser }.getOrNull() },
            onContinueAsGuest = { isGuestMode = true }
        )
    } else {
        AceAiAppContent(
            userEmail = currentUser?.email ?: "guest_athlete@aceai.hud",
            onSignOut = {
                isGuestMode = false
                if (credentialManager != null) {
                    signOutUser(
                        context = context,
                        credentialManager = credentialManager,
                        onSignOutComplete = { currentUser = null },
                        scope = coroutineScope
                    )
                } else {
                    runCatching { Firebase.auth.signOut() }
                    currentUser = null
                }
            }
        )
    }
}

@Composable
fun AceAiAppContent(
    userEmail: String,
    onSignOut: () -> Unit,
    viewModel: AceAiViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack),
        topBar = {
            HudTopBar(
                userEmail = userEmail,
                onSignOut = onSignOut
            )
        },
        bottomBar = {
            HudBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        },
        containerColor = CarbonBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavTab.HUD -> CourtHudScreen(
                    onNavigateToDashboard = { viewModel.setTab(NavTab.ANALYTICS) },
                    onNavigateToCoach = { viewModel.setTab(NavTab.COACH) }
                )
                NavTab.COACH -> AiCoachScreen(
                    onNavigateBack = { viewModel.setTab(NavTab.HUD) }
                )
                NavTab.ANALYTICS -> DashboardScreen(
                    onNavigateBack = { viewModel.setTab(NavTab.HUD) },
                    onNavigateToCoach = { viewModel.setTab(NavTab.COACH) }
                )
                NavTab.CADENCE -> CadenceScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HudTopBar(
    userEmail: String,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(HudContainer.copy(alpha = 0.95f))
            .border(width = 1.dp, color = OutlineMuted)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(OpticNeonLime)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ACEAI // BIOMECHANICS HUD",
                        color = OpticNeonLime,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp
                    )
                    Text(
                        text = userEmail,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HudBottomNavigation(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = HudContainer,
        contentColor = TextSecondary
    ) {
        NavigationBarItem(
            selected = currentTab == NavTab.HUD,
            onClick = { onTabSelected(NavTab.HUD) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Live HUD",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "HUD",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBlack,
                selectedTextColor = OpticNeonLime,
                indicatorColor = OpticNeonLime,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("tab_hud")
        )

        NavigationBarItem(
            selected = currentTab == NavTab.COACH,
            onClick = { onTabSelected(NavTab.COACH) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "AI Coach",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "COACH",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBlack,
                selectedTextColor = OpticNeonLime,
                indicatorColor = OpticNeonLime,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("tab_coach")
        )

        NavigationBarItem(
            selected = currentTab == NavTab.ANALYTICS,
            onClick = { onTabSelected(NavTab.ANALYTICS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Telemetry",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "CLOUD",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBlack,
                selectedTextColor = OpticNeonLime,
                indicatorColor = OpticNeonLime,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("tab_analytics")
        )

        NavigationBarItem(
            selected = currentTab == NavTab.CADENCE,
            onClick = { onTabSelected(NavTab.CADENCE) },
            icon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Cadence Beats",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "CADENCE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBlack,
                selectedTextColor = OpticNeonLime,
                indicatorColor = OpticNeonLime,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("tab_cadence")
        )
    }
}
