package tv.own.owntv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MetadataDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.TvProviderProgramDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ChannelFtsEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpgChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.EpisodeFtsEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.MetadataMatchEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.MovieFtsEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SeasonEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SeriesFtsEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.SubtitleCacheEntity
import tv.own.owntv.core.database.entity.SubtitleLinkEntity
import tv.own.owntv.core.database.entity.SubtitleSelectionEntity
import tv.own.owntv.core.database.entity.SubtitleTimingEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.database.entity.TvProviderProgramEntity
import tv.own.owntv.core.database.dao.SubtitleDao

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
        ContentOrderEntity::class,
        DownloadEntity::class,
        // Android TV home-screen bookkeeping
        TvProviderProgramEntity::class,
        // EPG
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        // TMDB metadata enrichment cache (plan §7)
        MetadataCacheEntity::class,
        MetadataMatchEntity::class,
        // External subtitles (OpenSubtitles / local files) — subtitle plan Phase 2
        SubtitleCacheEntity::class,
        SubtitleSelectionEntity::class,
        SubtitleTimingEntity::class,
        SubtitleLinkEntity::class,
        // FTS (search)
        ChannelFtsEntity::class,
        MovieFtsEntity::class,
        SeriesFtsEntity::class,
        EpisodeFtsEntity::class,
    ],
    version = 21, // v7: content_order (Move). v8: contentHash + browse/unique indexes. v9: EPG contentHash + natural key. v10: TMDB metadata cache. v11: movies/series rating-sort indexes. v12: metadata_cache trailerKey. v13: metadata_cache logoPath. v14: sources.mac (Stalker portal). v15: external-subtitle cache/selection/timing tables. v16: subtitle_link (downloaded-sub ↔ content). v17: sources.syncLive/Movies/Series (skip-sync enabledScope). v18: series.episodesSyncedAt (episode-cache freshness, S8). v19: epg_channels.iconUrl (XMLTV channel logos). v20: channels (sourceId, number) index for direct tune. v21: series.addedAt + date-added sorting triggers

    exportSchema = true,
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
    abstract fun contentOrderDao(): ContentOrderDao
    abstract fun tvProviderProgramDao(): TvProviderProgramDao
    abstract fun downloadDao(): DownloadDao
    abstract fun epgDao(): EpgDao
    abstract fun metadataDao(): tv.own.owntv.core.database.dao.MetadataDao
    abstract fun subtitleDao(): SubtitleDao

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

        /**
         * v2 → v3:
         * - add catch-up/archive columns to `channels` (pure additive ALTERs with defaults)
         * - add Android TV provider bookkeeping for Watch Next / Continue Watching rows
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchup` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupSource` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tv_provider_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`surface` TEXT NOT NULL, " +
                        "`mediaType` TEXT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`targetItemId` INTEGER NOT NULL, " +
                        "`providerProgramId` INTEGER, " +
                        "`lastPositionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`lastEngagementAt` INTEGER NOT NULL, " +
                        "`lastPublishedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId` ON `tv_provider_programs` (`profileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId_surface_mediaType_groupId` ON `tv_provider_programs` (`profileId`, `surface`, `mediaType`, `groupId`)")
            }
        }

        /**
         * v3 → v4: v3 existed in the wild in two incompatible variants (catch-up vs Android TV home
         * bookkeeping). v4 unifies them by ensuring BOTH the catch-up columns and the provider table
         * exist, regardless of which v3 a user has.
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Channels catch-up columns (skip if already present).
                if (!hasColumn(db, "channels", "catchup")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchup` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "channels", "catchupDays")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupDays` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "channels", "catchupSource")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupSource` TEXT")
                }

                // Android TV provider bookkeeping table (safe to run repeatedly).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tv_provider_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`surface` TEXT NOT NULL, " +
                        "`mediaType` TEXT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`targetItemId` INTEGER NOT NULL, " +
                        "`providerProgramId` INTEGER, " +
                        "`lastPositionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`lastEngagementAt` INTEGER NOT NULL, " +
                        "`lastPublishedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId` ON `tv_provider_programs` (`profileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId_surface_mediaType_groupId` ON `tv_provider_programs` (`profileId`, `surface`, `mediaType`, `groupId`)")

                // EPG-perf Guide read-index (v4.0.0). Declared on EpgProgrammeEntity, so v4 expects it; older
                // DBs (and the runtime ensureEpgIndexes) create it too — make sure the migrated DB has it.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId_epgChannelId` ON `epg_programmes` (`sourceId`, `epgChannelId`)")
            }
        }

        /**
         * v4 → v6: main's v5 briefly added `favorites.sortOrder` and v6 removed it again, so the v4
         * and v6 schemas are identical — a no-op hop keeps the public 3 → latest chain unbroken.
         * (Dev builds that sat exactly on the transient v5 fall back to the destructive safety net,
         * same as on main; v5 never shipped publicly.)
         */
        val MIGRATION_4_6 = object : androidx.room.migration.Migration(4, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // v4 and v6 schemas are identical.
            }
        }

        /**
         * v6 → v7: manual reorder (Move) — per-profile `content_order` table. This is main's v7 and
         * must keep that meaning: dev devices on unreleased main builds already sit on it.
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                createContentOrderTable(db)
            }
        }

        /**
         * v7 → v8: incremental sync (PR #40) — `contentHash` on channels/movies/series plus the
         * browse composite indexes and the unique `(sourceId, remoteId)` movie/series indexes.
         * Everything is guarded so both v3.2.0-lineage and main-dev DBs (which already have the
         * indexes) migrate cleanly.
         */
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "channels", "contentHash")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `contentHash` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "movies", "contentHash")) {
                    db.execSQL("ALTER TABLE `movies` ADD COLUMN `contentHash` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "series", "contentHash")) {
                    db.execSQL("ALTER TABLE `series` ADD COLUMN `contentHash` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_name` ON `channels` (`sourceId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_categoryId_name` ON `channels` (`categoryId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_sortOrder_name` ON `channels` (`sourceId`, `sortOrder`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_categoryId_sortOrder_name` ON `channels` (`categoryId`, `sortOrder`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_name` ON `movies` (`sourceId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_categoryId_name` ON `movies` (`categoryId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_sortOrder_name` ON `movies` (`sourceId`, `sortOrder`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_categoryId_sortOrder_name` ON `movies` (`categoryId`, `sortOrder`, `name`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_sourceId_remoteId` ON `movies` (`sourceId`, `remoteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_sourceId_name` ON `series` (`sourceId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_categoryId_name` ON `series` (`categoryId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_sourceId_sortOrder_name` ON `series` (`sourceId`, `sortOrder`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_categoryId_sortOrder_name` ON `series` (`categoryId`, `sortOrder`, `name`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceId_remoteId` ON `series` (`sourceId`, `remoteId`)")
                // Early v4 dev builds shipped without the EPG guide read-index (it was added while the
                // version stayed 4) — heal them here since the 4→6 hop is a no-op.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId_epgChannelId` ON `epg_programmes` (`sourceId`, `epgChannelId`)")
            }
        }

        /**
         * v8 → v9: incremental EPG sync (PR #40) — `contentHash` on programmes plus the natural-key
         * unique index.
         *
         * D1 rewrite (body only; the resulting schema is unchanged and still matches the committed
         * 9.json): the original ran a row-wise de-dup
         * (`DELETE ... WHERE id NOT IN (SELECT MIN(id) ... GROUP BY ...)`) — a full unindexed
         * self-scan of the largest table, synchronously on first open after upgrade (multi-second
         * hang on big guides). `epg_programmes` is a rebuildable cache with no user data attached,
         * so simply truncate it: the unique index is then free to create on an empty table, and the
         * guide re-downloads on the next EPG sync. Only affects upgrades from v3.2.0 or older
         * (DB ≤ 8); everyone on 4.x already ran the old body (Room never re-runs a completed
         * migration). Lesson for future heavy migrations: probe first or truncate rebuildable
         * caches — never row-wise de-dup a cache table.
         */
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "epg_programmes", "contentHash")) {
                    db.execSQL("ALTER TABLE `epg_programmes` ADD COLUMN `contentHash` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("DELETE FROM `epg_programmes`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_programmes_natural_key` " +
                        "ON `epg_programmes` (`sourceId`, `epgChannelId`, `startMs`)",
                )
            }
        }

        /**
         * v9 → v10: TMDB metadata enrichment cache (plan §7). Two additive, purely-cache tables; no
         * existing table is touched, so this is a safe additive migration.
         */
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `metadata_cache` (" +
                        "`key` TEXT NOT NULL, " +
                        "`tmdbId` INTEGER NOT NULL, " +
                        "`imdbId` TEXT, " +
                        "`type` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`year` INTEGER, " +
                        "`overview` TEXT, " +
                        "`posterPath` TEXT, " +
                        "`backdropPath` TEXT, " +
                        "`rating` REAL, " +
                        "`genresJson` TEXT, " +
                        "`castJson` TEXT, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`)" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_cache_tmdbId` ON `metadata_cache` (`tmdbId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_cache_updatedAt` ON `metadata_cache` (`updatedAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `metadata_match` (" +
                        "`localKey` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`tmdbId` INTEGER, " +
                        "`confidence` REAL NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`localKey`)" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_match_updatedAt` ON `metadata_match` (`updatedAt`)")
            }
        }

        /**
         * v10 → v11: composite indexes for the new "Rating" sort on Movies & Series
         * ("ORDER BY rating DESC, name"). Additive index-only migration; no data or column changes.
         */
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_rating_name` ON `movies` (`sourceId`, `rating`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_categoryId_rating_name` ON `movies` (`categoryId`, `rating`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_sourceId_rating_name` ON `series` (`sourceId`, `rating`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_categoryId_rating_name` ON `series` (`categoryId`, `rating`, `name`)")
            }
        }

        /**
         * v11 → v12: nullable `trailerKey` on the metadata_cache table (in-app YouTube trailers, plan §7.3).
         * Additive column on a pure cache table; existing rows get NULL and simply re-fetch on next refresh.
         */
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `trailerKey` TEXT")
            }
        }

        /**
         * v12 → v13: nullable `logoPath` on the metadata_cache table (Home hero title-logo treatment).
         * Additive column on a pure cache table; existing rows simply use text-title fallback until refreshed.
         */
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata_cache` ADD COLUMN `logoPath` TEXT")
            }
        }

        /**
         * v13 → v14: nullable `mac` on sources for the Stalker portal source type (null for
         * M3U/Xtream). Additive; preserves all user data.
         *
         * Also runs [healSchema]: Room re-validates the ENTIRE schema after any migration, and DBs
         * with runtime index drift (a bulk import interrupted mid-sync leaves BulkInsertHelper's
         * dropped indexes missing) would otherwise fail validation and crash-loop at launch — this
         * bricked 4.0.x → 4.1.0 upgrades for affected users. See [healSchema] for the standing rule.
         */
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "sources", "mac")) {
                    db.execSQL("ALTER TABLE `sources` ADD COLUMN `mac` TEXT")
                }
                healSchema(db)
            }
        }

        /**
         * v14 → v15: external subtitles (subtitle plan Phase 2). Three additive tables — a device-wide
         * `subtitle_cache` of downloaded/imported files, per-profile `subtitle_selection`, and
         * per-subtitle `subtitle_timing`. No existing table is touched, so all user data is preserved.
         *
         * Runs [healSchema] per the standing rule (every final migration must): this is now the last
         * hop in the chain, so it carries the schema-drift heal that a public-release upgrade relies on.
         */
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_cache` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`openSubFileId` INTEGER, " +
                        "`language` TEXT, " +
                        "`languageName` TEXT, " +
                        "`releaseName` TEXT, " +
                        "`format` TEXT, " +
                        "`hearingImpaired` INTEGER NOT NULL DEFAULT 0, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`cachedPath` TEXT NOT NULL, " +
                        "`lastUsedAt` INTEGER NOT NULL" +
                        ")",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subtitle_cache_openSubFileId` ON `subtitle_cache` (`openSubFileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_cache_lastUsedAt` ON `subtitle_cache` (`lastUsedAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_selection` (" +
                        "`profileId` INTEGER NOT NULL, " +
                        "`contentKey` TEXT NOT NULL, " +
                        "`cacheId` INTEGER, " +
                        "`off` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `contentKey`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE, " +
                        "FOREIGN KEY(`cacheId`) REFERENCES `subtitle_cache`(`id`) ON DELETE SET NULL" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_selection_profileId` ON `subtitle_selection` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_selection_cacheId` ON `subtitle_selection` (`cacheId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_timing` (" +
                        "`profileId` INTEGER NOT NULL, " +
                        "`contentKey` TEXT NOT NULL, " +
                        "`subtitleKey` TEXT NOT NULL, " +
                        "`offsetMs` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `contentKey`, `subtitleKey`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_timing_profileId` ON `subtitle_timing` (`profileId`)")

                healSchema(db)
            }
        }

        /**
         * v15 → v16: `subtitle_link` — ties each downloaded subtitle to the movie/episode it was
         * fetched for (subtitle plan §11), so a title's subtitles re-list on replay and the
         * "Delete subtitles" surfaces can browse by Movies/Series. Additive.
         */
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_link` (" +
                        "`profileId` INTEGER NOT NULL, " +
                        "`contentKey` TEXT NOT NULL, " +
                        "`cacheId` INTEGER NOT NULL, " +
                        "`mediaType` TEXT NOT NULL, " +
                        "`contentTitle` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `contentKey`, `cacheId`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE, " +
                        "FOREIGN KEY(`cacheId`) REFERENCES `subtitle_cache`(`id`) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_link_profileId` ON `subtitle_link` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_link_cacheId` ON `subtitle_link` (`cacheId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_link_profileId_mediaType` ON `subtitle_link` (`profileId`, `mediaType`)")
                healSchema(db)
            }
        }

        /**
         * v16 → v17: per-section enabledScope on sources (`syncLive` / `syncMovies` / `syncSeries`).
         * Default On (1) preserves today's "always sync everything" behaviour for existing sources.
         * Off means never fetch AND never show that section — cache is retained. Additive; runs
         * [healSchema] as the new last hop (standing rule).
         */
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "sources", "syncLive")) {
                    db.execSQL("ALTER TABLE `sources` ADD COLUMN `syncLive` INTEGER NOT NULL DEFAULT 1")
                }
                if (!hasColumn(db, "sources", "syncMovies")) {
                    db.execSQL("ALTER TABLE `sources` ADD COLUMN `syncMovies` INTEGER NOT NULL DEFAULT 1")
                }
                if (!hasColumn(db, "sources", "syncSeries")) {
                    db.execSQL("ALTER TABLE `sources` ADD COLUMN `syncSeries` INTEGER NOT NULL DEFAULT 1")
                }
                healSchema(db)
            }
        }

        /**
         * v18: `series.episodesSyncedAt` — when this show's episode list was last fetched.
         * Existing rows default to 0 ("never"), so every already-cached show refreshes its episodes
         * once on next open, which is exactly what an upgrading user needs: the shows frozen by S8
         * pick up their missing episodes without deleting and re-adding the playlist.
         */
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "series", "episodesSyncedAt")) {
                    db.execSQL("ALTER TABLE `series` ADD COLUMN `episodesSyncedAt` INTEGER NOT NULL DEFAULT 0")
                }
                healSchema(db)
            }
        }

        /** v18 → v19: nullable `iconUrl` on epg_channels (XMLTV `<icon src>`, "Prefer EPG logos"). */
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "epg_channels", "iconUrl")) {
                    db.execSQL("ALTER TABLE `epg_channels` ADD COLUMN `iconUrl` TEXT")
                }
                healSchema(db)
            }
        }

        /**
         * v19 → v20: non-unique `(sourceId, number)` index on `channels` for direct-tune channel-number
         * lookup. Additive index-only migration; no data or column changes.
         *
         * Last hop, so it carries [healSchema] (standing rule).
         */
        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_number` ON `channels` (`sourceId`, `number`)")
                healSchema(db)
            }
        }

        /**
         * v20 → v21: `series.addedAt` column + import-time triggers for date-added sorting.
         * Movies already have `addedAt` since the original schema; series did not.
         * Triggers assign the current timestamp to new rows with NULL addedAt (M3U/Stalker
         * sources have no provider date) and preserve existing dates on update (re-sync
         * never overwrites a known date with NULL).
         */
        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // New column: series.addedAt (movies already has it since the original schema).
                db.execSQL("ALTER TABLE series ADD COLUMN addedAt INTEGER")

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS movies_added_at_insert
                    AFTER INSERT ON movies
                    WHEN NEW.addedAt IS NULL
                    BEGIN
                        UPDATE movies SET addedAt = strftime('%s','now') * 1000 WHERE rowid = NEW.rowid;
                    END;
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS movies_added_at_update
                    AFTER UPDATE OF addedAt ON movies
                    WHEN NEW.addedAt IS NULL AND OLD.addedAt IS NOT NULL
                    BEGIN
                        UPDATE movies SET addedAt = OLD.addedAt WHERE rowid = NEW.rowid;
                    END;
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS series_added_at_insert
                    AFTER INSERT ON series
                    WHEN NEW.addedAt IS NULL
                    BEGIN
                        UPDATE series SET addedAt = strftime('%s','now') * 1000 WHERE rowid = NEW.rowid;
                    END;
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS series_added_at_update
                    AFTER UPDATE OF addedAt ON series
                    WHEN NEW.addedAt IS NULL AND OLD.addedAt IS NOT NULL
                    BEGIN
                        UPDATE series SET addedAt = OLD.addedAt WHERE rowid = NEW.rowid;
                    END;
                """.trimIndent())

                db.execSQL("UPDATE movies SET addedAt = strftime('%s','now') * 1000 WHERE addedAt IS NULL")
                db.execSQL("UPDATE series SET addedAt = strftime('%s','now') * 1000 WHERE addedAt IS NULL")
                OwnTVDatabase.healSchema(db)  // ← AÑADIR ESTA LÍNEA (standing rule: last migration heals)
            }
        }

        /**
         * Canonical CREATE statements for every NON-unique index Room expects on the four
         * bulk-synced tables, keyed by table (must stay in sync with the current schema JSON).
         * BulkInsertHelper drops exactly these during eligible fresh imports; restore, the
         * post-import ensure* passes, and [healSchema] all recreate from this one list so a gap
         * can't survive anywhere. Unique indexes are deliberately absent: no code path ever drops
         * them, and re-creating a unique index on unexpected data could itself fail.
         */
        val EXPECTED_NON_UNIQUE_INDEXES: Map<String, List<String>> = mapOf(
            "channels" to listOf(
                "CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_categoryId` ON `channels` (`categoryId`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_name` ON `channels` (`name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_epgChannelId` ON `channels` (`epgChannelId`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_sourceId_name` ON `channels` (`sourceId`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_categoryId_name` ON `channels` (`categoryId`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_sourceId_sortOrder_name` ON `channels` (`sourceId`, `sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_categoryId_sortOrder_name` ON `channels` (`categoryId`, `sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_sourceId_number` ON `channels` (`sourceId`, `number`)",
            ),
            "movies" to listOf(
                "CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_categoryId` ON `movies` (`categoryId`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_name` ON `movies` (`name`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_sourceId_name` ON `movies` (`sourceId`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_categoryId_name` ON `movies` (`categoryId`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_sourceId_sortOrder_name` ON `movies` (`sourceId`, `sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_categoryId_sortOrder_name` ON `movies` (`categoryId`, `sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_sourceId_rating_name` ON `movies` (`sourceId`, `rating`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_movies_categoryId_rating_name` ON `movies` (`categoryId`, `rating`, `name`)",
            ),
            "series" to listOf(
                "CREATE INDEX IF NOT EXISTS `index_series_sourceId` ON `series` (`sourceId`)",
                "CREATE INDEX IF NOT EXISTS `index_series_categoryId` ON `series` (`categoryId`)",
                "CREATE INDEX IF NOT EXISTS `index_series_name` ON `series` (`name`)",
                "CREATE INDEX IF NOT EXISTS `index_series_sourceId_name` ON `series` (`sourceId`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_series_categoryId_name` ON `series` (`categoryId`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_series_sourceId_sortOrder_name` ON `series` (`sourceId`, `sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_series_categoryId_sortOrder_name` ON `series` (`categoryId`, `sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_series_sourceId_rating_name` ON `series` (`sourceId`, `rating`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_series_categoryId_rating_name` ON `series` (`categoryId`, `rating`, `name`)",
            ),
            "epg_programmes" to listOf(
                "CREATE INDEX IF NOT EXISTS `index_epg_programmes_epgChannelId_startMs` ON `epg_programmes` (`epgChannelId`, `startMs`)",
                "CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId` ON `epg_programmes` (`sourceId`)",
                "CREATE INDEX IF NOT EXISTS `index_epg_programmes_stopMs` ON `epg_programmes` (`stopMs`)",
                "CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId_epgChannelId` ON `epg_programmes` (`sourceId`, `epgChannelId`)",
            ),
        )

        /**
         * Room's exact generated DDL for the external-content FTS tables. Only `createAllTables`
         * (fresh install) ever creates them — no migration does — so keep the strings verbatim from
         * the generated OwnTVDatabase_Impl or validation will reject the healed table.
         */
        // internal, not private: the migration test asserts healSchema restores every entry.
        internal val EXPECTED_FTS_TABLES: Map<String, String> = mapOf(
            "channels_fts" to "CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(`name` TEXT NOT NULL, content=`channels`)",
            "movies_fts" to "CREATE VIRTUAL TABLE IF NOT EXISTS `movies_fts` USING FTS4(`name` TEXT NOT NULL, content=`movies`)",
            "series_fts" to "CREATE VIRTUAL TABLE IF NOT EXISTS `series_fts` USING FTS4(`name` TEXT NOT NULL, content=`series`)",
            "episodes_fts" to "CREATE VIRTUAL TABLE IF NOT EXISTS `episodes_fts` USING FTS4(`name` TEXT NOT NULL, content=`episodes`)",
        )

        /**
         * Bring a live database back to the schema Room expects, idempotently (pure no-op on a
         * healthy DB). Recreates the non-unique indexes on the bulk-synced tables and any missing
         * FTS table (rebuilt from its content table when it had to be created).
         *
         * STANDING RULE: every future *final* migration (14 → 15, …) must call this. Room validates
         * the whole schema once, after the last migration in the chain — so the heal only protects
         * an upgrade if it runs in the step users actually pass through.
         */
        fun healSchema(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            EXPECTED_FTS_TABLES.forEach { (name, createSql) ->
                val exists = db.query(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='$name'",
                ).use { it.moveToFirst() }
                if (!exists) {
                    db.execSQL(createSql)
                    db.execSQL("INSERT INTO `$name`(`$name`) VALUES('rebuild')")
                }
            }
            EXPECTED_NON_UNIQUE_INDEXES.values.forEach { statements ->
                statements.forEach { db.execSQL(it) }
            }
        }

        /**
         * Every schema object [healSchema] guarantees: the FTS tables plus the name of each
         * non-unique index, parsed once from the canonical CREATE statements above so the two
         * lists can never drift apart.
         */
        private val EXPECTED_SCHEMA_OBJECTS: List<String> by lazy {
            EXPECTED_FTS_TABLES.keys.toList() +
                EXPECTED_NON_UNIQUE_INDEXES.values.flatten()
                    .map { it.substringAfter("IF NOT EXISTS `").substringBefore('`') }
        }

        /**
         * ST4: the cheap drift probe that gates the heal on the app-open path. One `sqlite_master`
         * count answers "is anything missing?"; on a healthy database — the normal case — that is a
         * single index lookup instead of ~30 `CREATE INDEX IF NOT EXISTS` statements landing on
         * whichever thread issues the first query, at exactly the moment the first grid wants data.
         *
         * Deliberately still inside `onOpen`: a query that ran before the heal finished would hit a
         * missing index, which is the slow path the heal exists to prevent. Returns true if it healed.
         */
        fun healSchemaIfDrifted(db: androidx.sqlite.db.SupportSQLiteDatabase): Boolean {
            val expected = EXPECTED_SCHEMA_OBJECTS
            val placeholders = expected.joinToString(",") { "?" }
            val present = db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE name IN ($placeholders)",
                expected.toTypedArray(),
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (present >= expected.size) return false
            healSchema(db)
            return true
        }

        private fun createContentOrderTable(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `content_order` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`profileId` INTEGER NOT NULL, " +
                    "`mediaType` TEXT NOT NULL, " +
                    "`contextKey` TEXT NOT NULL, " +
                    "`itemId` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE" +
                    ")",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_order_profileId` ON `content_order` (`profileId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_order_profileId_mediaType_contextKey` ON `content_order` (`profileId`, `mediaType`, `contextKey`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_content_order_profileId_mediaType_contextKey_itemId` ON `content_order` (`profileId`, `mediaType`, `contextKey`, `itemId`)")
        }

        private fun hasColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
                return false
            }
        }
    }
}

