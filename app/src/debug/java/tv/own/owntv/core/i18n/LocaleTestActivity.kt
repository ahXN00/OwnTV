package tv.own.owntv.core.i18n

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** Debug-only host used to verify that Compose LocalContext preserves Activity semantics. */
class LocaleTestActivity : ComponentActivity() {
    @Volatile
    var localizedContext: Context? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("localized_context_test", MODE_PRIVATE)
        prefs.edit { putString("ui_language", "en-US") }
        val store = LocaleStore.overPreferences(prefs)
        setContent {
            LocalizedContent(store) {
                localizedContext = LocalContext.current
                Box {}
            }
        }
    }
}
