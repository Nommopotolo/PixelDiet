package com.example.pixeldiet.ui.navigation

import com.example.pixeldiet.ui.main.AppSelectionScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pixeldiet.ui.calendar.CalendarScreen
import com.example.pixeldiet.ui.main.MainScreen
import com.example.pixeldiet.ui.settings.SettingsScreen
import com.example.pixeldiet.viewmodel.SharedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(sharedViewModel: SharedViewModel = viewModel()) {
    val navController = rememberNavController()

    val items = listOf(
        BottomNavItem.Main,
        BottomNavItem.Calendar,
        BottomNavItem.Settings,
    )
    val welcomeText by sharedViewModel.userName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Pixel 대신 로그인 상태 문구만 표시
                    Text(
                        text = welcomeText,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // ⭐️ NavHost가 Fragment의 역할을 대체
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Main.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Main.route) {
                MainScreen(
                    viewModel = sharedViewModel,          // ⭐ 같은 ViewModel 사용
                    onAppSelectionClick = {
                        navController.navigate("app_selection")
                    }
                )
            }
            composable(BottomNavItem.Calendar.route) {
                CalendarScreen(
                    viewModel = sharedViewModel
                )
            }
            composable(BottomNavItem.Settings.route) {
                SettingsScreen()
            }

            // 👇 앱 선택 화면 라우트 추가 (바텀탭에는 안 보이는 서브 화면)
            composable("app_selection") {
                AppSelectionScreen(
                    viewModel = sharedViewModel,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}