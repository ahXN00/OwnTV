package tv.own.owntv.features.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Voice search through the system's speech recogniser ([RecognizerIntent]). The recogniser app does the
 * listening, so OwnTV needs no microphone permission. Fire TV and many boxes ship no recogniser at all,
 * so the microphone shows only where one answers the intent.
 */
internal object VoiceSearch {

    /** The recogniser request: free-form speech, one best guess first, in the app's language. */
    fun intent(prompt: String, languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

    /** Whether any installed app answers the recogniser intent. */
    fun isAvailable(context: Context): Boolean = isAvailable {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }

    /**
     * [resolves] asks the package manager. Some OEM builds throw from it instead of answering; that
     * counts as "no recogniser", so a broken box loses the button, never the screen.
     */
    fun isAvailable(resolves: () -> Boolean): Boolean =
        try {
            resolves()
        } catch (_: RuntimeException) {
            false
        }

    /**
     * The search text from the recogniser's answer: its first non-blank guess, trimmed and with runs of
     * spaces collapsed. Null when the user backed out, nothing was heard, or the answer was empty.
     */
    fun spokenQuery(resultCode: Int, matches: List<String?>?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return matches.orEmpty()
            .firstNotNullOfOrNull { it?.trim()?.replace(Whitespace, " ")?.takeIf(String::isNotEmpty) }
    }

    private const val MAX_RESULTS = 5
    private val Whitespace = Regex("\\s+")
}
