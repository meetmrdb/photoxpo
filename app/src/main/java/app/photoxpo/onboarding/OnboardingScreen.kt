package app.photoxpo.onboarding

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
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
        if (ui.onboarding.step == OnboardingStep.PhotosAccess) viewModel.refreshAccess()
    }
    LaunchedEffect(ui.onboarding.choosingPreviews) {
        if (ui.onboarding.choosingPreviews) viewModel.ensurePreviewPhoto()
    }

    BackHandler(enabled = ui.onboarding.step != OnboardingStep.Welcome || ui.onboarding.choosingPreviews) {
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
    val back = { viewModel.onEvent(OnboardingEvent.Back) }

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
                onBack = back,
            )
            OnboardingStep.ArchiveDrive -> ArchiveDriveStep(
                driveChosen = ui.onboarding.driveChosen,
                driveMessage = ui.driveMessage,
                onChoose = pickDrive,
                onLater = { viewModel.deferDrive() },
                onContinue = { viewModel.onEvent(OnboardingEvent.Next) },
                onBack = back,
            )
            OnboardingStep.Ready -> ReadyStep(
                summary = readySummary(ui.onboarding),
                onChooseDrive = pickDrive,
                onChangePreviews = { viewModel.onEvent(OnboardingEvent.OpenPreviews) },
                onFinish = finish,
                onBack = back,
            )
        }
        if (ui.onboarding.choosingPreviews) {
            PreviewsSheet(
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
                onDone = { viewModel.onEvent(OnboardingEvent.ClosePreviews) },
            )
        }
    }
}

private const val STEP_COUNT = 4

