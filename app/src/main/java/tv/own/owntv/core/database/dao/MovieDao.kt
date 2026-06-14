package tv.own.owntv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.MovieEntity

@Dao
interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: Long)

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getById(id: Long): MovieEntity?

    // --- Stable-key lookups (Backup & Restore resolution: content ids change on re-sync) ---
    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun findByRemote(sourceId: Long, remoteId: String): MovieEntity?

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

    @Query("SELECT COUNT(*) FROM movies WHERE categoryId = :categoryId")
    fun countByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM movies WHERE sourceId IN (:sourceIds)")
    fun countAll(sourceIds: List<Long>): Flow<Int>

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

    @Query(
        "SELECT m.* FROM movies m INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' " +
            "WHERE f.profileId = :profileId AND m.name LIKE '%' || :query || '%' ORDER BY f.addedAt DESC",
    )
    fun searchFavorites(query: String, profileId: Long): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT m.* FROM movies m INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId AND m.name LIKE '%' || :query || '%' ORDER BY h.watchedAt DESC",
    )
    fun searchHistory(query: String, profileId: Long): PagingSource<Int, MovieEntity>

    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN favorites f ON f.itemId = m.id AND f.mediaType = 'MOVIE' " +
            "WHERE f.profileId = :profileId ORDER BY f.addedAt DESC",
    )
    fun pagingFavorites(profileId: Long): PagingSource<Int, MovieEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE profileId = :profileId AND mediaType = 'MOVIE'")
    fun countFavorites(profileId: Long): Flow<Int>

    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId ORDER BY h.watchedAt DESC",
    )
    fun pagingHistory(profileId: Long): PagingSource<Int, MovieEntity>

    /** Recently-watched / continue-watching row at the top of Movies. */
    @Query(
        "SELECT m.* FROM movies m " +
            "INNER JOIN watch_history h ON h.itemId = m.id AND h.mediaType = 'MOVIE' " +
            "WHERE h.profileId = :profileId ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    fun recentlyWatched(profileId: Long, limit: Int): Flow<List<MovieEntity>>
}
