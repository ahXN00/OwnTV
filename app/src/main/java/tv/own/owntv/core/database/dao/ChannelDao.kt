package tv.own.owntv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Live TV channels. Big lists are exposed as [PagingSource]; totals come from indexed COUNT queries
 * (per the plan's count requirements). FTS search joins via `channels_fts.rowid = channels.id`.
 * Favorites/history join the profile-scoped user-data tables.
 */
@Dao
interface ChannelDao {
    /** Batch insert; the sync layer calls this in chunks (~500) inside a transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: Long)

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    // --- Stable-key lookups (Backup & Restore resolution: content ids change on re-sync) ---
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun findByRemote(sourceId: Long, remoteId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND name = :name LIMIT 1")
    suspend fun findByName(sourceId: Long, name: String): ChannelEntity?

    /** Channels that carry an EPG id (so the guide grid only lists channels that can have a schedule). */
    @Query(
        "SELECT * FROM channels WHERE sourceId IN (:sourceIds) AND epgChannelId IS NOT NULL AND epgChannelId != '' " +
            "ORDER BY number ASC, name ASC LIMIT :limit",
    )
    suspend fun channelsForGuide(sourceIds: List<Long>, limit: Int): List<ChannelEntity>

    /**
     * Channels that actually HAVE programmes in the window — so the guide never wastes its row limit
     * on channels without data. The id match is case/whitespace-insensitive: XMLTV channel ids often
     * differ from the panel's epg_channel_id in case.
     */
    @Query(
        "SELECT * FROM channels WHERE sourceId IN (:sourceIds) AND epgChannelId IS NOT NULL AND epgChannelId != '' " +
            "AND (:query = '' OR name LIKE '%' || :query || '%') " +
            "AND LOWER(TRIM(epgChannelId)) IN (" +
            "  SELECT DISTINCT LOWER(TRIM(epgChannelId)) FROM epg_programmes " +
            "  WHERE sourceId IN (:sourceIds) AND stopMs > :from AND startMs < :to" +
            ") ORDER BY number ASC, name ASC LIMIT :limit",
    )
    suspend fun channelsWithGuide(sourceIds: List<Long>, from: Long, to: Long, query: String, limit: Int): List<ChannelEntity>

    // --- Browsing (each list has a playlist-order and an A–Z variant; the sort chip picks one) ---
    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY sortOrder ASC, name ASC")
    fun pagingByCategory(categoryId: Long): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY name ASC")
    fun pagingByCategoryAlpha(categoryId: Long): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId IN (:sourceIds) ORDER BY name ASC")
    fun pagingAll(sourceIds: List<Long>): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId IN (:sourceIds) ORDER BY sourceId ASC, sortOrder ASC, name ASC")
    fun pagingAllOriginal(sourceIds: List<Long>): PagingSource<Int, ChannelEntity>

    // --- Counts ---
    @Query("SELECT COUNT(*) FROM channels WHERE categoryId = :categoryId")
    fun countByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId IN (:sourceIds)")
    fun countAll(sourceIds: List<Long>): Flow<Int>

    /** "All Channels" count with hidden categories excluded (matches the filtered ALL list). */
    @Query(
        "SELECT COUNT(*) FROM channels WHERE sourceId IN (:sourceIds) " +
            "AND (categoryId IS NULL OR categoryId NOT IN (:excludedCategoryIds))",
    )
    fun countAllExcluding(sourceIds: List<Long>, excludedCategoryIds: List<Long>): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSourceOnce(sourceId: Long): Int

    // --- Search (FTS4) ---
    @Query(
        "SELECT * FROM channels WHERE sourceId IN (:sourceIds) " +
            "AND id IN (SELECT rowid FROM channels_fts WHERE channels_fts MATCH :query) ORDER BY name ASC",
    )
    fun search(query: String, sourceIds: List<Long>): PagingSource<Int, ChannelEntity>

    // --- Inline folder-scoped search (substring LIKE, matches the user's expectation) ---
    @Query("SELECT * FROM channels WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAll(query: String, sourceIds: List<Long>): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY sortOrder ASC, name ASC")
    fun searchInCategory(query: String, categoryId: Long): PagingSource<Int, ChannelEntity>

    /** Bounded list for global search (across all of a profile's sources). */
    @Query("SELECT * FROM channels WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchList(query: String, sourceIds: List<Long>, limit: Int): List<ChannelEntity>

    @Query(
        "SELECT c.* FROM channels c INNER JOIN favorites f ON f.itemId = c.id AND f.mediaType = 'LIVE' " +
            "WHERE f.profileId = :profileId AND c.name LIKE '%' || :query || '%' ORDER BY f.addedAt DESC",
    )
    fun searchFavorites(query: String, profileId: Long): PagingSource<Int, ChannelEntity>

    @Query(
        "SELECT c.* FROM channels c INNER JOIN watch_history h ON h.itemId = c.id AND h.mediaType = 'LIVE' " +
            "WHERE h.profileId = :profileId AND c.name LIKE '%' || :query || '%' ORDER BY h.watchedAt DESC",
    )
    fun searchHistory(query: String, profileId: Long): PagingSource<Int, ChannelEntity>

    // --- Favorites / History (profile-scoped) ---
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN favorites f ON f.itemId = c.id AND f.mediaType = 'LIVE' " +
            "WHERE f.profileId = :profileId ORDER BY f.addedAt DESC",
    )
    fun pagingFavorites(profileId: Long): PagingSource<Int, ChannelEntity>

    // Counts via the same join the list uses, so the badge can't show favorites whose channel id went
    // stale on a re-sync (the old "(2) but the folder is empty" bug) before the relink purges them.
    @Query(
        "SELECT COUNT(*) FROM favorites f INNER JOIN channels c ON c.id = f.itemId " +
            "WHERE f.profileId = :profileId AND f.mediaType = 'LIVE'",
    )
    fun countFavorites(profileId: Long): Flow<Int>

    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN watch_history h ON h.itemId = c.id AND h.mediaType = 'LIVE' " +
            "WHERE h.profileId = :profileId ORDER BY h.watchedAt DESC",
    )
    fun pagingHistory(profileId: Long): PagingSource<Int, ChannelEntity>

    /** Recently-watched row at the top of Live TV. */
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN watch_history h ON h.itemId = c.id AND h.mediaType = 'LIVE' " +
            "WHERE h.profileId = :profileId ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    fun recentlyWatched(profileId: Long, limit: Int): Flow<List<ChannelEntity>>
}
