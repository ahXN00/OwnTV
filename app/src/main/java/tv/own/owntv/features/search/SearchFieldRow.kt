package tv.own.owntv.features.search

import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.mpx

/**
 * Search's field, with the voice-search microphone on its right where the device has a speech
 * recogniser. What the user says becomes the query, so it searches exactly as typing it would.
 * ▼ from either goes to [focusDown].
 */
@Composable
internal fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    fieldFocus: FocusRequester,
    focusDown: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
        StageSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.search_field_hint),
            modifier = Modifier
                .weight(1f)
                .focusRequester(fieldFocus)
                .focusProperties { down = focusDown },
        )
        VoiceSearchButton(onQuery = onQueryChange, modifier = Modifier.focusProperties { down = focusDown })
    }
}

/** The 48-high microphone; absent when no recogniser answers, or once one has refused to start. */
@Composable
private fun VoiceSearchButton(onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var available by remember(context) { mutableStateOf(VoiceSearch.isAvailable(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        VoiceSearch.spokenQuery(result.resultCode, matches)?.let(onQuery)
    }
    if (!available) return
    val label = stringResource(R.string.search_voice)
    val prompt = stringResource(R.string.search_voice_prompt)
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    StageTool(
        text = null,
        icon = OwnTVIcon.MIC,
        boxed = true,
        onClick = {
            // Resolving is not a promise: the recogniser can be disabled between the check and OK.
            try {
                launcher.launch(VoiceSearch.intent(prompt, language))
            } catch (_: ActivityNotFoundException) {
                available = false
            }
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}
