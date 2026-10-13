package app.photoxpo.onboarding

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.photoxpo.R
import app.photoxpo.core.PreviewPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview quality, one tap from the Ready screen. Balanced stands unless the person picks another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewsSheet(
    preset: PreviewPreset,
    usingSample: Boolean,
    photoName: String?,
    originalBytes: Long?,
    canTryAnother: Boolean,
    cards: List<QualityCardModel>,
    images: Map<PreviewPreset, PreviewJpeg?>,
    onPreset: (PreviewPreset) -> Unit,
    onTryAnother: () -> Unit,
    onDone: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_quality_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            WrappingText(
                text = stringResource(R.string.onboarding_quality_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val original = if (usingSample) null else originalSizeLabel(originalBytes)
            val detail = when {
                usingSample -> stringResource(R.string.onboarding_no_photos)
                else -> listOfNotNull(photoName, original).joinToString("\n")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (usingSample) R.string.onboarding_sample_photo else R.string.onboarding_your_photo,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canTryAnother) {
                    TextButton(onClick = onTryAnother, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.onboarding_try_another))
                    }
                }
            }
            cards.forEach { card ->
                PreviewOption(
                    model = card,
                    image = images[card.preset],
                    onSelect = { onPreset(card.preset) },
                )
            }
            BigButton(label = usePresetLabel(preset), onClick = onDone)
        }
    }
}

@Composable
private fun PreviewOption(
    model: QualityCardModel,
    image: PreviewJpeg?,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (model.selected) colors.primaryContainer else colors.surfaceContainerHigh, shape)
            .border(2.dp, if (model.selected) colors.primary else Color.Transparent, shape)
            .clip(shape)
            .selectable(selected = model.selected, onClick = onSelect, role = Role.RadioButton)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewStill(
            jpeg = image,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = model.name, style = MaterialTheme.typography.titleMedium)
            if (model.recommended) {
                Text(
                    text = stringResource(R.string.onboarding_recommended),
                    modifier = Modifier
                        .background(colors.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimary,
                )
            }
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (model.selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
            Text(
                text = "${model.previewSize} · ${model.estimate}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(2.dp, if (model.selected) colors.primary else colors.outline, CircleShape)
                .background(if (model.selected) colors.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (model.selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.onboarding_selected),
                    tint = colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewStill(jpeg: PreviewJpeg?, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(initialValue = null, jpeg) {
        val bytes = jpeg?.bytes
        value = if (bytes == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }
    val current = image
    if (current == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
