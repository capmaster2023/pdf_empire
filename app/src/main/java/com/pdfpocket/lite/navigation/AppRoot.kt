package com.pdfpocket.lite.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pdfpocket.lite.R
import com.pdfpocket.lite.features.files.FilesScreen
import com.pdfpocket.lite.features.fillsign.FillSignScreen
import com.pdfpocket.lite.features.home.HomeScreen
import com.pdfpocket.lite.features.pages.PagesScreen
import com.pdfpocket.lite.features.settings.SettingsScreen
import com.pdfpocket.lite.features.tools.ConvertScreen
import com.pdfpocket.lite.features.tools.ImagesToPdfScreen
import com.pdfpocket.lite.features.tools.MergeScreen
import com.pdfpocket.lite.features.tools.WatermarkScreen
import com.pdfpocket.lite.features.tools.SplitScreen
import com.pdfpocket.lite.features.tools.ToolsScreen
import com.pdfpocket.lite.features.viewer.ViewerScreen

private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun AppRoot(
    pendingPdfUri: String?,
    onPendingConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(pendingPdfUri) {
        if (pendingPdfUri != null) {
            navController.navigate(Routes.viewer(pendingPdfUri))
            onPendingConsumed()
        }
    }

    val tabs = listOf(
        BottomTab(Routes.HOME, R.string.tab_home, Icons.Default.Home),
        BottomTab(Routes.FILES, R.string.tab_files, Icons.Default.Folder),
        BottomTab(Routes.TOOLS, R.string.tab_tools, Icons.Default.Build),
        BottomTab(Routes.SETTINGS, R.string.tab_settings, Icons.Default.Settings)
    )

    val showBottomBar = currentRoute != null &&
        !currentRoute.startsWith("viewer") &&
        !currentRoute.startsWith(Routes.FILL_SIGN)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenViewer = { uri -> navController.navigate(Routes.viewer(uri)) },
                    onOpenTool = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.FILES) {
                FilesScreen(
                    onOpenViewer = { uri -> navController.navigate(Routes.viewer(uri)) }
                )
            }
            composable(Routes.TOOLS) {
                ToolsScreen(onOpenTool = { route -> navController.navigate(route) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.MERGE) {
                MergeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SPLIT) {
                SplitScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PAGES) {
                PagesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CONVERT) {
                ConvertScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.WATERMARK) {
                WatermarkScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.IMAGES_TO_PDF) {
                ImagesToPdfScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.FILL_SIGN_PATTERN,
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                FillSignScreen(
                    initialUri = entry.arguments?.getString("uri"),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.VIEWER) { entry ->
                val uriString = entry.arguments?.getString("uri").orEmpty()
                ViewerScreen(
                    uriString = uriString,
                    onBack = { navController.popBackStack() },
                    onFillSign = { navController.navigate(Routes.fillSign(uriString)) }
                )
            }
        }
    }
}
