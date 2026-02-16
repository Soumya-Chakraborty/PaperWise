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
                onNavigateToPdf = { filePath, documentName ->
                    val encodedPath = NavigationCodec.encodeFilePath(filePath)
                    val encodedName = NavigationCodec.encodeValue(documentName ?: "")
                    navController.navigate(Screen.PdfViewer.createRoute(encodedPath, encodedName))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType },
                navArgument("documentName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
            val encodedName = backStackEntry.arguments?.getString("documentName") ?: ""
            val filePath = NavigationCodec.decodeFilePath(encodedPath)
            val documentName = NavigationCodec.decodeValue(encodedName).ifBlank { null }
            
            PdfViewerScreen(
                filePath = filePath,
                initialDocumentName = documentName,
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
