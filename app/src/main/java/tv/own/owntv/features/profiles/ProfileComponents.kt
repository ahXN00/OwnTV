package tv.own.owntv.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVAvatars
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/** Modal scrim wrapper for the profile dialogs. Phase 7 — Popup(focusable=true) creates
 *  a hard focus boundary on Android TV so D-pad stays inside the dialog. */
@Composable
internal fun ProfileScrim(
    onDismiss: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 480.dp,
    padding: androidx.compose.ui.unit.Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .modalScrim()
                .trapAllFocusExit()
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            // Scrollable so small/low-res screens can still reach the lower controls (Kids
            // toggle / PIN / Create were clipped and unreachable on a cut-off screen).
            Column(
                modifier = Modifier.dialogPanel(width = width, padding = padding),
            ) { content() }
        }
    }
}

/** Numeric PIN entry. Calls [onSubmit] with the entered digits. [compact] renders the small
 *  popup-menu treatment (narrow panel, Caladea font) used by the Customize screen. */
@Composable
internal fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean = false) {
    val dialog: @Composable () -> Unit = { PinDialogBody(title, onSubmit, onDismiss, compact) }
    if (compact) tv.own.owntv.ui.theme.PopupFontTheme(content = dialog) else dialog()
}

@Composable
private fun PinDialogBody(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean) {
    val colors = OwnTVTheme.colors
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ProfileScrim(onDismiss, width = if (compact) 290.dp else 480.dp, padding = if (compact) 16.dp else 28.dp) {
        Text(title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        OwnTVTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = stringResource(R.string.profiles_pin),
            placeholder = stringResource(R.string.profiles_pin_placeholder),
            keyboardType = KeyboardType.NumberPassword,
            isPassword = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(20.dp))
        // Explicit start/end links: spatial D-pad search from Cancel would otherwise wander into
        // the gate's profile tiles BEHIND the dialog before reaching OK.
        val cancelFocus = remember { FocusRequester() }
        val okFocus = remember { FocusRequester() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(
                stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(cancelFocus).focusProperties { end = okFocus },
            )
            Spacer(Modifier.weight(1f))
            OwnTVButton(
                stringResource(R.string.common_ok), onClick = { onSubmit(pin) }, enabled = pin.length >= 4,
                modifier = Modifier.focusRequester(okFocus).focusProperties { start = cancelFocus },
            )
        }
    }
}

/**
 * Create / edit a profile: name, avatar, kids flag and an optional PIN. [initial] non-null = edit.
 * [onConfirm] receives (name, avatarId, isKids, pin): null = leave the PIN unchanged,
 * "" = remove the PIN lock, otherwise = set this PIN.
 *
 * [takenNames] are the OTHER profiles' names (lowercased) — profile names must be unique (they're the
 * merge key for backup restore), so a collision blocks Create/Save with an inline error.
 */
@Composable
internal fun ProfileEditorDialog(
    initial: ProfileEntity?,
    onConfirm: (name: String, avatarId: Int, isKids: Boolean, pin: String?) -> Unit,
    onDismiss: () -> Unit,
    takenNames: Set<String> = emptySet(),
) {
    val colors = OwnTVTheme.colors
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatarId by remember { mutableIntStateOf(initial?.avatarId ?: -1) } // Phase 7 — new profiles default to no-avatar
    var isKids by remember { mutableStateOf(initial?.isKids ?: false) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val nameTaken = name.trim().isNotEmpty() && name.trim().lowercase() in takenNames

    ProfileScrim(onDismiss) {
        Text(stringResource(if (initial == null) R.string.profiles_new else R.string.profiles_edit), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(16.dp))
        OwnTVTextField(name, { name = it }, label = stringResource(R.string.profiles_name), placeholder = stringResource(R.string.profiles_name_hint), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        if (nameTaken) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.profiles_name_taken),
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444),
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.profiles_avatar), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Count + key instead of a materialized range: no list per recomposition, and an
            // avatar keeps its composition across avatar-count changes. -1 is "no avatar", first.
            items(count = OwnTVAvatars.COUNT + 1, key = { it - 1 }) { index ->
                val id = index - 1 // Phase 7 — includes "no avatar" (-1)
                FocusableSurface(
                    onClick = { avatarId = id },
                    modifier = Modifier.size(60.dp),
                    selected = id == avatarId,
                    shape = CircleShape,
                    selectedContainerColor = colors.primaryContainer,
                    contentAlignment = Alignment.Center,
                    surface = GlassSurface.DIALOGS,
                ) { _ ->
                    OwnTVAvatar(avatarId = id, modifier = Modifier.size(48.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        ToggleRow(label = stringResource(R.string.profiles_kids), desc = stringResource(R.string.profiles_kids_description), checked = isKids) { isKids = it }
        Spacer(Modifier.height(12.dp))
        if (initial?.pinHash != null) {
            ToggleRow(label = stringResource(R.string.profiles_remove_pin), desc = stringResource(R.string.profiles_no_pin), checked = removePin) { removePin = it }
            Spacer(Modifier.height(12.dp))
        }
        if (!removePin) {
            OwnTVTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = if (initial?.pinHash != null) stringResource(R.string.profiles_change_pin) else stringResource(R.string.profiles_optional_pin),
                placeholder = stringResource(R.string.profiles_pin_digits),
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            OwnTVButton(
                label = stringResource(if (initial == null) R.string.profiles_create else R.string.profiles_save),
                onClick = { onConfirm(name, avatarId, isKids, if (removePin) "" else pin.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && !nameTaken && (removePin || pin.isEmpty() || pin.length >= 4),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { onToggle(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.width(52.dp).height(30.dp).clip(CircleShape)
                    .background(if (checked) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}
