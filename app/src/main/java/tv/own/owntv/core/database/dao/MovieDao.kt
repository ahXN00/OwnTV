package tv.own.owntv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.ContentHashProjection
import tv.own.owntv.core.database.entity.MovieEntity

/** One category's current item count for a source (re-sync delta check). */
data class CategoryItemCount(val categoryId: Long, val itemCount: Int)

@Dao
interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Update
    suspend fun updateAll(movies: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: Long)

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getById(id: Long): MovieEntity?

    // --- Stable-key lookups (Backup & Restore resolution: content ids change on re-sync) ---
    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun findByRemote(sourceId: Long, remoteId: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND remoteId IN (:remoteIds)")
    suspend fun findByRemoteIds(sourceId: Long, remoteIds: List<String>): List<MovieEntity>

    @Query("SELECT remoteId FROM movies WHERE sourceId = :sourceId AND remoteId IS NOT NULL")
    suspend fun remoteIdsForSource(sourceId: Long): List<String>

    @Query("SELECT remoteId, id, contentHash, sortOrder FROM movies WHERE sourceId = :sourceId AND remoteId IS NOT NULL")
    suspend fun contentHashesForSource(sourceId: Long): List<ContentHashProjection>

    @Query("DELETE FROM movies WHERE sourceId = :sourceId AND remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(sourceId: Long, remoteIds: List<String>)

    /** One-time cleanup of pre-stable-key M3U rows (remoteId was always NULL under clear-then-insert). */
    @Query("DELETE FROM movies WHERE sourceId = :sourceId AND remoteId IS NULL")
    suspend fun deleteNullRemoteIds(sourceId: Long)

    // --- Re-sync delta check (Stalker paged catalogs): skip categories whose item count is unchanged ---
    @Query("SELECT categoryId, COUNT(*) AS itemCount FROM movies WHERE sourceId = :sourceId AND categoryId IS NOT NULL GROUP BY categoryId")
    suspend fun countsByCategoryOnce(sourceId: Long): List<CategoryItemCount>

    @Query("SELECT remoteId FROM movies WHERE sourceId = :sourceId AND categoryId = :categoryId AND remoteId IS NOT NULL")
    suspend fun remoteIdsForCategory(sourceId: Long, categoryId: Long): List<String>

    /** Prune scope for the per-category sync fallback: rows in the categories that were fetched
     *  successfully. Rows with no category are never returned by a per-category request, so they
     *  must stay out of scope or a fallback pass would delete them all. */
    @Query("SELECT remoteId FROM movies WHERE sourceId = :sourceId AND categoryId IN (:categoryIds) AND remoteId IS NOT NULL")
    suspend fun remoteIdsInCategories(sourceId: Long, categoryIds: List<Long>): List<String>

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND name = :name LIMIT 1")
    suspend fun findByName(sourceId: Long, name: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY sortOrder ASC, name ASC")
    fun pagingByCategory(categoryId: Long): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY name ASC")
    fun pagingByCategoryAlpha(categoryId: Long): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) ORDER BY name ASC")
    fun pagingAll(sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) ORDER BY sourceId ASC, sortOrder ASC, name ASC")
    fun pagingAllOriginal(sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    // Highest provider rating first; unrated (NULL) sink to the bottom (SQLite sorts NULL last in DESC).
    // Index-served by (sourceId, rating, name) / (categoryId, rating, name) — see MIGRATION_10_11.
    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) ORDER BY rating DESC, name ASC")
    fun pagingAllRating(sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY rating DESC, name ASC")
    fun pagingByCategoryRating(categoryId: Long): PagingSource<Int, MovieEntity>

    // Date added / last modification (newest first). NULLs explicitly last.
    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun pagingAllDateAdded(sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun pagingByCategoryDateAdded(categoryId: Long): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun searchAllDateAdded(query: String, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY addedAt IS NULL, addedAt DESC, id DESC, name ASC")
    fun searchInCategoryDateAdded(query: String, categoryId: Long): PagingSource<Int, MovieEntity>

    // --- Manual order (Move) — see ChannelDao for the join shape. ---
    @Query(
        "SELECT m.* FROM movies m " +
            "LEFT JOIN content_order o ON o.itemId = m.id AND o.profileId = :profileId AND o.mediaType = 'MOVIE' AND o.contextKey = :contextKey " +
            "WHERE m.categoryId = :categoryId " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, m.sortOrder, m.name",
    )
    fun pagingByCategoryManual(categoryId: Long, profileId: Long, contextKey: String): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' " +
            "LEFT JOIN content_order o ON o.itemId = m.id AND o.profileId = :profileId AND o.mediaType = 'MOVIE' AND o.contextKey = :contextKey " +
            "WHERE f.profileId = :profileId AND m.sourceId IN (:sourceIds) " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, f.addedAt DESC",
    )
    fun pagingFavoritesManual(profileId: Long, contextKey: String, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT m.* FROM movies m " +
            "LEFT JOIN content_order o ON o.itemId = m.id AND o.profileId = :profileId AND o.mediaType = 'MOVIE' AND o.contextKey = :contextKey " +
            "WHERE m.categoryId = :categoryId " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, m.sortOrder, m.name LIMIT :limit",
    )
    suspend fun snapshotByCategoryManual(categoryId: Long, profileId: Long, contextKey: String, limit: Int): List<MovieEntity>

    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' " +
            "LEFT JOIN content_order o ON o.itemId = m.id AND o.profileId = :profileId AND o.mediaType = 'MOVIE' AND o.contextKey = :contextKey " +
            "WHERE f.profileId = :profileId AND m.sourceId IN (:sourceIds) " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, f.addedAt DESC LIMIT :limit",
    )
    suspend fun snapshotFavoritesManual(profileId: Long, contextKey: String, sourceIds: List<Long>, limit: Int): List<MovieEntity>

    @Query("SELECT COUNT(*) FROM movies WHERE categoryId = :categoryId")
    fun countByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM movies WHERE sourceId IN (:sourceIds)")
    fun countAll(sourceIds: List<Long>): Flow<Int>

    /** "All Movies" count with hidden categories excluded (matches the filtered ALL list). */
    @Query(
        "SELECT COUNT(*) FROM movies WHERE sourceId IN (:sourceIds) " +
            "AND (categoryId IS NULL OR categoryId NOT IN (:excludedCategoryIds))",
    )
    fun countAllExcluding(sourceIds: List<Long>, excludedCategoryIds: List<Long>): Flow<Int>

    @Query("SELECT COUNT(*) FROM movies WHERE sourceId = :sourceId")
    suspend fun countForSourceOnce(sourceId: Long): Int

    @Query(
        "SELECT * FROM movies WHERE sourceId IN (:sourceIds) " +
            "AND id IN (SELECT rowid FROM movies_fts WHERE movies_fts MATCH :query) ORDER BY name ASC",
    )
    fun search(query: String, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    // --- Inline folder-scoped search (substring) ---
    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAll(query: String, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY sortOrder ASC, name ASC")
    fun searchInCategory(query: String, categoryId: Long): PagingSource<Int, MovieEntity>

    /** Bounded list for global search (across all of a profile's sources). */
    @Query("SELECT * FROM movies WHERE sourceId IN (:sourceIds) AND name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchList(query: String, sourceIds: List<Long>, limit: Int): List<MovieEntity>

    /** FTS-backed bounded list for global as-you-type search: index-served prefix-token match instead
     *  of the leading-wildcard LIKE above, which scans all ~170k rows per keystroke. [ftsQuery] must be
     *  a sanitized FTS MATCH expression (see SearchViewModel.ftsQueryFor). */
    @Query(
        "SELECT * FROM movies WHERE sourceId IN (:sourceIds) " +
            "AND id IN (SELECT rowid FROM movies_fts WHERE movies_fts MATCH :ftsQuery) ORDER BY name ASC LIMIT :limit",
    )
    suspend fun searchListFts(ftsQuery: String, sourceIds: List<Long>, limit: Int): List<MovieEntity>

    @Query(
        "SELECT m.* FROM movies m INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' " +
            "WHERE f.profileId = :profileId AND m.sourceId IN (:sourceIds) AND m.name LIKE '%' || :query || '%' ORDER BY f.addedAt DESC",
    )
    fun searchFavorites(query: String, profileId: Long, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT m.* FROM movies m INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId AND m.sourceId IN (:sourceIds) AND m.name LIKE '%' || :query || '%' ORDER BY h.watchedAt DESC",
    )
    fun searchHistory(query: String, profileId: Long, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' " +
            "WHERE f.profileId = :profileId ORDER BY f.addedAt DESC",
    )
    fun pagingFavorites(profileId: Long): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT COUNT(*) FROM favorites f INNER JOIN movies m ON m.id = f.itemId " +
            "WHERE f.profileId = :profileId AND f.mediaType = 'MOVIE' AND m.sourceId IN (:sourceIds)",
    )
    fun countFavorites(profileId: Long, sourceIds: List<Long>): Flow<Int>

    /** History rail count, joined to movies so it can honor the active-playlist filter. */
    @Query(
        "SELECT COUNT(*) FROM watch_history h INNER JOIN movies m ON m.id = h.itemId " +
            "WHERE h.profileId = :profileId AND h.mediaType = 'MOVIE' AND m.sourceId IN (:sourceIds)",
    )
    fun countHistory(profileId: Long, sourceIds: List<Long>): Flow<Int>

    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId AND m.sourceId IN (:sourceIds) ORDER BY h.watchedAt DESC",
    )
    fun pagingHistory(profileId: Long, sourceIds: List<Long>): PagingSource<Int, MovieEntity>

    /** Recently-watched / continue-watching row at the top of Movies. */
    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    fun recentlyWatched(profileId: Long, limit: Int): Flow<List<MovieEntity>>

    /** Search "Continue" chip: recently-watched snapshot (one-shot), scoped to the active sources. */
    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId AND m.sourceId IN (:sourceIds) ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    suspend fun recentlyWatchedSnapshot(profileId: Long, sourceIds: List<Long>, limit: Int): List<MovieEntity>

    /** Search "Unwatched" chip: favourite movies with no watch-history row (bounded by favourites). */
    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' AND f.profileId = :profileId " +
            "LEFT JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' AND h.profileId = :profileId " +
            "WHERE m.sourceId IN (:sourceIds) AND h.itemId IS NULL ORDER BY f.addedAt DESC LIMIT :limit",
    )
    suspend fun unwatchedFavorites(profileId: Long, sourceIds: List<Long>, limit: Int): List<MovieEntity>
}
