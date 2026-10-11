package app.photoxpo.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.photoxpo.photos.PhotoRoute
import app.photoxpo.photos.PhotosRoute
import app.photoxpo.photos.PhotosRepository
import app.photoxpo.photos.PreviewBitmapCache

@Composable
fun PhotoxpoScaffold(
    repository: PhotosRepository,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val previewCache = remember { PreviewBitmapCache() }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = route == null || route == AppRoutes.Home || route == AppRoutes.Photos

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = if (showBottomBar) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        bottomBar = {
            if (showBottomBar) {
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
            startDestination = AppRoutes.Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(AppRoutes.Home) {
                HomeRoute(modifier = Modifier.padding(innerPadding))
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

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
