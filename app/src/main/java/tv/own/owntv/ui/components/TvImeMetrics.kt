package tv.own.owntv.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/** Keyboard geometry in physical display pixels. TV IMEs frequently expose only one usable signal,
 * so [TvImeWatcher] combines modern insets, the legacy visible frame, and a calibrated fallback. */
@Immutable
data class TvImeMetrics(
    val displayHeightPx: Int = 0,
    val visible: Boolean = false,
    val keyboardTopPx: Int = displayHeightPx,
    val obscuredBottomPx: Int = 0,
    val source: Source = Source.NONE,
) {
    enum class Source { NONE, IME_INSET, VISIBLE_FRAME, ESTIMATE }
}

internal object TvImeDefaults {
    // Used only when the OEM publishes neither an IME inset nor a visible-frame change. A real
    // measurement is persisted and replaces this estimate on subsequent openings.
    const val FALLBACK_KEYBOARD_FRACTION = 0.45f
    const val POLL_INTERVAL_MS = 100L
    const val POLL_DURATION_MS = 2_000L
    const val TAG = "OwnTV-IME"
}

@Stable
internal class TvImeWatcher(private val hostView: View) {
    var metrics by mutableStateOf(TvImeMetrics())
        private set
    var imeRequested by mutableStateOf(false)
        private set

    private val prefs = hostView.context.applicationContext.getSharedPreferences(
        "owntv_ime_calibration",
        Context.MODE_PRIVATE,
    )
    private val rect = Rect()
    private var baselineVisibleBottom = -1
    private var attached = false
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { recompute("layout") }

    fun attach() {
        if (attached) return
        attached = true
        hostView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        ViewCompat.setOnApplyWindowInsetsListener(hostView) { _, insets ->
            recompute("insets")
            insets // observe only; do not consume Compose's inset stream
        }
        hostView.post {
            captureBaseline()
            recompute("attach")
            ViewCompat.requestApplyInsets(hostView)
        }
    }

    fun detach() {
        if (!attached) return
        attached = false
        if (hostView.viewTreeObserver.isAlive) {
            hostView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        }
        ViewCompat.setOnApplyWindowInsetsListener(hostView, null)
    }

    fun onImeRequested() {
        // Capture the unobstructed frame synchronously before keyboard.show() can alter it.
        captureBaseline()
        imeRequested = true
        recompute("requested")
    }

    fun onImeDismissed() {
        imeRequested = false
        recompute("dismissed")
        // Let the TV IME finish its hide animation before refreshing the unobstructed baseline.
        hostView.postDelayed({ captureBaseline(); recompute("hidden") }, 350L)
    }

    fun poll() = recompute("poll")

    private fun captureBaseline() {
        if (imeRequested || hostView.height <= 0) return
        hostView.getWindowVisibleDisplayFrame(rect)
        if (rect.bottom > 0) baselineVisibleBottom = maxOf(baselineVisibleBottom, rect.bottom)
    }

    private fun recompute(trigger: String) {
        val displayHeight = maxOf(
            hostView.resources.displayMetrics.heightPixels,
            hostView.rootView.height,
            hostView.height,
        )
        if (displayHeight <= 0) return

        val insets = ViewCompat.getRootWindowInsets(hostView)
        val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val imeInsetVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true

        hostView.getWindowVisibleDisplayFrame(rect)
        if (!imeRequested && !imeInsetVisible) captureBaseline()
        val frameObscured = if (baselineVisibleBottom > 0 && rect.bottom > 0) {
            (baselineVisibleBottom - rect.bottom).coerceAtLeast(0)
        } else 0

        val minKeyboard = (displayHeight * 0.11f).roundToInt().coerceAtLeast(64)
        val measured = when {
            imeInsetVisible && imeBottom >= minKeyboard -> imeBottom to TvImeMetrics.Source.IME_INSET
            frameObscured >= minKeyboard -> frameObscured to TvImeMetrics.Source.VISIBLE_FRAME
            else -> null
        }
        if (measured != null) {
            prefs.edit { putInt(KEY_CALIBRATED_HEIGHT, measured.first) }
        }

        val obstruction = measured?.first ?: if (imeRequested) {
            prefs.getInt(KEY_CALIBRATED_HEIGHT, 0).takeIf { it >= minKeyboard }
                ?: (displayHeight * TvImeDefaults.FALLBACK_KEYBOARD_FRACTION).roundToInt()
        } else 0
        val clamped = obstruction.coerceIn(0, (displayHeight * 0.70f).roundToInt())
        val visible = clamped >= minKeyboard && (imeRequested || imeInsetVisible || measured != null)
        val next = TvImeMetrics(
            displayHeightPx = displayHeight,
            visible = visible,
            keyboardTopPx = if (visible) displayHeight - clamped else displayHeight,
            obscuredBottomPx = if (visible) clamped else 0,
            source = if (!visible) TvImeMetrics.Source.NONE
            else measured?.second ?: TvImeMetrics.Source.ESTIMATE,
        )
        if (next != metrics) {
            metrics = next
            Log.d(
                TvImeDefaults.TAG,
                "$trigger visible=${next.visible} source=${next.source} displayH=$displayHeight " +
                    "keyboardTop=${next.keyboardTopPx} obscured=${next.obscuredBottomPx} " +
                    "frame=$rect imeBottom=$imeBottom",
            )
        }
    }

    private companion object {
        const val KEY_CALIBRATED_HEIGHT = "keyboard_height_px"
    }
}

internal val LocalTvImeWatcher = compositionLocalOf<TvImeWatcher?> { null }
internal val LocalTvImeMetrics = compositionLocalOf { TvImeMetrics() }
