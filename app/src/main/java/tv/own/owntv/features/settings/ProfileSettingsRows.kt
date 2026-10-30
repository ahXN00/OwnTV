package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.profiles.ProfileEditorDialog
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

private const val ADD_ROW = -1L

/** How many profiles Settings › Profile lists, for the page's count. */
@Composable
internal fun profileCount(): Int {
    val vm: ProfilesViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    return profiles.size
}

/**
 * Settings › Profile: the profiles themselves, then Add a profile. OK edits a profile (the profile
 * editor), a held OK offers to delete it while more than one exists. The panel explains
 * profiles as a whole.
 */
@Composable
internal fun ProfileSettingsRows() {
    val vm: ProfilesViewModel = koinViewModel()
    val gateSession: ProfileGateSessionViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeProfileId by vm.activeProfileId.collectAsStateWithLifecycle()
    val defaultProfileName = stringResource(R.string.profiles_default_name)
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProfileEntity?>(null) }
    // A closing editor or confirmation hands focus back to the row it came from (Add a profile, or the
    // profile — its neighbour when it was deleted).
    val rowFocus = remember { HashMap<Long, FocusRequester>() }
    val addFocus = remember { FocusRequester() }
    var returnTo by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(editing, creating, confirmDelete, profiles) {
        if (editing != null || creating || confirmDelete != null) return@LaunchedEffect
        val id = returnTo ?: return@LaunchedEffect
        withFrameNanos { }
        val target = rowFocus[id].takeIf { profiles.any { it.id == id } } ?: profiles.lastOrNull()?.let { rowFocus[it.id] } ?: addFocus
        if (id == ADD_ROW) runCatching { addFocus.requestFocus() } else runCatching { target.requestFocus() }
        returnTo = null
    }

    val help = SettingHelp(
        title = stringResource(R.string.profiles_title),
        text = stringResource(R.string.settings_help_profiles),
        hints = listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.common_edit),
            stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
        ),
    )
    val sep = stringResource(R.string.content_epg_bits_separator)
    profiles.forEach { p ->
        val inUse = p.id == activeProfileId
        val line = listOfNotNull(
            if (inUse) stringResource(R.string.settings_profile_in_use) else null,
            if (p.isKids) stringResource(R.string.settings_profile_kids) else null,
            stringResource(if (p.pinHash != null) R.string.settings_profile_pin_lock else R.string.settings_profile_no_lock),
        ).joinToString(sep)
        val inUseTag = stringResource(R.string.settings_profile_in_use).uppercase()
        val kidsTag = stringResource(R.string.profiles_kids_tag).uppercase()
        val pinTag = stringResource(R.string.profiles_pin).uppercase()
        val accent = stageAccent.accent
        StageSettingRow(
            icon = OwnTVIcon.PERSON,
            title = p.name,
            desc = line,
            value = SettingValue.Custom {
                Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                    if (inUse) StageTag(inUseTag, tint = accent)
                    if (p.isKids) StageTag(kidsTag)
                    if (p.pinHash != null) StageTag(pinTag)
                    if (!inUse) OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(20.mpx))
                }
            },
            onClick = { returnTo = p.id; editing = p },
            onLongClick = if (profiles.size > 1) ({ returnTo = p.id; confirmDelete = p }) else null,
            help = help,
            modifier = Modifier.focusRequester(remember(p.id) { rowFocus.getOrPut(p.id) { FocusRequester() } }),
        )
    }
    StageSettingRow(
        icon = OwnTVIcon.SPARKLE,
        title = stringResource(R.string.settings_profile_add),
        desc = stringResource(R.string.settings_profile_add_line),
        value = null,
        onClick = { returnTo = ADD_ROW; creating = true },
        help = help,
        modifier = Modifier.focusRequester(addFocus),
    )

    if (creating) {
        ProfileEditorDialog(
            initial = null,
            onConfirm = { name, avatarId, isKids, pin, picture -> vm.create(name, avatarId, isKids, pin, defaultProfileName, picture); creating = false },
            onDismiss = { creating = false },
            // Names must stay unique (backup restore matches profiles by name).
            takenNames = profiles.map { it.name.trim().lowercase() }.toSet(),
        )
    }
    editing?.let { p ->
        ProfileEditorDialog(
            initial = p,
            onConfirm = { name, avatarId, isKids, pin, _ -> vm.edit(p, name, avatarId, isKids, pin); editing = null },
            onDismiss = { editing = null },
            takenNames = profiles.filter { it.id != p.id }.map { it.name.trim().lowercase() }.toSet(),
            // From the live list, not [p]: [p] is the row as it was when the dialog opened, and a
            // picture chosen here applies at once.
            customPath = profiles.firstOrNull { it.id == p.id }?.avatarPath.orEmpty(),
            onPickPicture = { file -> vm.setPicture(p.id, file) },
            onClearPicture = { vm.clearPicture(p.id) },
        )
    }
    confirmDelete?.let { p ->
        // Private for the automation API: its keys must not delete a profile (ProfileEditorDialog marks itself).
        SensitiveScreenMarker()
        ConfirmDialog(
            title = stringResource(R.string.profiles_delete_title, p.name),
            message = stringResource(R.string.profiles_delete_message),
            onConfirm = {
                // Only an active-profile deletion changes the identity this Activity authenticated.
                gateSession.invalidateIfDeletingActiveProfile(p.id, activeProfileId)
                vm.delete(p)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}
