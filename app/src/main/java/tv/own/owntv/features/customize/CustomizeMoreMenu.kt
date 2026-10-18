package tv.own.owntv.features.customize

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R

/** The entries of the Customize screen's ⋯ menu, as (key, label) pairs. */
@Composable
internal fun customizeMoreOptions(
    hideNewCategories: Boolean,
    hasPin: Boolean,
    askForSettingsPin: Boolean,
): List<Pair<String, String>> = listOfNotNull(
    // Bulk rename over every listed category, as the items page has it (the long-press span
    // stays). In ⋯ rather than the tool row: two more pills pushed ⋯ itself off the screen.
    "BULK" to stringResource(R.string.settings_bulk_rename_title),
    "BULK_CLEANUP" to stringResource(R.string.settings_bulk_rename_auto_cleanup),
    "NEW" to stringResource(
        R.string.settings_customize_new_categories_button,
        stringResource(if (hideNewCategories) R.string.settings_customize_behavior_hide else R.string.settings_customize_behavior_show),
    ),
    if (!hasPin) "PIN_SET" to stringResource(R.string.settings_customize_set_pin)
    else "PIN_CHANGE" to stringResource(R.string.settings_customize_change_pin),
    if (hasPin) "PIN_REMOVE" to stringResource(R.string.settings_customize_remove_lock) else null,
    // Settings can ask for this same PIN; with no PIN there is nothing to ask for, so it is hidden.
    if (hasPin) {
        "SETTINGS_PIN" to stringResource(
            R.string.settings_customize_settings_pin_button,
            stringResource(if (askForSettingsPin) R.string.common_on else R.string.common_off),
        )
    } else {
        null
    },
    // Undo for hide / rename / move / reorder, for the playlist(s) this screen shows.
    CustomizeOption.RESTORE.name to stringResource(R.string.settings_customize_restore_defaults),
)

/** Key of the restore entry in the More menu (the others are the screen's existing string keys). */
internal enum class CustomizeOption { RESTORE }
