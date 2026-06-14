package tv.own.owntv.core.repository

import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncManager
import tv.own.owntv.core.sync.SyncResult

/**
 * Adds/links sources to a profile and runs imports. The setup wizard (Phase 6) and playlist screen
 * (Phase 13) drive this; the actual parsing/inserting lives in [SyncManager].
 */
class SourceRepository(
    private val sourceDao: SourceDao,
    private val syncManager: SyncManager,
    private val userData: tv.own.owntv.core.backup.UserDataResolver,
) {
    fun observeSources(profileId: Long): Flow<List<SourceEntity>> = sourceDao.observeForProfile(profileId)

    suspend fun getById(id: Long): SourceEntity? = sourceDao.getById(id)

    suspend fun addXtreamSource(
        profileId: Long, name: String, serverUrl: String, username: String, password: String,
        userAgent: String? = null, epgUrl: String? = null,
    ): SourceEntity = addAndLink(
        profileId,
        SourceEntity(name = name, type = SourceType.XTREAM, url = serverUrl, username = username, password = password, userAgent = userAgent, epgUrl = epgUrl),
    )

    suspend fun addM3uSource(
        profileId: Long, name: String, url: String, userAgent: String? = null, epgUrl: String? = null,
    ): SourceEntity = addAndLink(
        profileId,
        SourceEntity(name = name, type = SourceType.M3U, url = url, userAgent = userAgent, epgUrl = epgUrl),
    )

    private suspend fun addAndLink(profileId: Long, source: SourceEntity): SourceEntity {
        val id = sourceDao.insert(source)
        sourceDao.link(ProfileSourceCrossRef(profileId = profileId, sourceId = id))
        return source.copy(id = id)
    }

    suspend fun deleteSource(source: SourceEntity) = sourceDao.delete(source)

    suspend fun updateSource(source: SourceEntity) = sourceDao.update(source)

    suspend fun sync(source: SourceEntity, onProgress: (ImportStage) -> Unit): SyncResult {
        // Snapshot favorites/history/resume with stable keys BEFORE the sync clears content (their ids
        // change on every refresh, so they'd otherwise orphan — count badge set, list empty).
        val snapshot = runCatching { userData.exportAll() }.getOrNull()
        val result = syncManager.sync(source, onProgress)
        if (result == SyncResult.Success) {
            // Content rows just regenerated — re-attach the snapshot (and any restored backup data) to
            // the new ids, and drop rows the provider removed.
            runCatching { userData.relinkAfterSync(snapshot ?: org.json.JSONArray()) }
        }
        return result
    }
}
