package tv.own.owntv.features.automation

import android.util.Log
import org.json.JSONObject

/**
 * The ui area: what is on the TV, from what the shell publishes ([AutomationUiState]), and opening a
 * section through [NavGate], which brings OwnTV forward first when it is in the background.
 */
class AutomationUi(
    private val launcher: AppLauncher,
    private val foreground: AutomationForeground,
    private val sensitive: SensitiveScreens,
    private val uiState: AutomationUiState,
    private val profile: AutomationProfile,
    private val gate: NavGate,
) : UiBackend {

    override suspend fun state(): JSONObject {
        val screenOn = launcher.screenOn()
        return UiJson.reduced(inFront = foreground.activity() != null, screenOn = screenOn, privateOpen = sensitive.anyOpen)
            ?: UiJson.full(screenOn, uiState.current.value, profile.chosenPlaylist())
    }

    /** Refused while the API sees no open profile ([AutomationProfile.id]): there is none to open it in. */
    override suspend fun navigate(target: NavTarget): NavResult {
        val pid = profile.id() ?: return NavResult.NO_PROFILE
        Log.d(TAG, "navigate to=${target.apiName}")
        return gate.section(target, pid, launcher.opened(target.section.labelRes))
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
