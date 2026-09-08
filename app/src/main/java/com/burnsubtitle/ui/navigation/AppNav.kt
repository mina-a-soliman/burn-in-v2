package com.burnsubtitle.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.burnsubtitle.ui.encode.EncodeScreen
import com.burnsubtitle.ui.home.HomeScreen
import com.burnsubtitle.ui.style.StyleScreen

object AppRoutes {
    const val HOME = "home"
    const val STYLE = "style"
    const val ENCODE = "encode"
}

@Composable
fun BurnSubtitleNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(AppRoutes.STYLE) },
                onExport = {
                    navController.navigate(AppRoutes.ENCODE) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoutes.STYLE) {
            StyleScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.ENCODE) {
            EncodeScreen(
                onHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
