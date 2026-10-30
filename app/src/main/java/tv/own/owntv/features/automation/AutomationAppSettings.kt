package tv.own.owntv.features.automation

import android.util.Log
import org.json.JSONObject
import tv.own.owntv.core.settings.SettingsRepository

/** App settings for the automation API, through the backup export and import the app already has. */
class AutomationAppSettings(private val settings: SettingsRepository) : SettingsBackend {

    override suspend fun export(): JSONObject = settings.exportSettings()

    override suspend fun import(changes: JSONObject) {
        val result = settings.importSettings(changes, keepDeviceSettings = false)
        // importSettings leaves the language to its caller, as a backup restore applies it last.
        if (result.localePresent && !result.invalidLocale) result.localeTag?.let { settings.applyImportedLocale(it) }
        Log.d(TAG, "settings changed: ${changes.keys().asSequence().joinToString()}")
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
