package tv.own.owntv.features.automation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A device that traded the pairing PIN for a key. Only the key's hash is kept. */
data class PairedDevice(
    val id: String,
    val name: String,
    val address: String,
    val secretHash: String,
    val pairedAt: Long,
)

data class AutomationSettings(
    val enabled: Boolean = false,
    val allowTailscale: Boolean = false,
    val devices: List<PairedDevice> = emptyList(),
    /** The first-run "Set up by Automation" page turned the API on, which goes off as the page closes. */
    val offBeforeSetup: Boolean = false,
) {
    /**
     * What a new process starts from. A visit to the setup page ends with the process, so one the last
     * process left open is over, and the API it turned on is off again ([offBeforeSetup]).
     */
    fun restarted(): AutomationSettings = if (offBeforeSetup) copy(enabled = false, offBeforeSetup = false) else this
}

/**
 * The automation API's switches and paired devices, in preferences of their own: kept out of the
 * app's backups on purpose, so restoring a backup on another box never restores working keys.
 * There is one store for the process, loaded as it starts ([AutomationSettings.restarted]).
 */
class AutomationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load().restarted())
    val settings: StateFlow<AutomationSettings> = _settings.asStateFlow()

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun setAllowTailscale(on: Boolean) = update { it.copy(allowTailscale = on) }
    fun setOffBeforeSetup(on: Boolean) = update { it.copy(offBeforeSetup = on) }
    fun addDevice(device: PairedDevice) = update { it.copy(devices = it.devices + device) }
    fun removeDevice(id: String) = update { s -> s.copy(devices = s.devices.filterNot { it.id == id }) }
    fun removeAllDevices() = update { it.copy(devices = emptyList()) }

    @Synchronized
    private fun update(change: (AutomationSettings) -> AutomationSettings) {
        val next = change(_settings.value)
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putBoolean(KEY_TAILSCALE, next.allowTailscale)
            .putString(KEY_DEVICES, PairedDeviceCodec.encode(next.devices))
            .putBoolean(KEY_OFF_BEFORE_SETUP, next.offBeforeSetup)
            .apply()
        _settings.value = next
    }

    private fun load() = AutomationSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        allowTailscale = prefs.getBoolean(KEY_TAILSCALE, false),
        devices = PairedDeviceCodec.decode(prefs.getString(KEY_DEVICES, null)),
        offBeforeSetup = prefs.getBoolean(KEY_OFF_BEFORE_SETUP, false),
    )

    private companion object {
        const val PREFS = "automation_api"
        const val KEY_ENABLED = "enabled"
        const val KEY_TAILSCALE = "allow_tailscale"
        const val KEY_DEVICES = "devices"
        const val KEY_OFF_BEFORE_SETUP = "off_before_setup"
    }
}

internal object PairedDeviceCodec {
    fun encode(devices: List<PairedDevice>): String = JSONArray().apply {
        devices.forEach { d ->
            put(
                JSONObject()
                    .put("id", d.id).put("name", d.name).put("address", d.address)
                    .put("hash", d.secretHash).put("pairedAt", d.pairedAt),
            )
        }
    }.toString()

    /** A damaged entry is dropped, never guessed at: losing a pairing is safe, a wrong one is not. */
    fun decode(raw: String?): List<PairedDevice> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val hash = o.optString("hash")
            val address = o.optString("address")
            if (hash.length != 64 || address.isEmpty()) return@mapNotNull null
            PairedDevice(o.optString("id"), o.optString("name"), address, hash, o.optLong("pairedAt"))
        }
    }
}
