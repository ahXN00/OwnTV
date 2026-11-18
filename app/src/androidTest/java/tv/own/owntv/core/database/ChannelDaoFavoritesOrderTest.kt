package tv.own.owntv.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType

/**
 * Favorites follow the sort chip, not the order the channels were favorited in.
 *
 * The two queries that back Favorites — the paging one the list scrolls and the bounded snapshot the
 * Guide, the in-player channel list and Move use — carry the same ORDER BY, so both are checked here
 * against the same rows. The bug this pins: A–Z was never passed to either of them, so with Favorites
 * selected the list stayed in "most recently added" order (which, when a whole playlist had been
 * favorited in one go, looked like provider blocks) and A–Z appeared to work only inside provider
 * folders.
 */
@RunWith(AndroidJUnit4::class)
class ChannelDaoFavoritesOrderTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OwnTVDatabase
    private lateinit var dao: ChannelDao

    private val profileId = 1L
    private val playlistIds = listOf(10L, 20L)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OwnTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.channelDao()
        runBlocking {
            db.profileDao().insert(ProfileEntity(id = profileId, name = "Me", avatarColor = 0))
            db.sourceDao().insert(SourceEntity(id = 10L, name = "Zattoo", type = SourceType.M3U, url = "https://example.test/a"))
            db.sourceDao().insert(SourceEntity(id = 20L, name = "sat1", type = SourceType.M3U, url = "https://example.test/b"))
            db.categoryDao().upsertAll(
                listOf(
                    tv.own.owntv.core.database.entity.CategoryEntity(
                        id = 100L, sourceId = 10L, mediaType = MediaType.LIVE, name = "Zattoo", sortOrder = 0,
                    ),
                    tv.own.owntv.core.database.entity.CategoryEntity(
                        id = 200L, sourceId = 20L, mediaType = MediaType.LIVE, name = "sat1", sortOrder = 0,
                    ),
                ),
            )
            // The channels are seeded in one playlist's order, and favorited in it too: "Zebra" of the
            // first playlist newest, so addedAt DESC and A–Z disagree whichever way round they are.
            dao.upsertAll(
                listOf(
                    channel(id = 1L, sourceId = 10L, categoryId = 100L, name = "Zebra"),
                    channel(id = 2L, sourceId = 20L, categoryId = 200L, name = "Alpha"),
                    channel(id = 3L, sourceId = 10L, categoryId = 100L, name = "Mike"),
                ),
            )
            favorite(itemId = 1L, addedAt = 300L)
            favorite(itemId = 2L, addedAt = 200L)
            favorite(itemId = 3L, addedAt = 100L)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun snapshot_aToZ_mixesPlaylistsInNameOrder() = runBlocking {
        val rows = dao.snapshotFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, 50, alpha = true)
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.id })
    }

    @Test
    fun snapshot_providerOrder_isMostRecentlyAddedFirst() = runBlocking {
        val rows = dao.snapshotFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, 50, alpha = false)
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.id })
    }

    @Test
    fun paging_aToZ_matchesTheSnapshot() = runBlocking {
        assertEquals(
            listOf(2L, 3L, 1L),
            ids(dao.pagingFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, alpha = true)),
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            ids(dao.pagingFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, alpha = false)),
        )
    }

    /** A channel the user moved keeps its place in both sorts — the rule the folder lists follow too. */
    @Test
    fun movedRowsKeepTheirPosition_inBothSorts() = runBlocking {
        db.contentOrderDao().insertAll(
            listOf(
                ContentOrderEntity(
                    profileId = profileId, mediaType = MediaType.LIVE,
                    contextKey = ContentOrderEntity.FAV_CONTEXT, itemId = 1L, position = 0,
                ),
            ),
        )

        // Zebra moved to the top: it leads A–Z (Alpha, Mike) and the added order (Alpha, Mike) alike.
        assertEquals(
            listOf(1L, 2L, 3L),
            dao.snapshotFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, 50, alpha = true).map { it.id },
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            dao.snapshotFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, 50, alpha = false).map { it.id },
        )
    }

    /**
     * A–Z is case-insensitive: SQLite's default BINARY collation sorts every lowercase name after
     * every uppercase one, so "arte" used to land at the bottom of the list, after "ZDFneo".
     */
    @Test
    fun aToZ_isCaseInsensitive_acrossFavoritesAndOtherLists() = runBlocking {
        dao.upsertAll(
            listOf(
                channel(id = 4L, sourceId = 10L, categoryId = 100L, name = "arte"),
                channel(id = 5L, sourceId = 10L, categoryId = 100L, name = "ZDFneo"),
            ),
        )
        favorite(itemId = 4L, addedAt = 400L)
        favorite(itemId = 5L, addedAt = 500L)

        // alpha, arte, mike, zdfneo, zebra
        assertEquals(
            listOf(2L, 4L, 3L, 5L, 1L),
            dao.snapshotFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, 50, alpha = true).map { it.id },
        )
        assertEquals(
            listOf(2L, 4L, 3L, 5L, 1L),
            ids(dao.pagingFavoritesManual(profileId, ContentOrderEntity.FAV_CONTEXT, playlistIds, alpha = true)),
        )
        // All channels and a provider folder have their own A–Z queries; the same rule applies.
        assertEquals(listOf(2L, 4L, 3L, 5L, 1L), ids(dao.pagingAll(playlistIds)))
        // Folder 100 holds Zebra, Mike, arte and ZDFneo.
        assertEquals(listOf(4L, 3L, 5L, 1L), ids(dao.pagingByCategoryAlpha(100L)))
    }

    // --- helpers ---

    private fun channel(id: Long, sourceId: Long, categoryId: Long, name: String) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        categoryId = categoryId,
        name = name,
        streamUrl = "https://example.test/$id",
        number = id.toInt(),
        sortOrder = id.toInt(),
    )

    private suspend fun favorite(itemId: Long, addedAt: Long) {
        db.favoriteDao().add(
            FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = itemId, addedAt = addedAt),
        )
    }

    /** Page through the whole source once, the way the list on screen would. */
    private suspend fun ids(source: PagingSource<Int, ChannelEntity>): List<Long> {
        val page = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false))
        return (page as PagingSource.LoadResult.Page).data.map { it.id }
    }
}
