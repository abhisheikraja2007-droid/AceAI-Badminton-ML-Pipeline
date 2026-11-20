package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.coach.AiCoachScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.hud.CourtHudScreen

sealed class Screen(val route: String, val title: String) {
    object LiveHud : Screen("live_hud", "Live Biomechanics HUD")
    object Dashboard : Screen("dashboard", "Session Analytics")
    object CoachChat : Screen("coach_chat", "AceAI Biomechanics Coach")
}

@Composable
fun AceAiNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.LiveHud.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.LiveHud.route) {
            CourtHudScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route)
                },
                onNavigateToCoach = {
                    navController.navigate(Screen.CoachChat.route)
                }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCoach = {
                    navController.navigate(Screen.CoachChat.route)
                }
            )
        }
        composable(Screen.CoachChat.route) {
            AiCoachScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

