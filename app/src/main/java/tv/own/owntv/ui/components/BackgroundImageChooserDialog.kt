package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import java.io.File

/**
 * Background image picker chooser — mirrors Backup's Local/Remote chooser UX:
 * - **From device**: opens the StorageBrowser to pick an image file, which is then copied into
 *   app-private storage (so a USB unplug / source-folder delete can't blank it).
 * - **Remote**: opens the LAN companion upload flow (PIN + QR, same as Remote backup restore)
 *   so another device on the same Wi-Fi — phone, tablet or PC — can send a photo to the TV.
 * - **Clear**: removes the current background (panels return to solid).
 *
 * @param onPickLocal invoked when the user taps "From device" (the host opens the StorageBrowser).
 * @param onPickRemote invoked when the user taps "Remote" (the host opens [RemoteBackgroundDialog]).
 * @param onClear invoked when the user taps "Clear".
 * @param onDismiss invoked on Cancel / Back / outside.
 */
@Composable
fun BackgroundImageChooserDialog(
    hasImage: Boolean,
    onPickLocal: () -> Unit,
    onPickRemote: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A Stage popup with the three sources as rows: from this TV, by Remote, or clear.
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.setup_background_image),
        body = stringResource(R.string.setup_background_image_description),
        width = 760.mpx,
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.setup_from_device), onClick = onPickLocal, modifier = Modifier.focusRequester(first),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.FOLDER) },
        )
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.setup_from_phone), onClick = onPickRemote,
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.PHONE) },
        )
        if (hasImage) tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.common_clear), onClick = onClear, danger = true,
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.TRASH, tv.own.owntv.ui.theme.StageColors.Danger) },
        )
    }
}

/**
 * Remote background upload: opens the LAN companion server in image-upload mode and shows the PIN,
 * a QR of the URL, and the URL text — the same flow as Remote backup restore. When an image arrives,
 * [onImageReceived] hands the saved cache file to the host (which ingests it as the background and
 * closes this dialog). The server stops automatically when the dialog leaves composition. Private for
 * the automation API ([tv.own.owntv.features.automation.SensitiveScreenMarker]): its PIN lets a phone
 * upload a file.
 */
@Composable
fun RemoteBackgroundDialog(
    state: tv.own.owntv.core.companion.CompanionServerState,
    images: kotlinx.coroutines.flow.Flow<File>,
    onStart: (port: Int) -> Unit,
    onStop: () -> Unit,
    onImageReceived: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    tv.own.owntv.features.automation.SensitiveScreenMarker()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    // Open a fresh image-upload session on entry; stop it when the dialog leaves.
    LaunchedEffect(Unit) { onStart(tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT) }
    LaunchedEffect(images) { images.collect(onImageReceived) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onStop() } }
    val restart = { onStart(tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT) }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.setup_send_from_phone),
        body = stringResource(R.string.setup_phone_background_description),
        width = 820.mpx,
        buttons = {
            when (state) {
                is tv.own.owntv.core.companion.CompanionServerState.Failed ->
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.setup_try_again), onClick = restart, height = 56.mpx, textSize = 19)
                // Restarting mints a fresh PIN, so this is the recovery path — not a retry of a failure.
                tv.own.owntv.core.companion.CompanionServerState.Locked ->
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.setup_start_again_new_pin), onClick = restart, height = 56.mpx, textSize = 19)
                else -> Unit
            }
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(firstFocus))
        },
    ) {
        StageCompanionStatus(state)
    }
}

/**
 * The companion server's state as a Stage popup shows it: opening…, then the QR (on white, so it
 * scans) beside the PIN in large accent digits and the address to open; or what went wrong. Shared by
 * every "send it from another device" popup, so they all read the same.
 */
@Composable
internal fun StageCompanionStatus(state: tv.own.owntv.core.companion.CompanionServerState) {
    val text = tv.own.owntv.ui.theme.StageColors.Text
    val muted = tv.own.owntv.ui.theme.StageColors.Muted
    val danger = tv.own.owntv.ui.theme.StageColors.Danger
    val body = tv.own.owntv.ui.theme.stageText(18, 500)
    when (state) {
        tv.own.owntv.core.companion.CompanionServerState.Idle,
        tv.own.owntv.core.companion.CompanionServerState.Starting,
        -> Text(stringResource(R.string.setup_opening_server), style = body, color = muted)
        is tv.own.owntv.core.companion.CompanionServerState.Listening -> Row(horizontalArrangement = Arrangement.spacedBy(28.mpx), verticalAlignment = Alignment.CenterVertically) {
            state.qr?.let { qr ->
                androidx.compose.foundation.Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.common_qr_code_companion_url),
                    // White backing like the backup screens — a QR on a dark/glass panel may not scan.
                    modifier = Modifier.size(200.mpx).clip(RoundedCornerShape(16.mpx)).background(Color.White).padding(12.mpx),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            Column {
                Text(stringResource(R.string.setup_enter_pin_browser), style = body, color = muted)
                Text(
                    state.pin, style = tv.own.owntv.ui.theme.stageText(52, 800, androidx.compose.ui.unit.TextUnit(0.18f, androidx.compose.ui.unit.TextUnitType.Em)),
                    color = tv.own.owntv.ui.theme.stageAccent.accent,
                )
                Text(stringResource(R.string.setup_open_url), style = body, color = muted, modifier = Modifier.padding(top = 12.mpx))
                state.urls.forEach { url -> Text(url, style = tv.own.owntv.ui.theme.stageText(20, 700), color = text) }
            }
        }
        is tv.own.owntv.core.companion.CompanionServerState.Failed -> Text(state.failure.displayText(), style = body, color = danger)
        tv.own.owntv.core.companion.CompanionServerState.Locked -> Text(companionLockedText(), style = body, color = danger)
    }
}

/**
 * Copy a user-picked image [source] File into app-private storage under
 * [dir]/`background_<timestamp>.<ext>`, returning the new absolute path. The copy is what gets
 * persisted — the original may live on a USB stick / removable folder that could vanish, so owning
 * our own copy guarantees the background survives.
 *
 * The timestamp makes every ingest a NEW path. A fixed `background.<ext>` name meant picking a
 * second image of the same type produced an identical path: the settings Flow deduplicates, so
 * nothing recomposed, and Coil keyed its cache on the path and served the old bitmap — the
 * background only changed after an app restart.
 */
fun ingestBackgroundImage(source: File, dir: File): String {
    if (!dir.exists()) dir.mkdirs()
    // Wipe any previous ingest so the folder never accumulates old backgrounds.
    dir.listFiles()?.forEach { runCatching { it.delete() } }
    val ext = source.extension.ifBlank { "png" }.lowercase()
    val dest = File(dir, "background_${System.currentTimeMillis()}.$ext")
    source.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
    return dest.absolutePath
}
