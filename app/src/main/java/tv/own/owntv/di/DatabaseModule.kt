package tv.own.owntv.di

import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.core.database.OwnTVDatabase

/**
 * Provides the Room database (WAL journal mode for fast concurrent reads during large imports) and
 * each DAO. Foreign-key enforcement is on by default in Room.
 *
 * There is deliberately NO destructive-migration fallback: the migration chain below covers every
 * shipped version, and a wipe-on-mismatch "safety net" would silently delete a user's profiles,
 * sources, favorites, history and resume positions on the first schema surprise. A missing or
 * failing migration must surface as an error we can fix, not as an empty app.
 */
val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), OwnTVDatabase::class.java, OwnTVDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                OwnTVDatabase.MIGRATION_20_21,
                OwnTVDatabase.MIGRATION_1_2,
                OwnTVDatabase.MIGRATION_2_3,
                OwnTVDatabase.MIGRATION_3_4,
                OwnTVDatabase.MIGRATION_4_6,
                OwnTVDatabase.MIGRATION_6_7,
                OwnTVDatabase.MIGRATION_7_8,
                OwnTVDatabase.MIGRATION_8_9,
                OwnTVDatabase.MIGRATION_9_10,
                OwnTVDatabase.MIGRATION_10_11,
                OwnTVDatabase.MIGRATION_11_12,
                OwnTVDatabase.MIGRATION_12_13,
                OwnTVDatabase.MIGRATION_13_14,
                OwnTVDatabase.MIGRATION_14_15,
                OwnTVDatabase.MIGRATION_15_16,
                OwnTVDatabase.MIGRATION_16_17,
                OwnTVDatabase.MIGRATION_17_18,
                OwnTVDatabase.MIGRATION_18_19,
                OwnTVDatabase.MIGRATION_19_20,
            )
            .addCallback(object : RoomDatabase.Callback() {
                // Self-heal index/FTS drift on every open (no-op when healthy): an interrupted bulk
                // import can leave BulkInsertHelper's dropped indexes missing, which is invisible
                // now but fails Room's full-schema validation at the NEXT version bump (the
                // 4.0.x → 4.1.0 crash-loop). Healing here repairs drift long before that migration.
                //
                // ST4: gated behind a single sqlite_master count so a healthy open pays one index
                // lookup instead of ~30 DDL statements on the thread issuing the first query. The
                // heal itself stays in onOpen — moving it off would let a query beat it to a missing
                // index. The OwnTVPerf timeline reports both the cost and whether it healed.
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    val healed = runCatching { OwnTVDatabase.healSchemaIfDrifted(db) }.getOrDefault(false)
                    tv.own.owntv.Perf.stamp(if (healed) "db-heal(repaired)" else "db-heal(clean)")
                }
            })
            .build()
    }

    single { get<OwnTVDatabase>().profileDao() }
    single { get<OwnTVDatabase>().sourceDao() }
    single { get<OwnTVDatabase>().categoryDao() }
    single { get<OwnTVDatabase>().channelDao() }
    single { get<OwnTVDatabase>().movieDao() }
    single { get<OwnTVDatabase>().seriesDao() }
    single { get<OwnTVDatabase>().favoriteDao() }
    single { get<OwnTVDatabase>().historyDao() }
    single { get<OwnTVDatabase>().progressDao() }
    single { get<OwnTVDatabase>().contentOrderDao() }
    single { get<OwnTVDatabase>().tvProviderProgramDao() }
    single { get<OwnTVDatabase>().downloadDao() }
    single { get<OwnTVDatabase>().epgDao() }
    single { get<OwnTVDatabase>().metadataDao() }
    single { get<OwnTVDatabase>().subtitleDao() }
}
