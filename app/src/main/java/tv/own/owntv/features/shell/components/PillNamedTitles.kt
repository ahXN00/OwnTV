package tv.own.owntv.features.shell.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.LockedNotificationTitles
import tv.own.owntv.core.download.DownloadActivityTracker
import tv.own.owntv.core.recording.RecordingActivityTracker
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.categorylock.namedIds
import tv.own.owntv.core.categorylock.sealedCategoryKeys

/** The running download and recordings the status pill may name; the others it labels as locked. */
internal data class PillNamedTitles(val downloads: Set<Long> = emptySet(), val recordings: Set<Long> = emptySet())

/**
 * Which running download and recordings [SyncStatusPill] may name. The notifications hide a locked
 * category's titles in every session; the pill is inside the app, behind the PIN, so it names a title
 * once the PIN has opened its category, and hides it again when the category is sealed.
 */
@Composable
internal fun rememberPillNamedTitles(downloads: DownloadActivityTracker, recordings: RecordingActivityTracker): PillNamedTitles {
    val customize: CustomizationStore = koinInject()
    val settings: SettingsRepository = koinInject()
    val titles: LockedNotificationTitles = koinInject()
    val named = remember(downloads, recordings, customize, settings, titles) {
        val locks = customize.sealedCategoryKeys(settings.activeProfileId)
        combine(
            namedIds(downloads.active.map { setOfNotNull(it?.id) }, locks) { titles.hidesDownload(it, anySession = false) },
            namedIds(recordings.active.map { it.keys }, locks) { titles.hidesRecording(it, anySession = false) },
        ) { d, r -> PillNamedTitles(d, r) }
    }
    val value by named.collectAsStateWithLifecycle(PillNamedTitles())
    return value
}
