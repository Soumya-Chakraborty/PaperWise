package com.paperwise.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object PdfViewer : Screen("pdf_viewer/{filePath}?documentName={documentName}") {
        fun createRoute(filePath: String, documentName: String) =
            "pdf_viewer/$filePath?documentName=$documentName"
    }
    object Settings : Screen("settings")
}
