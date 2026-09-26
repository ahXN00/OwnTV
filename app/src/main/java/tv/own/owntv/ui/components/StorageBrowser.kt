package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

enum class BrowseMode { FOLDER, FILE }

/** One directory's contents, already split and sorted off the main thread (audit U2). */
private data class Listing(val folders: List<File>, val files: List<File>) {
    companion object { val EMPTY = Listing(emptyList(), emptyList()) }
}

/**
 * An in-app file/folder picker (the TV-safe replacement for SAF). In [BrowseMode.FOLDER] the user
 * navigates and taps "Use this folder"; in [BrowseMode.FILE] tapping a matching file picks it. Grabs
 * focus on open and after each navigation so the remote lands on the list immediately.
 */
@Composable
fun StorageBrowser(
    title: String,
    mode: BrowseMode,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    fileExtensions: Set<String>? = null,
) {
    // Hosted in a real window: D-pad focus physically cannot escape to the screen behind. An
    // inline overlay loses focus containment when rows are added/removed (the grant-access row
    // after returning from system settings) and Compose reassigns focus outside the trap.
    // "New folder" is opened beside this popup, never inside it: a popup nested in another inherits the
    // first one's already-applied popup theme, so it skipped the user's popup font and size settings.
    var createIn by remember { mutableStateOf<((String) -> Unit)?>(null) }
    OwnTVPopup(
        onDismissRequest = onDismiss,
        dismissOnBackPress = false,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.72f) {
            StorageBrowserContent(title, mode, onPick, onDismiss, fileExtensions, onNewFolder = { createIn = it })
        }
    }
    createIn?.let { create ->
        NewFolderDialog(
            onCreate = { name -> create(name); createIn = null },
            onDismiss = { createIn = null },
        )
    }
}

