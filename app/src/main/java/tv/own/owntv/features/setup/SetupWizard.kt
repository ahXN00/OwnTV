package tv.own.owntv.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.features.profiles.ProfileEditorDialog
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class Step { WELCOME, DISCLAIMER, SETUP_CHOICE, CREATE_PROFILE, ADD_CONTENT, ADD_SOURCE, IMPORTING, EXISTING, IMPORT_BACKUP }

/**
 * Onboarding for one profile. [firstRun] shows the welcome/disclaimer; otherwise it starts at profile
 * creation (used by "Add profile"). [onDone] enters the app; [onCancel] backs out (to the gate).
 */
@Composable
fun Onboarding(firstRun: Boolean, onDone: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SetupViewModel = koinViewModel()
    var step by remember { mutableStateOf(if (firstRun) Step.WELCOME else Step.CREATE_PROFILE) }
    val importState by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var existing by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    // Where "Try Again" returns to when an import fails (new source vs. linking existing).
    var importOrigin by remember { mutableStateOf(Step.ADD_SOURCE) }
    // Where Back from the backup-restore picker returns to (first-run choice vs. add-content step).
    var backupOrigin by remember { mutableStateOf(Step.ADD_CONTENT) }

    // Refresh the "existing playlists" availability whenever we land on the add-content step.
    LaunchedEffect(step) { if (step == Step.ADD_CONTENT) existing = runCatching { vm.availableExistingSources() }.getOrDefault(emptyList()) }

    Box(modifier = modifier.fillMaxSize().background(OwnTVTheme.colors.background)) {
        when (step) {
            Step.WELCOME -> WelcomeScreen(onNext = { step = Step.DISCLAIMER })
            Step.DISCLAIMER -> DisclaimerScreen(onAgree = { step = Step.SETUP_CHOICE }, onBack = { step = Step.WELCOME })
            // First decision: start fresh or bring everything back from a backup (profiles included —
            // no point creating a profile first that the restore would replace).
            Step.SETUP_CHOICE -> SetupChoiceScreen(
                onCreate = { step = Step.CREATE_PROFILE },
                onRestore = { backupOrigin = Step.SETUP_CHOICE; step = Step.IMPORT_BACKUP },
                onBack = { step = Step.DISCLAIMER },
            )
            Step.CREATE_PROFILE -> ProfileEditorDialog(
                initial = null,
                onConfirm = { name, avatar, kids, pin -> vm.createProfile(name, avatar, kids, pin); step = Step.ADD_CONTENT },
                onDismiss = { if (firstRun) step = Step.SETUP_CHOICE else onCancel() },
            )
            Step.ADD_CONTENT -> AddContentScreen(
                hasExisting = existing.isNotEmpty(),
                onNew = { step = Step.ADD_SOURCE },
                onExisting = { step = Step.EXISTING },
                onImport = { backupOrigin = Step.ADD_CONTENT; step = Step.IMPORT_BACKUP },
                onSkip = { vm.finish(); onDone() },
            )
            Step.ADD_SOURCE -> AddSourceScreen(
                onStartXtream = { name, server, user, pass, ua, epg, refresh -> vm.startXtream(name, server, user, pass, ua, epg, refresh); importOrigin = Step.ADD_SOURCE; step = Step.IMPORTING },
                onStartM3u = { name, url, ua, epg, refresh -> vm.startM3u(name, url, ua, epg, refresh); importOrigin = Step.ADD_SOURCE; step = Step.IMPORTING },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.IMPORTING -> ImportProgressScreen(
                state = importState,
                stageLabel = progress?.label ?: "content",
                processed = progress?.processed ?: 0,
                onContinue = { vm.finish(); onDone() }, // playlist + its EPG synced (auto)
                onRetry = { vm.reset(); step = importOrigin },
            )
            Step.EXISTING -> ExistingSourcesScreen(
                sources = existing,
                onAdd = { ids -> vm.linkExisting(ids); importOrigin = Step.EXISTING; step = Step.IMPORTING },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.IMPORT_BACKUP -> ImportBackupScreen(
                state = importState,
                onPick = { file -> vm.importBackup(file) { onDone() } }, // restore activates a profile itself
                onBack = { vm.reset(); step = backupOrigin },
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onNext: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Centered {
        BrandLockup(markSize = 72, textSize = 44)
        Spacer(Modifier.height(16.dp))
        Text("Your own IPTV player.", style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        OwnTVButton("Get Started", onClick = onNext, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
    }
}

@Composable
private fun DisclaimerScreen(onAgree: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Centered {
        Text("Before you start", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(16.dp))
        Text(
            "OwnTV is a media player only. It includes no channels, playlists, or content. You are " +
                "responsible for adding your own legally accessible M3U or Xtream sources.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
            OwnTVButton("I Understand", onClick = onAgree, modifier = Modifier.focusRequester(fr))
        }
    }
}

@Composable
private fun SetupChoiceScreen(onCreate: () -> Unit, onRestore: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    Centered {
        Text("Set up OwnTV", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "Start fresh, or bring back your profiles, playlists and favorites from a backup file.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.PERSON, title = "New profile", desc = "Create a profile and add sources", modifier = Modifier.focusRequester(fr), onClick = onCreate)
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = "Restore backup", desc = "Import profiles & playlists from a file", onClick = onRestore)
        }
    }
}

@Composable
private fun AddContentScreen(hasExisting: Boolean, onNew: () -> Unit, onExisting: () -> Unit, onImport: () -> Unit, onSkip: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Centered {
        Text("Add a playlist", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Add a source for this profile, or set one up later.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.ADD, title = "New", desc = "Add an M3U or Xtream source", modifier = Modifier.focusRequester(fr), onClick = onNew)
            if (hasExisting) {
                ChoiceCard(icon = OwnTVIcon.PLAYLIST, title = "Existing", desc = "Use another profile's playlists", onClick = onExisting)
            }
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = "Import", desc = "Restore from a backup file", onClick = onImport)
        }
        Spacer(Modifier.height(24.dp))
        OwnTVButton("Skip for now", onClick = onSkip, style = OwnTVButtonStyle.SECONDARY)
    }
}

@Composable
private fun ExistingSourcesScreen(sources: List<SourceEntity>, onAdd: (Set<Long>) -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Use existing playlists", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text("Pick the playlists to add to this profile.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.id }) { src ->
                    val checked = src.id in selected
                    FocusableSurface(
                        onClick = { selected = if (checked) selected - src.id else selected + src.id },
                        modifier = if (src.id == sources.firstOrNull()?.id) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        selected = checked,
                        shape = RoundedCornerShape(12.dp),
                        selectedContainerColor = colors.primaryContainer,
                        contentAlignment = Alignment.CenterStart,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.titleMedium, color = if (checked) colors.onPrimaryContainer else colors.onSurface)
                                Text(src.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                            if (checked) OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton("Add ${selected.size}", onClick = { onAdd(selected) }, enabled = selected.isNotEmpty())
            }
        }
    }
}

@Composable
private fun ImportBackupScreen(state: SetupViewModel.ImportState, onPick: (java.io.File) -> Unit, onBack: () -> Unit) {
    when (state) {
        SetupViewModel.ImportState.Running -> Centered {
            OwnTVSpinner(sizeDp = 56); Spacer(Modifier.height(16.dp))
            Text("Restoring…", style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurface)
        }
        is SetupViewModel.ImportState.Failed -> Centered {
            Text("Restore failed", style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
            Spacer(Modifier.height(20.dp))
            OwnTVButton("Back", onClick = onBack)
        }
        else -> StorageBrowser(
            title = "Pick a backup file to restore",
            mode = BrowseMode.FILE,
            fileExtension = "json",
            onPick = onPick,
            onDismiss = onBack,
        )
    }
}

@Composable
private fun ChoiceCard(icon: OwnTVIcon, title: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(width = 220.dp, height = 170.dp),
        shape = RoundedCornerShape(22.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                OwnTVIcon(icon, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (focused) colors.primary else colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ImportProgressScreen(
    state: SetupViewModel.ImportState,
    stageLabel: String,
    processed: Int,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(state) {
        if (state is SetupViewModel.ImportState.Success || state is SetupViewModel.ImportState.Failed) runCatching { fr.requestFocus() }
    }
    Centered {
        when (state) {
            SetupViewModel.ImportState.Running, SetupViewModel.ImportState.Idle -> {
                OwnTVSpinner(sizeDp = 56)
                Spacer(Modifier.height(20.dp))
                Text("Importing ${stageLabel.lowercase()}…", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(if (processed > 0) formatCount(processed) else "Connecting…", style = MaterialTheme.typography.headlineLarge, color = colors.primary)
            }
            is SetupViewModel.ImportState.Success -> {
                Text("All set!", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(state.summary, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                Spacer(Modifier.height(28.dp))
                OwnTVButton("Continue", onClick = onContinue, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
            }
            is SetupViewModel.ImportState.Failed -> {
                Text("Import failed", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
                Spacer(Modifier.height(28.dp))
                OwnTVButton("Try Again", onClick = onRetry, modifier = Modifier.focusRequester(fr))
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
