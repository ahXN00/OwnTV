package tv.own.owntv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ChannelFtsEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpgChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.EpisodeFtsEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.MovieFtsEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SeasonEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SeriesFtsEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity

@Database(
    entities = [
        // Profiles & sources
        ProfileEntity::class,
        SourceEntity::class,
        ProfileSourceCrossRef::class,
        // Content
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        // User data (profile-scoped)
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        PlaybackProgressEntity::class,
        DownloadEntity::class,
        // EPG
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        // FTS (search)
        ChannelFtsEntity::class,
        MovieFtsEntity::class,
        SeriesFtsEntity::class,
        EpisodeFtsEntity::class,
    ],
    version = 2, // v2 (v2.2.0): EPG tables lost their sources FK so standalone EPG sources can insert
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OwnTVDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sourceDao(): SourceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun progressDao(): ProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun epgDao(): EpgDao

    companion object {
        const val NAME = "owntv.db"

        /**
         * v1 → v2: drop the foreign key on the EPG tables (standalone EPG sources use ids that
         * aren't in `sources`). EPG data is transient and re-synced, so the tables are recreated
         * empty — everything else (profiles, sources, content, favorites, history) is preserved.
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `epg_programmes`")
                db.execSQL("DROP TABLE IF EXISTS `epg_channels`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `epg_channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `epgChannelId` TEXT NOT NULL, `displayName` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_channels_sourceId` ON `epg_channels` (`sourceId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_channels_sourceId_epgChannelId` ON `epg_channels` (`sourceId`, `epgChannelId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `epg_programmes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `epgChannelId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `stopMs` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_epgChannelId_startMs` ON `epg_programmes` (`epgChannelId`, `startMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId` ON `epg_programmes` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_stopMs` ON `epg_programmes` (`stopMs`)")
            }
        }
    }
}