@Composable
private fun StorageBrowserContent(
    title: String,
    mode: BrowseMode,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    fileExtensions: Set<String>?,
    /** Ask for a folder name; the lambda creates it in the folder shown now. */
    onNewFolder: (onCreate: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val roots = remember { StorageAccess.storageRoots(context) }
    var current by remember { mutableStateOf<File?>(null) } // null = the roots list
    var hasAccess by remember { mutableStateOf(StorageAccess.hasStorageAccess(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    // Re-check on resume — the settings screen returns no activity result.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = StorageAccess.hasStorageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { if (current != null) current = current?.parentFile else onDismiss() }

    // Re-grab focus whenever the listing changes (open / navigate / refresh). Deferred a beat: the
    // clicked row is removed in the same recompose, and its focus teardown lands AFTER an immediate
    // request — which would leave focus on whatever sits behind the overlay.
    LaunchedEffect(current, hasAccess, refresh) {
        kotlinx.coroutines.delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        // scroll = false: the listing below is a height-capped LazyColumn, which cannot nest inside
        // dialogPanel's own vertical scroll.
        Column(Modifier.dialogPanel(width = 270.dp, corner = 16.dp, padding = 14.dp, scroll = false)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(current?.absolutePath ?: stringResource(R.string.setup_pick_location), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            val dir = current
            // U2 — listFiles() plus the per-child isDirectory/isFile stats are disk work, and a
            // `remember` block still runs it on the main thread during composition: a USB drive with
            // a large folder stalled the frame. Load it on IO instead; the ".." / roots rows render
            // immediately either way, so D-pad focus still lands the moment the dialog opens.
            val listing by produceState(Listing.EMPTY, dir, refresh, mode, fileExtensions) {
                value = Listing.EMPTY
                value = withContext(Dispatchers.IO) {
                    val children = runCatching { dir?.listFiles()?.toList() }.getOrNull().orEmpty()
                    Listing(
                        folders = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() },
                        files = if (mode == BrowseMode.FILE) {
                            children.filter { it.isFile && (fileExtensions == null || it.extension.lowercase() in fileExtensions) }
                                .sortedBy { it.name.lowercase() }
                        } else emptyList(),
                    )
                }
            }
            val folders = listing.folders
            val files = listing.files

            // Above the list, not below it. Below, the only way down to it was through every folder
            // in the listing — about thirty presses in a full directory, for the one action the user
            // opened a folder picker to perform. Above, it is a single Up from the first row no
            // matter how long the listing is, and the list still opens focused so navigating first
            // costs nothing.
            if (mode == BrowseMode.FOLDER && current != null) {
                OwnTVButton(stringResource(R.string.setup_use_folder), onClick = { current?.let(onPick) }, modifier = Modifier.fillMaxWidth(), compact = true)
                Spacer(Modifier.height(8.dp))
            }

            // Cap the list to the screen (minus dialog chrome) so the footer buttons stay reachable.
            val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 200.dp).coerceIn(140.dp, 200.dp)
            LazyColumn(Modifier.heightIn(max = listMax).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (dir == null) {
                    if (!hasAccess) {
                        item {
                            BrowserRow(OwnTVIcon.SETTINGS, stringResource(R.string.setup_grant_storage_access), Modifier.focusRequester(firstFocus)) {
                                StorageAccess.openStoragePermissionSettings(context)
                            }
                        }
                    }
                    itemsIndexed(roots) { i, root ->
                        val m = if (i == 0 && hasAccess) Modifier.focusRequester(firstFocus) else Modifier
                        BrowserRow(OwnTVIcon.DOWNLOADS, root.displayLabel(), m) { current = root.file }
                    }
                } else {
                    item { BrowserRow(OwnTVIcon.BACK, stringResource(R.string.setup_from_current_folder), Modifier.focusRequester(firstFocus)) { current = dir.parentFile } }
                    itemsIndexed(folders) { _, f -> BrowserRow(OwnTVIcon.DOWNLOADS, f.name) { current = f } }
                    itemsIndexed(files) { _, f -> BrowserRow(OwnTVIcon.PLAYLIST, f.name) { onPick(f) } }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, compact = true)
                Spacer(Modifier.weight(1f))
                if (current != null) OwnTVButton(stringResource(R.string.setup_new_folder), onClick = {
                    onNewFolder { name ->
                        current?.let { runCatching { File(it, StorageAccess.sanitize(name)).mkdirs() } }
                        refresh++
                    }
                }, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.ADD, compact = true)
            }
        }
    }
}

@Composable
private fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // A popup window of its own: drawn in the browser's layer, the browser's focus trap kept the cursor
    // behind this dialog and the name could never be typed. Deferred a beat so the window is attached.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { focus.requestFocus() }
    }
    OwnTVPopup(onDismissRequest = onDismiss) {
    tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.72f) {
    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 420.dp, corner = 18.dp, fill = colors.surfaceContainerHighest)) {
            Text(stringResource(R.string.setup_new_folder), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(14.dp))
            OwnTVTextField(name, { name = it }, label = stringResource(R.string.setup_folder_name), placeholder = stringResource(R.string.setup_folder_example), modifier = Modifier.fillMaxWidth().focusRequester(focus), surface = GlassSurface.DIALOGS)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_create), onClick = { onCreate(name) }, enabled = name.isNotBlank())
            }
        }
    }
    }
    }
}

@Composable
private fun StorageAccess.StorageRoot.displayLabel(): String = when (kind) {
    StorageAccess.RootKind.INTERNAL -> stringResource(R.string.content_storage_internal)
    StorageAccess.RootKind.REMOVABLE -> volumeName ?: stringResource(R.string.content_storage_removable)
    StorageAccess.RootKind.APP -> stringResource(R.string.content_storage_app)
}

@Composable
private fun BrowserRow(icon: OwnTVIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), contentAlignment = Alignment.CenterStart, surface = GlassSurface.DIALOGS) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwnTVIcon(icon, tint = if (focused) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(
                    if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                ),
            )
        }
    }
}
