package app.photoxpo.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
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

/** The floating bar's height plus the gap under it. Lists pad their end by this so the last row clears it. */
val FloatingNavClearance = 96.dp

/** The two destinations as a pill that floats over the content, for windows under 600 dp wide. */
@Composable
fun PhotoxpoFloatingNav(
    currentRoute: String?,
    onHome: () -> Unit,
    onPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FloatingNavItem(
                selected = currentRoute == AppRoutes.Home,
                label = "Home",
                icon = R.drawable.ic_nav_home,
                onClick = onHome,
            )
            FloatingNavItem(
                selected = currentRoute == AppRoutes.Photos,
                label = "Photos",
                icon = R.drawable.ic_nav_photos,
                onClick = onPhotos,
            )
        }
    }
}

@Composable
private fun FloatingNavItem(
    selected: Boolean,
    label: String,
    icon: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .defaultMinSize(minWidth = 104.dp, minHeight = 48.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = content,
            maxLines = 1,
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
private fun FloatingNavLargeFontPreview() {
    PhotoxpoTheme {
        PhotoxpoFloatingNav(
            currentRoute = AppRoutes.Home,
            onHome = {},
            onPhotos = {},
        )
    }
}
