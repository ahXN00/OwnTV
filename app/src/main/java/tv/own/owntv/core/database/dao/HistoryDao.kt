package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType

/** Write side of watch history; the recently-watched content rows live in the content DAOs (joins). */
@Dao
interface HistoryDao {
    /** One row per (profile, type, item); REPLACE bumps `watchedAt` via the unique index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    suspend fun remove(profileId: Long, type: MediaType, itemId: Long)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)

    @Query("SELECT COUNT(*) FROM watch_history WHERE profileId = :profileId AND mediaType = :type")
    fun count(profileId: Long, type: MediaType): Flow<Int>

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM watch_history")
    suspend fun getAllOnce(): List<WatchHistoryEntity>

    /** Drops history rows orphaned by a re-sync (see FavoriteDao.purgeOrphans); episodes excluded. */
    @Query(
        "DELETE FROM watch_history WHERE " +
            "(mediaType = 'LIVE'   AND itemId NOT IN (SELECT id FROM channels)) OR " +
            "(mediaType = 'MOVIE'  AND itemId NOT IN (SELECT id FROM movies))   OR " +
            "(mediaType = 'SERIES' AND itemId NOT IN (SELECT id FROM series))",
    )
    suspend fun purgeOrphans()
}
