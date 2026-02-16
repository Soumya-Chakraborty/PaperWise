package com.paperwise.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object PdfViewer : Screen("pdf_viewer/{filePath}") {
        fun createRoute(filePath: String) = "pdf_viewer/$filePath"
    }
    object Settings : Screen("settings")
}
