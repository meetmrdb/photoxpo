package app.photoxpo.nav

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.photoxpo.R
import app.photoxpo.ui.theme.PhotoxpoTheme

@Composable
fun PhotoxpoBottomBar(
    currentRoute: String?,
    onHome: () -> Unit,
    onPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentRoute == AppRoutes.Home,
            onClick = onHome,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_home),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { BarLabel("Home") },
            alwaysShowLabel = true,
        )
        NavigationBarItem(
            selected = currentRoute == AppRoutes.Photos,
            onClick = onPhotos,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_photos),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { BarLabel("Photos") },
            alwaysShowLabel = true,
        )
    }
}

/** The same two destinations as a rail, for windows at least 600 dp wide. */
@Composable
fun PhotoxpoRail(
    currentRoute: String?,
    onHome: () -> Unit,
    onPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        NavigationRailItem(
            selected = currentRoute == AppRoutes.Home,
            onClick = onHome,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_home),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { BarLabel("Home") },
            alwaysShowLabel = true,
        )
        NavigationRailItem(
            selected = currentRoute == AppRoutes.Photos,
            onClick = onPhotos,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_photos),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { BarLabel("Photos") },
            alwaysShowLabel = true,
        )
    }
}

@Composable
private fun BarLabel(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        maxLines = 2,
        softWrap = true,
        overflow = TextOverflow.Clip,
    )
}

@Preview(showBackground = true, widthDp = 480, fontScale = 2f)
@Composable
private fun BottomBarLargeFontPreview() {
    PhotoxpoTheme {
        PhotoxpoBottomBar(
            currentRoute = AppRoutes.Home,
            onHome = {},
            onPhotos = {},
        )
    }
}
