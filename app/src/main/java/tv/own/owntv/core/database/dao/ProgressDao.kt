package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.model.MediaType

/** Resume positions for VOD and episodes (per profile). */
@Dao
interface ProgressDao {
    /** One row per (profile, type, item); REPLACE updates position via the unique index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    fun observe(profileId: Long, type: MediaType, itemId: Long): Flow<PlaybackProgressEntity?>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    suspend fun get(profileId: Long, type: MediaType, itemId: Long): PlaybackProgressEntity?

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    suspend fun clear(profileId: Long, type: MediaType, itemId: Long)

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM playback_progress")
    suspend fun getAllOnce(): List<PlaybackProgressEntity>

    /**
     * Drops resume positions orphaned by a re-sync (see FavoriteDao.purgeOrphans). Episodes are
     * excluded — they load lazily, so episode progress is kept and re-attached when the show opens.
     */
    @Query(
        "DELETE FROM playback_progress WHERE " +
            "(mediaType = 'LIVE'   AND itemId NOT IN (SELECT id FROM channels)) OR " +
            "(mediaType = 'MOVIE'  AND itemId NOT IN (SELECT id FROM movies))   OR " +
            "(mediaType = 'SERIES' AND itemId NOT IN (SELECT id FROM series))",
    )
    suspend fun purgeOrphans()
}
