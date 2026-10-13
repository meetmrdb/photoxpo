package app.photoxpo.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.photoxpo.R
import app.photoxpo.ui.theme.PhotoOverlay

/**
 * The actions under a photo: Share, Info and Load, plus a card that says what
 * Load is doing. The Load wording comes from [loadUiText].
 */
@Composable
fun LoadSlot(
    photo: PhotoEntity,
    repository: PhotosRepository,
    controller: LoadController,
    onInfo: () -> Unit,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val driveOnline by produceState(initialValue = repository.driveOnline.value, repository) {
        repository.driveOnline.collect { value = it }
    }
    val state by produceState(initialValue = controller.state.value, controller) {
        controller.state.collect { value = it }
    }
    val text = loadUiText(
        state = state,
        sizeBytes = photo.sizeBytes,
        driveOnline = driveOnline,
        originalOnPhone = photo.originalOnPhone,
    )
    val failed = state is LoadUiState.Failed
    val message = text.message?.takeIf { it != LOAD_ON_PHONE_AND_DRIVE }
    val showCard = message != null || text.progressFraction != null || text.showCancel || text.showRetry

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showCard) {
            LoadCard(
                message = message,
                progressFraction = text.progressFraction,
                failed = failed,
                showCancel = text.showCancel,
                showRetry = text.showRetry,
                onCancel = controller::cancel,
                onRetry = { controller.load(photo) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            RingAction(
                label = "Share",
                icon = painterResource(R.drawable.ic_share),
                enabled = onShare != null,
                onClick = { onShare?.invoke() },
            )
            RingAction(
                label = "Info",
                icon = painterResource(R.drawable.ic_info),
                enabled = true,
                onClick = onInfo,
            )
            val loadLabel = text.loadLabel ?: if (state is LoadUiState.Loading) "Loading" else "Loaded"
            RingAction(
                label = loadLabel,
                icon = painterResource(R.drawable.ic_download),
                enabled = text.loadEnabled,
                main = true,
                onClick = { controller.load(photo) },
            )
        }
    }
}

@Composable
private fun RingAction(
    label: String,
    icon: Painter,
    enabled: Boolean,
    onClick: () -> Unit,
    main: Boolean = false,
) {
    val ringColor = if (main && enabled) PhotoOverlay.Mint else PhotoOverlay.Ring
    val iconColor = if (main && enabled) PhotoOverlay.OnMint else PhotoOverlay.OnScrim
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 80.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = PhotoOverlay.OnScrim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadCard(
    message: String?,
    progressFraction: Float?,
    failed: Boolean,
    showCancel: Boolean,
    showRetry: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (failed) colors.errorContainer else PhotoOverlay.LoadCard
    val content = if (failed) colors.onErrorContainer else PhotoOverlay.OnLoadCard
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (progressFraction != null) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = PhotoOverlay.Mint,
                trackColor = PhotoOverlay.Track,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        if (message != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (failed) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                )
            }
        }
        if (showCancel || showRetry) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showCancel) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = PhotoOverlay.Mint),
                    ) {
                        Text("Cancel")
                    }
                }
                if (showRetry) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = content),
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
