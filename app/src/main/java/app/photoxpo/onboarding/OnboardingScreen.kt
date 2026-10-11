package app.photoxpo.onboarding

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.photoxpo.R
import app.photoxpo.core.PreviewPreset
import app.photoxpo.drive.DrivePickResult
import app.photoxpo.drive.rememberDrivePicker
import app.photoxpo.ui.theme.PhotoxpoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val mediaAccessPermissions = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.ACCESS_MEDIA_LOCATION,
)

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = context as ViewModelStoreOwner
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            OnboardingViewModel.factory(context.applicationContext),
        )[OnboardingViewModel::class.java]
    }
    OnboardingScreen(
        viewModel = viewModel,
        onFinished = onFinished,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val step by rememberUpdatedState(ui.onboarding.step)
    var finished by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && step == OnboardingStep.PhotosAccess) {
                viewModel.refreshAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.onboarding.step) {
        when (ui.onboarding.step) {
            OnboardingStep.PhotosAccess -> viewModel.refreshAccess()
            OnboardingStep.PreviewQuality -> viewModel.ensurePreviewPhoto()
            else -> Unit
        }
    }

    BackHandler(enabled = ui.onboarding.step != OnboardingStep.Welcome) {
        viewModel.onEvent(OnboardingEvent.Back)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onPermissionResult()
    }
    val requestAccess = {
        permissionLauncher.launch(mediaAccessPermissions)
    }
    val openSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
    val pickDrive = rememberDrivePicker { result ->
        when (result) {
            is DrivePickResult.Chosen -> viewModel.onDriveChosen()
            DrivePickResult.ReadOnly -> viewModel.onDriveMessage(driveReadOnlyMessage())
            DrivePickResult.Cancelled -> Unit
            is DrivePickResult.Failed -> viewModel.onDriveMessage(driveFailureMessage(result.message))
        }
    }
    val finish = {
        if (!finished) {
            viewModel.finish()
            finished = true
            onFinished()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (ui.onboarding.step) {
            OnboardingStep.Welcome -> WelcomeStep(
                onGetStarted = { viewModel.onEvent(OnboardingEvent.Next) },
            )
            OnboardingStep.PhotosAccess -> PhotosAccessStep(
                access = ui.access,
                requested = ui.accessRequested,
                onAllow = requestAccess,
                onOpenSettings = openSettings,
                onTryAgain = requestAccess,
                onContinue = { viewModel.onEvent(OnboardingEvent.Next) },
                onBack = { viewModel.onEvent(OnboardingEvent.Back) },
            )
            OnboardingStep.ArchiveDrive -> ArchiveDriveStep(
                driveChosen = ui.onboarding.driveChosen,
                driveMessage = ui.driveMessage,
                onChoose = pickDrive,
                onLater = { viewModel.deferDrive() },
                onContinue = { viewModel.onEvent(OnboardingEvent.Next) },
                onBack = { viewModel.onEvent(OnboardingEvent.Back) },
            )
            OnboardingStep.PreviewQuality -> PreviewQualityStep(
                preset = ui.onboarding.preset,
                usingSample = ui.usingSample,
                photoName = ui.photoName,
                originalBytes = ui.originalBytes,
                canTryAnother = ui.canTryAnother,
                cards = qualityCardModels(
                    selected = ui.onboarding.preset,
                    inputs = PreviewPreset.entries.associateWith { preset ->
                        val card = ui.cards[preset]
                        QualityCardInput(
                            previewBytes = card?.preview?.bytes?.size?.toLong(),
                            projectedBytes = card?.projectedBytes,
                            failed = card?.failed == true,
                        )
                    },
                    libraryCount = ui.libraryCount,
                ),
                images = PreviewPreset.entries.associateWith { ui.cards[it]?.preview },
                onPreset = { viewModel.onEvent(OnboardingEvent.PresetChosen(it)) },
                onTryAnother = { viewModel.tryAnotherPhoto() },
                onContinue = { viewModel.onEvent(OnboardingEvent.Next) },
                onBack = { viewModel.onEvent(OnboardingEvent.Back) },
            )
            OnboardingStep.Ready -> ReadyStep(
                summary = readySummary(ui.onboarding),
                onFinish = finish,
                onBack = { viewModel.onEvent(OnboardingEvent.Back) },
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepColumn(modifier) {
        StepLabel(OnboardingStep.Welcome)
        ScreenTitle(stringResource(R.string.onboarding_welcome_sentence))
        BigButton(
            label = stringResource(R.string.onboarding_get_started),
            onClick = onGetStarted,
        )
    }
}

@Composable
private fun PhotosAccessStep(
    access: MediaAccessState?,
    requested: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onTryAgain: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = if (showAccessWarning(access, requested)) {
        access?.let { accessWarning(it) }
    } else {
        null
    }
    StepColumn(modifier) {
        BackButton(onBack)
        StepLabel(OnboardingStep.PhotosAccess)
        ScreenTitle(stringResource(R.string.onboarding_photos_title))
        WrappingText(
            text = stringResource(R.string.onboarding_photos_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (access != MediaAccessState.Full) {
            WrappingText(
                text = stringResource(R.string.onboarding_photos_allow_all),
                style = MaterialTheme.typography.bodyLarge,
            )
            WrappingText(
                text = stringResource(R.string.onboarding_photos_select_warning),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (access == MediaAccessState.Full) {
            ToneCard(
                text = stringResource(R.string.onboarding_photos_seen),
                error = false,
            )
            BigButton(
                label = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
            )
        } else if (warning != null) {
            ToneCard(
                title = warning.title,
                text = warning.message,
                error = true,
            )
            when (warning.action) {
                AccessAction.OpenSettings -> {
                    BigButton(
                        label = stringResource(R.string.onboarding_open_settings),
                        onClick = onOpenSettings,
                    )
                    QuietButton(
                        label = stringResource(R.string.onboarding_try_again),
                        onClick = onTryAgain,
                    )
                }
                AccessAction.TryAgain -> {
                    BigButton(
                        label = stringResource(R.string.onboarding_try_again),
                        onClick = onTryAgain,
                    )
                    QuietButton(
                        label = stringResource(R.string.onboarding_open_settings),
                        onClick = onOpenSettings,
                    )
                }
            }
        } else {
            BigButton(
                label = stringResource(R.string.onboarding_photos_allow),
                onClick = onAllow,
            )
        }
    }
}

@Composable
private fun ArchiveDriveStep(
    driveChosen: Boolean,
    driveMessage: String?,
    onChoose: () -> Unit,
    onLater: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepColumn(modifier) {
        BackButton(onBack)
        StepLabel(OnboardingStep.ArchiveDrive)
        ScreenTitle(stringResource(R.string.onboarding_drive_title))
        WrappingText(
            text = stringResource(R.string.onboarding_drive_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (driveChosen) {
            ToneCard(
                text = stringResource(R.string.onboarding_drive_ready),
                error = false,
            )
            BigButton(
                label = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
            )
            QuietButton(
                label = stringResource(R.string.onboarding_drive_choose_again),
                onClick = onChoose,
            )
        } else {
            if (driveMessage != null) {
                ToneCard(text = driveMessage, error = true)
            }
            BigButton(
                label = stringResource(R.string.onboarding_drive_choose),
                onClick = onChoose,
            )
            QuietButton(
                label = stringResource(R.string.onboarding_drive_later),
                onClick = onLater,
            )
            WrappingText(
                text = stringResource(R.string.onboarding_drive_later_note),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewQualityStep(
    preset: PreviewPreset,
    usingSample: Boolean,
    photoName: String?,
    originalBytes: Long?,
    canTryAnother: Boolean,
    cards: List<QualityCardModel>,
    images: Map<PreviewPreset, PreviewJpeg?>,
    onPreset: (PreviewPreset) -> Unit,
    onTryAnother: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepColumn(modifier) {
        BackButton(onBack)
        StepLabel(OnboardingStep.PreviewQuality)
        ScreenTitle(stringResource(R.string.onboarding_quality_title))
        WrappingText(
            text = stringResource(R.string.onboarding_quality_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        WrappingText(
            text = stringResource(
                if (usingSample) R.string.onboarding_sample_photo else R.string.onboarding_your_photo,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (usingSample) {
            WrappingText(
                text = stringResource(R.string.onboarding_no_photos),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (photoName != null) {
            WrappingText(
                text = photoName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val original = if (usingSample) null else originalSizeLabel(originalBytes)
        if (original != null) {
            WrappingText(
                text = original,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (canTryAnother) {
            QuietButton(
                label = stringResource(R.string.onboarding_try_another),
                onClick = onTryAnother,
            )
        }
        cards.forEach { card ->
            QualityCard(
                model = card,
                image = images[card.preset],
                onSelect = { onPreset(card.preset) },
            )
        }
        BigButton(
            label = usePresetLabel(preset),
            onClick = onContinue,
        )
    }
}

@Composable
private fun ReadyStep(
    summary: ReadySummary,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = readyLines(summary)
    StepColumn(modifier) {
        BackButton(onBack)
        StepLabel(OnboardingStep.Ready)
        ScreenTitle(stringResource(R.string.onboarding_ready_title))
        WrappingText(
            text = lines.archiveDrive,
            style = MaterialTheme.typography.bodyLarge,
        )
        WrappingText(
            text = lines.previews,
            style = MaterialTheme.typography.bodyLarge,
        )
        val note = lines.note
        if (note != null) {
            WrappingText(
                text = note,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        BigButton(label = lines.button, onClick = onFinish)
    }
}

@Composable
private fun QualityCard(
    model: QualityCardModel,
    image: PreviewJpeg?,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (model.selected) 3.dp else 1.dp,
                color = if (model.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = shape,
            )
            .clip(shape)
            .selectable(
                selected = model.selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewStill(
            jpeg = image,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(12.dp)),
        )
        if (model.selected) {
            WrappingText(
                text = stringResource(R.string.onboarding_selected),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        WrappingText(
            text = model.name,
            style = MaterialTheme.typography.titleLarge,
        )
        if (model.recommended) {
            WrappingText(
                text = stringResource(R.string.onboarding_recommended),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        WrappingText(
            text = model.description,
            style = MaterialTheme.typography.bodyLarge,
        )
        WrappingText(
            text = model.previewSize,
            style = MaterialTheme.typography.bodyLarge,
        )
        WrappingText(
            text = model.estimate,
            style = MaterialTheme.typography.bodyLarge,
        )
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

@Composable
private fun StepColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun StepLabel(step: OnboardingStep) {
    WrappingText(
        text = stringResource(
            R.string.onboarding_step,
            step.ordinal + 1,
            OnboardingStep.entries.size,
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        softWrap = true,
        overflow = TextOverflow.Visible,
    )
}

@Composable
private fun WrappingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style,
        color = color,
        softWrap = true,
        overflow = TextOverflow.Visible,
    )
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            softWrap = true,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            softWrap = true,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(
        onClick = onBack,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_back),
            style = MaterialTheme.typography.bodyLarge,
            softWrap = true,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun ToneCard(
    text: String,
    error: Boolean,
    title: String? = null,
) {
    val container = if (error) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val onContainer = if (error) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            WrappingText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
        }
        WrappingText(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = onContainer,
        )
    }
}

@Preview(
    name = "Welcome",
    showBackground = true,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun WelcomeLargeTextPreview() {
    PhotoxpoTheme(darkTheme = true) {
        WelcomeStep(onGetStarted = {})
    }
}

@Preview(
    name = "Photos access",
    showBackground = true,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun PhotosAccessLargeTextPreview() {
    PhotoxpoTheme(darkTheme = true) {
        PhotosAccessStep(
            access = MediaAccessState.Partial,
            requested = true,
            onAllow = {},
            onOpenSettings = {},
            onTryAgain = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Archive Drive",
    showBackground = true,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun ArchiveDriveLargeTextPreview() {
    PhotoxpoTheme(darkTheme = true) {
        ArchiveDriveStep(
            driveChosen = false,
            driveMessage = driveReadOnlyMessage(),
            onChoose = {},
            onLater = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Preview quality",
    showBackground = true,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun PreviewQualityLargeTextPreview() {
    val cards = qualityCardModels(
        selected = PreviewPreset.BALANCED,
        inputs = mapOf(
            PreviewPreset.SMALLER to QualityCardInput(
                previewBytes = 80_000L,
                projectedBytes = 5L * 1024 * 1024 * 1024,
            ),
            PreviewPreset.BALANCED to QualityCardInput(
                previewBytes = 120_000L,
                projectedBytes = 6L * 1024 * 1024 * 1024,
            ),
            PreviewPreset.SHARPER to QualityCardInput(
                previewBytes = 260_000L,
                projectedBytes = 8L * 1024 * 1024 * 1024,
            ),
        ),
        libraryCount = 48_000,
    )
    PhotoxpoTheme(darkTheme = true) {
        PreviewQualityStep(
            preset = PreviewPreset.BALANCED,
            usingSample = false,
            photoName = "IMG_100.jpg",
            originalBytes = 4L * 1024 * 1024,
            canTryAnother = true,
            cards = cards,
            images = emptyMap(),
            onPreset = {},
            onTryAnother = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Ready",
    showBackground = true,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun ReadyLargeTextPreview() {
    PhotoxpoTheme(darkTheme = true) {
        ReadyStep(
            summary = readySummary(
                OnboardingState(
                    step = OnboardingStep.Ready,
                    driveDeferred = true,
                    preset = PreviewPreset.BALANCED,
                ),
            ),
            onFinish = {},
            onBack = {},
        )
    }
}
