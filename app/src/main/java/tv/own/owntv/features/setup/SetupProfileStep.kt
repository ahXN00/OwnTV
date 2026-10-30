package tv.own.owntv.features.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVAvatars
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Who's watching?: Name, Avatar (◀ ▶ through the pictures, the letter first), a picture of the
 * user's own, Kids profile and an optional PIN, then Create profile. The picture is held until then:
 * the profile has no id to store it under before it exists. Private for the automation API
 * ([SensitiveScreenMarker]): its keys must not create a profile or type its PIN.
 */
@Composable
internal fun ProfileStep(onCreate: (name: String, avatarId: Int, isKids: Boolean, pin: String?, picture: java.io.File?) -> Unit, onBack: () -> Unit) {
    SensitiveScreenMarker()
    var name by remember { mutableStateOf("") }
    var avatarId by remember { mutableIntStateOf(-1) } // new profiles start on the letter
    var isKids by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var picture by remember { mutableStateOf<java.io.File?>(null) }
    var choosingPicture by remember { mutableStateOf(false) }
    val nameFocus = rememberFirstFocus()
    val valid = name.isNotBlank() && (pin.isEmpty() || pin.length >= 4)
    val nameLabel = stringResource(R.string.profiles_name)
    val pinLabel = stringResource(R.string.profiles_optional_pin)
    val fieldHelp = stringResource(R.string.settings_form_field_help)
    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.profiles_gate_title),
        sub = stringResource(R.string.setup_profile_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_create_profile), { if (valid) onCreate(name, avatarId, isKids, pin.takeIf { it.isNotBlank() }, picture) }, enabled = valid),
    ) {
        StageFieldRow(
            OwnTVIcon.PENCIL, nameLabel, name, { name = it }, SettingHelp(nameLabel, fieldHelp),
            placeholder = stringResource(R.string.profiles_name_hint), modifier = Modifier.focusRequester(nameFocus),
        )
        val ids = (-1 until OwnTVAvatars.COUNT).toList()
        val at = ids.indexOf(avatarId).coerceAtLeast(0)
        StageSettingRow(
            icon = OwnTVIcon.PERSON,
            title = stringResource(R.string.setup_avatar),
            desc = stringResource(R.string.setup_avatar_line),
            value = SettingValue.Custom { AvatarStrip(ids, avatarId) },
            onClick = { avatarId = ids[(at + 1) % ids.size] },
            onStep = { dir -> avatarId = ids[(at + dir + ids.size) % ids.size] },
        )
        StageSettingRow(
            icon = OwnTVIcon.IMAGE,
            title = stringResource(R.string.profiles_avatar_own_picture),
            desc = null,
            value = picture?.let { file -> SettingValue.Custom { OwnTVAvatar(avatarId = avatarId, imagePath = file.absolutePath, modifier = Modifier.size(34.mpx)) } },
            onClick = { choosingPicture = true },
        )
        StageSettingRow(
            icon = OwnTVIcon.FAVORITE,
            title = stringResource(R.string.profiles_kids),
            desc = stringResource(R.string.profiles_kids_description),
            value = SettingValue.Switch(isKids),
            onClick = { isKids = !isKids },
        )
        StageFieldRow(
            OwnTVIcon.PENCIL, pinLabel, pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, SettingHelp(pinLabel, fieldHelp),
            placeholder = stringResource(R.string.profiles_pin_digits), password = true, keyboardType = KeyboardType.NumberPassword,
        )
    }
    if (choosingPicture) {
        tv.own.owntv.features.profiles.ProfilePictureChooser(
            hasImage = picture != null,
            onPicked = { picture = it; choosingPicture = false },
            onClear = { picture = null; choosingPicture = false },
            onDismiss = { choosingPicture = false },
        )
    }
}

/** The avatar row's value: six pictures from the chosen one on, the chosen ringed, then "+N". */
@Composable
private fun AvatarStrip(ids: List<Int>, chosen: Int) {
    val a = stageAccent
    val start = ids.indexOf(chosen).coerceAtLeast(0)
    val shown = List(6.coerceAtMost(ids.size)) { ids[(start + it) % ids.size] }
    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { id ->
            Box(
                Modifier.size(34.mpx).then(if (id == chosen) Modifier.border(2.mpx, a.accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { OwnTVAvatar(avatarId = id, modifier = Modifier.size(30.mpx)) }
        }
        if (ids.size > shown.size) Text("+" + localizedInteger(ids.size - shown.size, grouping = false), style = stageText(17, 700), color = StageColors.Muted)
    }
}
