package tv.own.owntv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.ContentHashProjection
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.SeasonEntity
import tv.own.owntv.core.database.entity.SeriesEntity

/** Series browsing plus the Show → Season → Episode hierarchy. */
@Dao
interface SeriesDao {
    // --- Series ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeries(series: List<SeriesEntity>)

    /** Like [upsertSeries] but returns the row ids — the M3U series import needs them for episodes. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesReturnIds(series: List<SeriesEntity>): List<Long>

    /** Like [upsertSeasons] but returns the row ids — the M3U series import needs them for episodes. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonsReturnIds(seasons: List<SeasonEntity>): List<Long>

    @Update
    suspend fun updateSeries(series: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: Long)

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeriesById(id: Long): SeriesEntity?

    // --- Stable-key lookups (Backup & Restore resolution: content ids change on re-sync) ---
    @Query("SELECT * FROM series WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun findSeriesByRemote(sourceId: Long, remoteId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE sourceId = :sourceId AND remoteId IN (:remoteIds)")
    suspend fun findSeriesByRemoteIds(sourceId: Long, remoteIds: List<String>): List<SeriesEntity>

    @Query("SELECT remoteId FROM series WHERE sourceId = :sourceId AND remoteId IS NOT NULL")
    suspend fun remoteIdsForSource(sourceId: Long): List<String>

    @Query("SELECT remoteId, id, contentHash, sortOrder FROM series WHERE sourceId = :sourceId AND remoteId IS NOT NULL")
    suspend fun contentHashesForSource(sourceId: Long): List<ContentHashProjection>

    @Query("DELETE FROM series WHERE sourceId = :sourceId AND remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(sourceId: Long, remoteIds: List<String>)

    // --- Re-sync delta check (Stalker paged catalogs): skip categories whose item count is unchanged ---
    @Query("SELECT categoryId, COUNT(*) AS itemCount FROM series WHERE sourceId = :sourceId AND categoryId IS NOT NULL GROUP BY categoryId")
    suspend fun countsByCategoryOnce(sourceId: Long): List<CategoryItemCount>

    @Query("SELECT remoteId FROM series WHERE sourceId = :sourceId AND categoryId = :categoryId AND remoteId IS NOT NULL")
    suspend fun remoteIdsForCategory(sourceId: Long, categoryId: Long): List<String>

    /** Prune scope for the per-category sync fallback — see [MovieDao.remoteIdsInCategories]. */
    @Query("SELECT remoteId FROM series WHERE sourceId = :sourceId AND categoryId IN (:categoryIds) AND remoteId IS NOT NULL")
    suspend fun remoteIdsInCategories(sourceId: Long, categoryIds: List<Long>): List<String>

