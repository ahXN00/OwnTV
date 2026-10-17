package tv.own.owntv.features.live

import androidx.compose.runtime.Immutable
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.core.live.LiveKey

/** A rail entry. Favorites/History carry an [icon] rendered inline before the title. */
@Immutable
data class LiveRailItem(
    val key: LiveKey,
    val title: String? = null,
    val icon: OwnTVIcon? = null,
    val providerName: String? = null,
    /** The playlist a folder belongs to: its mark in the Movies / Series category list. */
    val sourceId: Long? = null,
    /** Behind the Customize PIN (it shows a lock); [sealed] while this session has not opened it. */
    val locked: Boolean = false,
    val sealed: Boolean = false,
)
