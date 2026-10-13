package app.photoxpo.nav

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.photoxpo.history.HistoryRoute
import app.photoxpo.onboarding.OnboardingRoute
import app.photoxpo.photos.PhotoRoute
import app.photoxpo.photos.PhotosRoute
import app.photoxpo.photos.PhotosRepository
import app.photoxpo.photos.PreviewBitmapCache

@Composable
fun PhotoxpoScaffold(
    repository: PhotosRepository,
    startOnboarding: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val previewCache = remember { PreviewBitmapCache() }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val onboardingVisible = route == AppRoutes.Onboarding || (route == null && startOnboarding)
    val showBottomBar = !onboardingVisible && (
        route == null || route == AppRoutes.Home || route == AppRoutes.Photos
        )

    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_WINDOW_DP
    val showRail = showBottomBar && wide

    Row(modifier = modifier.fillMaxSize()) {
        if (showRail) {
            PhotoxpoRail(
                currentRoute = route ?: AppRoutes.Home,
                onHome = { navController.openTab(AppRoutes.Home) },
                onPhotos = { navController.openTab(AppRoutes.Photos) },
            )
        }
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentWindowInsets = if (showBottomBar || onboardingVisible) {
                ScaffoldDefaults.contentWindowInsets
            } else {
                WindowInsets(0, 0, 0, 0)
            },
            bottomBar = {
                if (showBottomBar && !showRail) {
                    PhotoxpoBottomBar(
                        currentRoute = route ?: AppRoutes.Home,
                        onHome = { navController.openTab(AppRoutes.Home) },
                        onPhotos = { navController.openTab(AppRoutes.Photos) },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (startOnboarding) AppRoutes.Onboarding else AppRoutes.Home,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(AppRoutes.Onboarding) {
                    OnboardingRoute(
                        onFinished = {
                            navController.navigate(AppRoutes.Home) {
                                popUpTo(AppRoutes.Onboarding) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                composable(AppRoutes.Home) {
                    HomeRoute(
                        onOpenHistory = {
                            navController.navigate(AppRoutes.History) {
                                launchSingleTop = true
                            }
                        },
                        onOpenPhotos = { navController.openTab(AppRoutes.Photos) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                composable(AppRoutes.History) {
                    HistoryRoute(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                composable(AppRoutes.Photos) {
                    PhotosRoute(
                        repository = repository,
                        previewCache = previewCache,
                        contentPadding = innerPadding,
                        onOpenPhoto = { hash ->
                            navController.navigate(AppRoutes.photo(hash))
                        },
                    )
                }
                composable(
                    route = AppRoutes.Photo,
                    arguments = listOf(
                        navArgument("hash") { type = NavType.StringType },
                    ),
                ) { entry ->
                    PhotoRoute(
                        hash = entry.arguments?.getString("hash").orEmpty(),
                        repository = repository,
                        previewCache = previewCache,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private const val WIDE_WINDOW_DP = 600

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(AppRoutes.Home) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
