package tv.own.owntv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * EPG channel descriptor (XMLTV `<channel>` or an Xtream epg id). Channels link to it via their
 * `epgChannelId`.
 */
// NOTE: no foreign key to sources. EPG also comes from standalone EPG sources (stored in DataStore
// with negative ids that don't exist in the sources table), so a sourceId FK would reject them.
// EPG rows are cleared explicitly when a source/EPG-source is removed.
@Entity(
    tableName = "epg_channels",
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "epgChannelId"], unique = true),
    ],
)
data class EpgChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val epgChannelId: String,
    val displayName: String? = null,
)

/**
 * A single programme (now/next & guide). Bounded to a rolling window (≈ now → +48h) by the EPG
 * engine, which prunes old rows. Indexed on `(epgChannelId, startMs)` for fast now/next lookups.
 */
@Entity(
    tableName = "epg_programmes",
    indices = [
        Index(value = ["epgChannelId", "startMs"]),
        Index("sourceId"),
        Index("stopMs"),
    ],
)
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val epgChannelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String? = null,
)
