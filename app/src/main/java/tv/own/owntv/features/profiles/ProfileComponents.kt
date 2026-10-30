package tv.own.owntv.features.profiles

import androidx.compose.foundation.border
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.StageColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVAvatars

/** The profile dialogs' Stage popup: [title], [content], [buttons] bottom right. It scrolls, so small
 *  screens still reach the lower controls; the popup window keeps D-pad focus inside. */
@Composable
internal fun ProfileScrim(
    onDismiss: () -> Unit,
    title: String,
    width: androidx.compose.ui.unit.Dp = 864.mpx,
    buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, eyebrow = null, width = width, buttons = buttons, content = content)
}

/** Numeric PIN entry. Calls [onSubmit] with the entered digits. [compact] renders the small
 *  popup-menu treatment (narrow panel, Caladea font) used by the Customize screen. Private for the
 *  automation API while open ([SensitiveScreenMarker]): its keys cannot type a PIN here. */
@Composable
internal fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean = false) {
    SensitiveScreenMarker()
    val dialog: @Composable () -> Unit = { PinDialogBody(title, onSubmit, onDismiss, compact) }
    if (compact) tv.own.owntv.ui.theme.PopupFontTheme(content = dialog) else dialog()
}

@Composable
private fun PinDialogBody(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Explicit start/end links: spatial D-pad search from Cancel would otherwise wander into
    // the gate's profile tiles BEHIND the dialog before reaching OK.
    val cancelFocus = remember { FocusRequester() }
    val okFocus = remember { FocusRequester() }
    ProfileScrim(
        onDismiss, title = title, width = if (compact) 560.mpx else 700.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocus).focusProperties { end = okFocus },
                primary = false,
            )
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.common_ok),
                onClick = { onSubmit(pin) },
                modifier = Modifier.focusRequester(okFocus).focusProperties { start = cancelFocus },
                enabled = pin.length >= 4,
            )
        },
    ) {
        tv.own.owntv.features.settings.StageFieldRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
            label = stringResource(R.string.profiles_pin),
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = stringResource(R.string.profiles_pin_placeholder),
            password = true,
            keyboardType = KeyboardType.NumberPassword,
        )
    }
}

/**
 * Create / edit a profile: name, avatar, kids flag and an optional PIN. [initial] non-null = edit.
 * [onConfirm] receives (name, avatarId, isKids, pin): null = leave the PIN unchanged,
 * "" = remove the PIN lock, otherwise = set this PIN.
 *
 * [takenNames] are the OTHER profiles' names (lowercased) — profile names must be unique (they're the
 * merge key for backup restore), so a collision blocks Create/Save with an inline error.
 *
 * Private for the automation API while open ([SensitiveScreenMarker]): editing does not ask for the old
 * PIN, so its keys could otherwise remove another profile's PIN or turn Kids off.
 *
 * A picture of the user's own: on an existing profile [onPickPicture] / [onClearPicture] apply it at
 * once, as the phone does, and [customPath] is the one it has now. A new profile has no id to store
 * one under yet, so the dialog holds the chosen file and hands it to [onConfirm] with the rest.
 */
@Composable
internal fun ProfileEditorDialog(
    initial: ProfileEntity?,
    onConfirm: (name: String, avatarId: Int, isKids: Boolean, pin: String?, picture: java.io.File?) -> Unit,
    onDismiss: () -> Unit,
    takenNames: Set<String> = emptySet(),
    customPath: String = "",
    onPickPicture: ((java.io.File) -> Unit)? = null,
    onClearPicture: (() -> Unit)? = null,
) {
    SensitiveScreenMarker()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatarId by remember { mutableIntStateOf(initial?.avatarId ?: -1) } // new profiles default to no avatar
    var isKids by remember { mutableStateOf(initial?.isKids ?: false) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    var pendingPicture by remember { mutableStateOf<java.io.File?>(null) }
    var choosingPicture by remember { mutableStateOf(false) }
    val picturePath = pendingPicture?.absolutePath ?: customPath
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val nameTaken = name.trim().isNotEmpty() && name.trim().lowercase() in takenNames

    ProfileScrim(
        onDismiss,
        title = stringResource(if (initial == null) R.string.profiles_new else R.string.profiles_edit),
        buttons = {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = onDismiss, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(if (initial == null) R.string.profiles_create else R.string.profiles_save),
                onClick = { onConfirm(name, avatarId, isKids, if (removePin) "" else pin.takeIf { it.isNotBlank() }, pendingPicture) },
                enabled = name.isNotBlank() && !nameTaken && (removePin || pin.isEmpty() || pin.length >= 4),
            )
        },
    ) {
        tv.own.owntv.features.settings.StageFieldRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
            label = stringResource(R.string.profiles_name),
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = stringResource(R.string.profiles_name_hint),
        )
        if (nameTaken) {
            Text(
                stringResource(R.string.profiles_name_taken),
                style = stageText(17, 600), color = StageColors.Danger,
                modifier = Modifier.padding(top = 8.mpx),
            )
        }
        tv.own.owntv.ui.stage.StagePopupLabel(stringResource(R.string.profiles_avatar).uppercase(), Modifier.padding(top = 18.mpx))
        val accent = stageAccent.accent
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.mpx), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.mpx)) {
            items((-1 until OwnTVAvatars.COUNT).toList()) { id -> // includes "no avatar" (-1)
                tv.own.owntv.ui.stage.StageSurface(
                    onClick = { avatarId = id },
                    radius = 44.mpx,
                    modifier = Modifier.size(88.mpx),
                    focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                    idle = if (id == avatarId) Modifier.border(3.mpx, accent, CircleShape) else Modifier,
                    contentAlignment = Alignment.Center,
                ) { _ ->
                    OwnTVAvatar(avatarId = id, modifier = Modifier.size(70.mpx))
                }
            }
        }
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.profiles_avatar_own_picture),
            onClick = { choosingPicture = true },
            leading = {
                if (picturePath.isNotBlank()) OwnTVAvatar(avatarId = avatarId, imagePath = picturePath, modifier = Modifier.size(44.mpx))
                else tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.IMAGE)
            },
        )
        tv.own.owntv.ui.stage.StagePopupDivider()
        ToggleRow(label = stringResource(R.string.profiles_kids), desc = stringResource(R.string.profiles_kids_description), checked = isKids) { isKids = it }
        if (initial?.pinHash != null) {
            ToggleRow(label = stringResource(R.string.profiles_remove_pin), desc = stringResource(R.string.profiles_no_pin), checked = removePin) { removePin = it }
        }
        if (!removePin) {
            Spacer(Modifier.height(14.mpx))
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = if (initial?.pinHash != null) stringResource(R.string.profiles_change_pin) else stringResource(R.string.profiles_optional_pin),
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.profiles_pin_digits),
                password = true,
                keyboardType = KeyboardType.NumberPassword,
            )
        }
    }
    if (choosingPicture) {
        ProfilePictureChooser(
            hasImage = picturePath.isNotBlank(),
            onPicked = { file ->
                if (onPickPicture != null) onPickPicture(file) else pendingPicture = file
                choosingPicture = false
            },
            onClear = {
                if (onClearPicture != null) onClearPicture() else pendingPicture = null
                choosingPicture = false
            },
            onDismiss = { choosingPicture = false },
        )
    }
}

/** `.opt` with a switch on the right. */
@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    tv.own.owntv.ui.stage.StagePopupOption(
        title = label, subtitle = desc, onClick = { onToggle(!checked) },
        trailing = { tv.own.owntv.ui.stage.StageSwitch(checked) },
    )
}
