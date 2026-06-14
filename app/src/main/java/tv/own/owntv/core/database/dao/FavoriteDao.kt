package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.model.MediaType

/** Write side of favorites; per-type favorite content lists live in the content DAOs (joins). */
@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    suspend fun remove(profileId: Long, type: MediaType, itemId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId)")
    fun isFavorite(profileId: Long, type: MediaType, itemId: Long): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorites WHERE profileId = :profileId AND mediaType = :type")
    fun count(profileId: Long, type: MediaType): Flow<Int>

    /** Favorite item ids for a type, so lists can mark rows without a per-row query. */
    @Query("SELECT itemId FROM favorites WHERE profileId = :profileId AND mediaType = :type")
    fun observeFavoriteIds(profileId: Long, type: MediaType): Flow<List<Long>>

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM favorites")
    suspend fun getAllOnce(): List<FavoriteEntity>

    /**
     * Drops favorites whose content row no longer exists — content is clear-then-insert on every sync,
     * so a favorite's itemId goes stale and the join (pagingFavorites) returns nothing while the raw
     * count still showed it. Called after a re-sync's relink (UserDataResolver). Episodes are excluded
     * (they load lazily, so their rows are legitimately absent until a show is opened).
     */
    @Query(
        "DELETE FROM favorites WHERE " +
            "(mediaType = 'LIVE'   AND itemId NOT IN (SELECT id FROM channels)) OR " +
            "(mediaType = 'MOVIE'  AND itemId NOT IN (SELECT id FROM movies))   OR " +
            "(mediaType = 'SERIES' AND itemId NOT IN (SELECT id FROM series))",
    )
    suspend fun purgeOrphans()
}
