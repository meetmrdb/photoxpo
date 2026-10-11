package app.photoxpo.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadSlot(
    photo: PhotoEntity,
    repository: PhotosRepository,
    controller: LoadController,
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
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fraction = text.progressFraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val message = text.message
        if (message != null) {
            Text(
                text = message,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val label = text.loadLabel
        if (label != null) {
            Button(
                onClick = { controller.load(photo) },
                enabled = text.loadEnabled,
            ) {
                Text(label)
            }
        }
        if (text.showCancel) {
            Button(onClick = controller::cancel) {
                Text("Cancel")
            }
        }
        if (text.showRetry) {
            Button(onClick = { controller.load(photo) }) {
                Text("Retry")
            }
        }
    }
}