    @Query("SELECT * FROM series WHERE sourceId = :sourceId AND name = :name LIMIT 1")
    suspend fun findSeriesByName(sourceId: Long, name: String): SeriesEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND remoteId = :remoteId LIMIT 1")
    suspend fun findEpisodeByRemote(seriesId: Long, remoteId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :season AND episodeNumber = :episode LIMIT 1")
    suspend fun findEpisodeByNumber(seriesId: Long, season: Int, episode: Int): EpisodeEntity?

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY sortOrder ASC, name ASC")
    fun pagingByCategory(categoryId: Long): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY name ASC")
    fun pagingByCategoryAlpha(categoryId: Long): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) ORDER BY name ASC")
    fun pagingAll(sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) ORDER BY sourceId ASC, sortOrder ASC, name ASC")
    fun pagingAllOriginal(sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    // Highest provider rating first; unrated (NULL) sink to the bottom (SQLite sorts NULL last in DESC).
    // Index-served by (sourceId, rating, name) / (categoryId, rating, name) — see MIGRATION_10_11.
    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) ORDER BY rating DESC, name ASC")
    fun pagingAllRating(sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY rating DESC, name ASC")
    fun pagingByCategoryRating(categoryId: Long): PagingSource<Int, SeriesEntity>

    // Date added / last modification (newest first). NULLs explicitly last.
    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun pagingAllDateAdded(sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun pagingByCategoryDateAdded(categoryId: Long): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun searchAllDateAdded(query: String, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun searchInCategoryDateAdded(query: String, categoryId: Long): PagingSource<Int, SeriesEntity>

    // --- Manual order (Move) — see ChannelDao for the join shape. ---
    @Query(
        "SELECT s.* FROM series s " +
            "LEFT JOIN content_order o ON o.itemId = s.id AND o.profileId = :profileId AND o.mediaType = 'SERIES' AND o.contextKey = :contextKey " +
            "WHERE s.categoryId = :categoryId " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, s.sortOrder, s.name",
    )
    fun pagingByCategoryManual(categoryId: Long, profileId: Long, contextKey: String): PagingSource<Int, SeriesEntity>

    @Query(
        "SELECT s.* FROM series s " +
            "INNER JOIN favorites f ON f.itemId = s.id AND f.mediaType = 'SERIES' " +
            "LEFT JOIN content_order o ON o.itemId = s.id AND o.profileId = :profileId AND o.mediaType = 'SERIES' AND o.contextKey = :contextKey " +
            "WHERE f.profileId = :profileId AND s.sourceId IN (:sourceIds) " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, f.addedAt DESC",
    )
    fun pagingFavoritesManual(profileId: Long, contextKey: String, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query(
        "SELECT s.* FROM series s " +
            "LEFT JOIN content_order o ON o.itemId = s.id AND o.profileId = :profileId AND o.mediaType = 'SERIES' AND o.contextKey = :contextKey " +
            "WHERE s.categoryId = :categoryId " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, s.sortOrder, s.name LIMIT :limit",
    )
    suspend fun snapshotByCategoryManual(categoryId: Long, profileId: Long, contextKey: String, limit: Int): List<SeriesEntity>

    @Query(
        "SELECT s.* FROM series s " +
            "INNER JOIN favorites f ON f.itemId = s.id AND f.mediaType = 'SERIES' " +
            "LEFT JOIN content_order o ON o.itemId = s.id AND o.profileId = :profileId AND o.mediaType = 'SERIES' AND o.contextKey = :contextKey " +
            "WHERE f.profileId = :profileId AND s.sourceId IN (:sourceIds) " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, f.addedAt DESC LIMIT :limit",
    )
    suspend fun snapshotFavoritesManual(profileId: Long, contextKey: String, sourceIds: List<Long>, limit: Int): List<SeriesEntity>

    @Query("SELECT COUNT(*) FROM series WHERE categoryId = :categoryId")
    fun countByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE sourceId IN (:sourceIds)")
    fun countAll(sourceIds: List<Long>): Flow<Int>

    /** "All Series" count with hidden categories excluded (matches the filtered ALL list). */
    @Query(
        "SELECT COUNT(*) FROM series WHERE sourceId IN (:sourceIds) " +
            "AND (categoryId IS NULL OR categoryId NOT IN (:excludedCategoryIds))",
    )
    fun countAllExcluding(sourceIds: List<Long>, excludedCategoryIds: List<Long>): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE sourceId = :sourceId")
    suspend fun countForSourceOnce(sourceId: Long): Int

    @Query(
        "SELECT * FROM series WHERE sourceId IN (:sourceIds) " +
            "AND id IN (SELECT rowid FROM series_fts WHERE series_fts MATCH :query) ORDER BY name ASC",
    )
    fun search(query: String, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    // --- Inline folder-scoped search (substring) ---
    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAll(query: String, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY sortOrder ASC, name ASC")
    fun searchInCategory(query: String, categoryId: Long): PagingSource<Int, SeriesEntity>

    /** Bounded list for global search (across all of a profile's sources). */
    @Query("SELECT * FROM series WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchList(query: String, sourceIds: List<Long>, limit: Int): List<SeriesEntity>

    /** FTS-backed bounded list for global as-you-type search (see MovieDao.searchListFts). */
    @Query(
        "SELECT * FROM series WHERE sourceId IN (:sourceIds) " +
            "AND id IN (SELECT rowid FROM series_fts WHERE series_fts MATCH :ftsQuery) ORDER BY name ASC LIMIT :limit",
    )
    suspend fun searchListFts(ftsQuery: String, sourceIds: List<Long>, limit: Int): List<SeriesEntity>

    @Query(
        "SELECT s.* FROM series s INNER JOIN favorites f ON f.itemId = s.id AND f.mediaType = 'SERIES' " +
            "WHERE f.profileId = :profileId AND s.sourceId IN (:sourceIds) AND s.name LIKE '%' || :query || '%' ORDER BY f.addedAt DESC",
    )
    fun searchFavorites(query: String, profileId: Long, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query(
        "SELECT s.* FROM series s " +
            "INNER JOIN favorites f ON f.itemId = s.id AND f.mediaType = 'SERIES' " +
            "WHERE f.profileId = :profileId ORDER BY f.addedAt DESC",
    )
    fun pagingFavorites(profileId: Long): PagingSource<Int, SeriesEntity>

    @Query(
        "SELECT COUNT(*) FROM favorites f INNER JOIN series s ON s.id = f.itemId " +
            "WHERE f.profileId = :profileId AND f.mediaType = 'SERIES' AND s.sourceId IN (:sourceIds)",
    )
    fun countFavorites(profileId: Long, sourceIds: List<Long>): Flow<Int>

    /** History rail count, joined to series so it can honor the active-playlist filter. */
    @Query(
        "SELECT COUNT(*) FROM watch_history h INNER JOIN series s ON s.id = h.itemId " +
            "WHERE h.profileId = :profileId AND h.mediaType = 'SERIES' AND s.sourceId IN (:sourceIds)",
    )
    fun countHistory(profileId: Long, sourceIds: List<Long>): Flow<Int>

    @Query(
        "SELECT s.* FROM series s INNER JOIN watch_history h ON h.itemId = s.id AND h.mediaType = 'SERIES' " +
            "WHERE h.profileId = :profileId AND s.sourceId IN (:sourceIds) ORDER BY h.watchedAt DESC",
    )
    fun pagingHistory(profileId: Long, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    @Query(
        "SELECT s.* FROM series s INNER JOIN watch_history h ON h.itemId = s.id AND h.mediaType = 'SERIES' " +
            "WHERE h.profileId = :profileId AND s.sourceId IN (:sourceIds) AND s.name LIKE '%' || :query || '%' ORDER BY h.watchedAt DESC",
    )
    fun searchHistory(query: String, profileId: Long, sourceIds: List<Long>): PagingSource<Int, SeriesEntity>

    /** Search "Continue" chip: recently-watched series snapshot (one-shot). */
    @Query(
        "SELECT s.* FROM series s INNER JOIN watch_history h ON h.itemId = s.id AND h.mediaType = 'SERIES' " +
            "WHERE h.profileId = :profileId AND s.sourceId IN (:sourceIds) ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    suspend fun recentlyWatchedSnapshot(profileId: Long, sourceIds: List<Long>, limit: Int): List<SeriesEntity>

    /** Search "Unwatched" chip: favourite series with no watch-history row (bounded by favourites). */
    @Query(
        "SELECT s.* FROM series s " +
            "INNER JOIN favorites f ON f.itemId = s.id AND f.mediaType = 'SERIES' AND f.profileId = :profileId " +
            "LEFT JOIN watch_history h ON h.itemId = s.id AND h.mediaType = 'SERIES' AND h.profileId = :profileId " +
            "WHERE s.sourceId IN (:sourceIds) AND h.itemId IS NULL ORDER BY f.addedAt DESC LIMIT :limit",
    )
    suspend fun unwatchedFavorites(profileId: Long, sourceIds: List<Long>, limit: Int): List<SeriesEntity>

    // --- Seasons ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasons(seasons: List<SeasonEntity>)

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY seasonNumber ASC")
    fun seasons(seriesId: Long): Flow<List<SeasonEntity>>

    // --- Episodes ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    fun episodesBySeason(seasonId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun episodesBySeries(seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT COUNT(*) FROM episodes WHERE seriesId = :seriesId")
    suspend fun episodeCount(seriesId: Long): Int

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodes(seriesId: Long)

    // --- Episode refresh (S8) ---
    // The refresh path must preserve episode row ids: watch history, resume positions and
    // next-episode autoplay all key on them, so a delete-then-reinsert would silently detach the
    // user's progress on every refresh. Hence read/update/insert/delete-the-gone rather than
    // deleteEpisodes + upsertEpisodes.

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun episodesBySeriesOnce(seriesId: Long): List<EpisodeEntity>

    @Update
    suspend fun updateEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE id IN (:ids)")
    suspend fun deleteEpisodesByIds(ids: List<Long>)

    /** Stamps a show's episode cache as freshly loaded (epoch ms). */
    @Query("UPDATE series SET episodesSyncedAt = :at WHERE id = :seriesId")
    suspend fun markEpisodesSynced(seriesId: Long, at: Long)

    /**
     * Invalidates every episode cache belonging to a source, so the next open of any of its shows
     * re-fetches. Called after a successful sync: "just resync" is what a user does when episodes
     * look stale, and before S8 that did nothing at all for episodes.
     *
     * The `!= 0` keeps this proportional to the shows actually opened rather than to the catalog:
     * a 46k-series source where the user has opened twelve shows writes twelve rows. It also makes
     * the call a genuine no-op for M3U, whose shows are never stamped.
     */
    @Query("UPDATE series SET episodesSyncedAt = 0 WHERE sourceId = :sourceId AND episodesSyncedAt != 0")
    suspend fun invalidateEpisodeCaches(sourceId: Long)

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId")
    suspend fun deleteSeasons(seriesId: Long)

    /** One-time cleanup of pre-stable-key M3U rows (remoteId was always NULL under clear-then-insert). */
    @Query("DELETE FROM series WHERE sourceId = :sourceId AND remoteId IS NULL")
    suspend fun deleteNullRemoteIds(sourceId: Long)
}
