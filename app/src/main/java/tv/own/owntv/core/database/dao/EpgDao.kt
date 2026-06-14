package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.EpgChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** EPG storage + now/next lookups. Programmes are kept to a rolling window and pruned. */
@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgrammes(programmes: List<EpgProgrammeEntity>)

    @Query("DELETE FROM epg_programmes WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: Long)

    /** Drop programmes that have already finished, to bound storage. */
    @Query("DELETE FROM epg_programmes WHERE stopMs < :before")
    suspend fun prune(before: Long)

    /** The programme airing at [now] on a given EPG channel. */
    @Query("SELECT * FROM epg_programmes WHERE epgChannelId = :epgChannelId AND startMs <= :now AND stopMs > :now ORDER BY startMs DESC LIMIT 1")
    suspend fun nowPlaying(epgChannelId: String, now: Long): EpgProgrammeEntity?

    /** Now + upcoming programmes for a channel (now/next and a short guide). */
    @Query("SELECT * FROM epg_programmes WHERE epgChannelId = :epgChannelId AND stopMs > :now ORDER BY startMs ASC LIMIT :limit")
    fun upcoming(epgChannelId: String, now: Long, limit: Int): Flow<List<EpgProgrammeEntity>>

    /** All programmes overlapping a time window for the given sources — drives the full guide grid. */
    @Query("SELECT * FROM epg_programmes WHERE sourceId IN (:sourceIds) AND stopMs > :from AND startMs < :to ORDER BY epgChannelId ASC, startMs ASC")
    suspend fun programmesInWindow(sourceIds: List<Long>, from: Long, to: Long): List<EpgProgrammeEntity>

    /**
     * One guide row's programmes, loaded lazily when the row scrolls into view. [epgKey] must be the
     * normalized (trim+lowercase) id — programmes are stored normalized, so this hits the
     * (epgChannelId, startMs) index and stays instant even with 100k+ stored programmes.
     */
    @Query("SELECT * FROM epg_programmes WHERE epgChannelId = :epgKey AND sourceId IN (:sourceIds) AND stopMs > :from AND startMs < :to ORDER BY startMs ASC")
    suspend fun programmesForChannel(sourceIds: List<Long>, epgKey: String, from: Long, to: Long): List<EpgProgrammeEntity>

    /** How many programmes are stored for these sources (to tell "no guide yet" from "empty window"). */
    @Query("SELECT COUNT(*) FROM epg_programmes WHERE sourceId IN (:sourceIds)")
    suspend fun countForSources(sourceIds: List<Long>): Int

    /** Live programme count for one source — drives the EPG status shown on the source row. */
    @Query("SELECT COUNT(*) FROM epg_programmes WHERE sourceId = :sourceId")
    fun countForSource(sourceId: Long): Flow<Int>

    /** How many distinct channels actually have guide data (for the Guide's status line). */
    @Query("SELECT COUNT(DISTINCT epgChannelId) FROM epg_programmes WHERE sourceId IN (:sourceIds)")
    suspend fun countGuideChannels(sourceIds: List<Long>): Int

    /** Distinct EPG channels available across feeds — drives the manual "Match EPG" picker. */
    @Query(
        "SELECT * FROM epg_channels WHERE sourceId IN (:sourceIds) " +
            "AND (:query = '' OR LOWER(displayName) LIKE '%' || :query || '%' OR LOWER(epgChannelId) LIKE '%' || :query || '%') " +
            "GROUP BY epgChannelId ORDER BY displayName ASC LIMIT :limit",
    )
    suspend fun listEpgChannels(sourceIds: List<Long>, query: String, limit: Int): List<EpgChannelEntity>
}