@Composable
private fun WelcomeStep(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepFrame(
        index = 0,
        onBack = null,
        modifier = modifier,
        actions = {
            BigButton(
                label = stringResource(R.string.onboarding_get_started),
                onClick = onGetStarted,
            )
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo_full),
            contentDescription = "Photoxpo",
            modifier = Modifier.height(44.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        OriginalAndPreview()
        ScreenTitle(stringResource(R.string.onboarding_welcome_title))
        WrappingText(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    StepFrame(
        index = 1,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (access == MediaAccessState.Full) {
                BigButton(
                    label = stringResource(R.string.onboarding_continue),
                    onClick = onContinue,
                )
            } else if (warning != null) {
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
        },
    ) {
        ScreenTitle(stringResource(R.string.onboarding_photos_title))
        InfoCard(
            icon = R.drawable.ic_folder_photos,
            title = stringResource(R.string.onboarding_photos_card_title),
            body = stringResource(R.string.onboarding_photos_card_body),
        )
        if (access != MediaAccessState.Full) {
            WhatThePhoneWillAsk()
        }
        if (access == MediaAccessState.Full) {
            ToneCard(
                text = stringResource(R.string.onboarding_photos_seen),
                error = false,
            )
        } else if (warning != null) {
            ToneCard(
                title = warning.title,
                text = warning.message,
                error = true,
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
    StepFrame(
        index = 2,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (driveChosen) {
                BigButton(
                    label = stringResource(R.string.onboarding_continue),
                    onClick = onContinue,
                )
                QuietButton(
                    label = stringResource(R.string.onboarding_drive_choose_again),
                    onClick = onChoose,
                )
            } else {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        ScreenTitle(stringResource(R.string.onboarding_drive_title))
        NumberedStep(number = 1, title = stringResource(R.string.onboarding_drive_step_plug))
        NumberedStep(
            number = 2,
            title = stringResource(R.string.onboarding_drive_step_choose),
            body = stringResource(R.string.onboarding_drive_step_choose_body),
        )
        if (driveChosen) {
            ToneCard(
                text = stringResource(R.string.onboarding_drive_ready),
                error = false,
            )
        } else if (driveMessage != null) {
            ToneCard(text = driveMessage, error = true)
        }
    }
}

@Composable
private fun ReadyStep(
    summary: ReadySummary,
    onChooseDrive: () -> Unit,
    onChangePreviews: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = readyLines(summary)
    StepFrame(
        index = 3,
        onBack = onBack,
        modifier = modifier,
        actions = { BigButton(label = lines.button, onClick = onFinish) },
    ) {
        ScreenTitle(stringResource(R.string.onboarding_ready_title))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
        ) {
            SummaryRow(
                icon = if (summary.driveChosen) R.drawable.ic_check_circle else R.drawable.ic_schedule,
                iconTint = if (summary.driveChosen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = stringResource(R.string.onboarding_ready_drive_label),
                value = lines.driveValue,
                action = if (summary.driveChosen) null else stringResource(R.string.onboarding_choose),
                onAction = onChooseDrive,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryRow(
                icon = R.drawable.ic_folder_photos,
                iconTint = MaterialTheme.colorScheme.primary,
                label = stringResource(R.string.onboarding_ready_previews_label),
                value = lines.previewsValue,
                action = stringResource(R.string.onboarding_change),
                onAction = onChangePreviews,
            )
        }
        lines.note?.let { note ->
            WrappingText(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The one idea of the app as a picture: the Original goes to the drive, a Preview stays on the phone. */
@Composable
private fun OriginalAndPreview() {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.onboarding_welcome_diagram)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiagramNode(
            label = stringResource(R.string.onboarding_preview_label),
            where = stringResource(R.string.onboarding_on_phone),
        ) {
            val tints = listOf(colors.primary, colors.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) { column ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        tints[(row + column) % tints.size].copy(alpha = 0.55f + 0.1f * ((row * 3 + column) % 4)),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(28.dp)
                .rotate(180f),
        )
        DiagramNode(
            label = stringResource(R.string.onboarding_original_label),
            where = stringResource(R.string.onboarding_on_drive),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_archive_drive),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun DiagramNode(label: String, where: String, picture: @Composable () -> Unit) {
    Column(
        modifier = Modifier.width(132.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { picture() }
        Text(text = label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = where,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InfoCard(icon: Int, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the system dialog will offer, with the right answer marked. Decorative: the sentence above it says the same. */
@Composable
private fun WhatThePhoneWillAsk() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WrappingText(
            text = stringResource(R.string.onboarding_photos_ask_next),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .background(colors.surfaceContainerHighest, RoundedCornerShape(20.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MockChoice(stringResource(R.string.onboarding_mock_allow_all), highlighted = true)
            MockChoice(stringResource(R.string.onboarding_mock_select), highlighted = false)
            MockChoice(stringResource(R.string.onboarding_mock_deny), highlighted = false)
        }
        WrappingText(
            text = stringResource(R.string.onboarding_photos_select_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MockChoice(label: String, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .background(colors.primaryContainer, RoundedCornerShape(14.dp))
                        .border(2.dp, colors.primary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
        if (highlighted) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NumberedStep(number: Int, title: String, body: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    icon: Int,
    iconTint: Color,
    label: String,
    value: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

/** Back arrow and a thin bar on top, the content in the middle, the buttons at the bottom where the thumb is. */
@Composable
private fun StepFrame(
    index: Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.onboarding_back),
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            StepProgress(current = index, total = STEP_COUNT, modifier = Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = actions,
        )
    }
}

@Composable
private fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.onboarding_step, current + 1, total)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { part ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        if (part <= current) colors.primary else colors.surfaceContainerHighest,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        softWrap = true,
        overflow = TextOverflow.Visible,
    )
}

@Composable
internal fun WrappingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style,
        color = color,
        textAlign = textAlign,
        softWrap = true,
        overflow = TextOverflow.Visible,
    )
}

@Composable
internal fun BigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
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

@Preview(name = "Welcome", showBackground = true, fontScale = 1.3f, widthDp = 360, heightDp = 800)
@Composable
private fun WelcomePreview() {
    PhotoxpoTheme { WelcomeStep(onGetStarted = {}) }
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
            onChooseDrive = {},
            onChangePreviews = {},
            onFinish = {},
            onBack = {},
        )
    }
}
