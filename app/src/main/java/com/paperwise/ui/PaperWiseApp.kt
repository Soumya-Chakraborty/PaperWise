package com.paperwise.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paperwise.ui.home.HomeScreen
import com.paperwise.ui.navigation.NavigationCodec
import com.paperwise.ui.navigation.Screen
import com.paperwise.ui.settings.SettingsScreen
import com.paperwise.ui.viewer.PdfViewerScreen

/**
 * Root composable for PaperWise app with navigation.
 */
@Composable
fun PaperWiseApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPdf = { filePath ->
                    val encodedPath = NavigationCodec.encodeFilePath(filePath)
                    navController.navigate(Screen.PdfViewer.createRoute(encodedPath))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
            val filePath = NavigationCodec.decodeFilePath(encodedPath)
            
            PdfViewerScreen(
                filePath = filePath,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
