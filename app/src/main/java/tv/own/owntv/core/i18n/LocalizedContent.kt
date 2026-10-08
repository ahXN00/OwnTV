package tv.own.owntv.core.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Provides the four Compose locale locals for the currently selected locale and triggers the one
 * documented `Activity.recreate()` across a script-family boundary.
 *
 * Verified mechanics (see `docs/internationalization.md`, "Verified mechanics") that this wrapper
 * depends on:
 *   - `stringResource` / `pluralStringResource` / `stringArrayResource` read `LocalResources.current`,
 *     not `LocalConfiguration`. Providing only `LocalConfiguration` switches nothing, so all four
 *     locals are provided here.
 *   - `LocalContext` must match `LocalResources`, because painter/vector/color resources read both —
 *     a mismatch breaks `ldrtl`-qualified drawables.
 *   - `LocalLayoutDirection` must be provided manually: it normally comes from
 *     `AndroidComposeView.onRtlPropertiesChanged` (the View system's resolved direction), which an
 *     in-composition override never touches.
 *   - `LocalLocaleList` is `@RestrictTo` and cannot be overridden, so CJK font shaping/fallback
 *     keeps following the Activity context. A same-script switch applies instantly through the
 *     locals here; a script-family change requires exactly one `Activity.recreate()`, so the new
 *     Activity wraps the base context with the new locale in `attachBaseContext`.
 */
@Composable
fun LocalizedContent(
    localeStore: LocaleStore,
    content: @Composable () -> Unit,
) {
    val hostContext = LocalContext.current
    val tag by localeStore.currentTag.collectAsStateWithLifecycle()

    // The locale already applied to this Activity's base context (set by attachBaseContext). On a
    // script-family switch the locals cannot represent the new script's shaping, so we recreate and
    // let the fresh Activity wrap with the new tag. Same-script switches update the locals instantly
    // below — far cheaper than a recreate. No picker exists in Phase 0, so this only fires once the
    // Phase 2 picker writes [tag].
    val baseContextTag = remember { localeStore.readBlocking() }
    LaunchedEffect(tag) {
        val current = tag
        if (current != baseContextTag && !sameScriptFamily(current, baseContextTag)) {
            findActivity(hostContext)?.recreate()
        }
    }

    // AppLocale.wrap() is correct for attachBaseContext, but its ContextImpl result is not a safe
    // Compose LocalContext: it loses the Activity wrapper. Keep the Activity as the base while
    // delegating resources/assets to the localized configuration context.
    // Keyed on the whole host configuration, not only the tag: the manifest's configChanges keep the
    // Activity alive across uiMode/density/screen changes, and wrap() copies the configuration at
    // call time — so without this key the screens would keep the old dark/light mode and density.
    val hostConfig = LocalConfiguration.current
    val hostLocaleKey = hostConfig.locales.toLanguageTags()
    val wrapped = remember(hostContext, tag, hostConfig) { AppLocale.wrapForCompose(hostContext, tag) }
    val layoutDirection = remember(tag, hostLocaleKey) {
        if (wrapped.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }
    val resources: Resources = wrapped.resources
    CompositionLocalProvider(
        LocalConfiguration provides resources.configuration,
        LocalContext provides wrapped,
        LocalResources provides resources,
        LocalLayoutDirection provides layoutDirection,
    ) {
        content()
    }
}

/** Unwraps a Compose `LocalContext` chain to the surrounding [Activity], or null if there is none. */
private fun findActivity(context: Context?): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Same script family? Compares ISO 15924 scripts. `Locale.forLanguageTag("ar").script` is empty
 * (a language alone defines no script), so the script is taken from `SupportedLocales` (which carries
 * the explicit `script` field from `locales.json`). The empty (system-default) tag resolves to the
 * device locale's script. For tags NOT in the catalogue (e.g. system `de-DE`, `ar-EG`, `zh-CN`),
 * `Locale.addLikelySubtags` is applied first so a language-only or language-region tag reports its
 * likely script instead of an empty one — otherwise every "System default → custom locale" transition
 * on a common system tag would unnecessarily recreate the Activity.
 */
internal fun sameScriptFamily(a: String, b: String): Boolean = scriptForTag(a) == scriptForTag(b)

internal fun scriptForTag(tag: String): String {
    val trimmed = tag.trim()
    if (trimmed.isEmpty()) {
        val list = android.content.res.Resources.getSystem().configuration.locales
        val device = if (list.isEmpty) null else list[0]
        val deviceTag = device?.toLanguageTag() ?: ""
        return SupportedLocales.scriptForTag(deviceTag) ?: likelyScript(deviceTag)
    }
    return SupportedLocales.scriptForTag(trimmed) ?: likelyScript(trimmed)
}

/**
 * The ISO 15924 script for an arbitrary BCP-47 tag via ICU likely-subtags, which fills in the
 * likely script (Arab for ar, Hans for zh, Cyrl for ru, Latn for de/fr/…). Returns "" when the tag
 * cannot be resolved, so an unknown script never equals a known one falsely. Uses
 * `android.icu.util.ULocale.addLikelySubtags`, available since API 21 (minSdk 26 here).
 */
private fun likelyScript(tag: String): String {
    if (tag.isBlank()) return ""
    return runCatching {
        val u = android.icu.util.ULocale.forLanguageTag(tag)
        android.icu.util.ULocale.addLikelySubtags(u).script
    }.getOrDefault("")
}