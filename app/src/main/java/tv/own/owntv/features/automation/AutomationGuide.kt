package tv.own.owntv.features.automation

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/**
 * The automation API's guide sources, doing what Settings › EPG does (see EpgSourcesViewModel). How a
 * source is shown, and masked, is [GuideJson]'s job.
 */
class AutomationGuide(
    private val store: EpgSourceStore,
    private val epgRepository: EpgRepository,
    private val epgDao: EpgDao,
    private val settings: SettingsRepository,
    private val scheduler: EpgSyncScheduler,
    private val scope: CoroutineScope,
) : GuideBackend {

    override suspend fun guides(): JSONObject {
        val array = JSONArray()
        store.getAll().forEach { array.put(json(it)) }
        return JSONObject().put("guide", array)
    }

    override suspend fun guide(id: Long): JSONObject? = find(id)?.let { json(it) }

    override suspend fun add(fields: GuideFields): JSONObject {
        val url = requireNotNull(fields.url) { "GuideRequest.parse guarantees a url when adding" }
        val source = store.add(fields.name.orEmpty(), url, fields.userAgent)
        settings.setEpgAutoRefresh(source.id, fields.refresh ?: EpgRefresh.OFF)
        Log.d(TAG, "guide add id=${source.id}")
        scheduler.enqueueSync(source.id, "add")
        return json(source)
    }

    override suspend fun update(id: Long, fields: GuideFields): Boolean {
        val source = find(id) ?: return false
        fields.refresh?.let { settings.setEpgAutoRefresh(id, it) }
        if (fields.name == null && fields.url == null && fields.userAgent == null) return true
        val updated = source.copy(
            name = fields.name?.ifBlank { source.name } ?: source.name,
            url = fields.url ?: source.url,
            // An empty user agent clears it; leaving the field out keeps it.
            userAgent = if (fields.userAgent != null) fields.userAgent.ifBlank { null } else source.userAgent,
        )
        store.update(updated)
        Log.d(TAG, "guide update id=$id")
        scheduler.enqueueSync(id, "update", epgDao.countForSources(listOf(id)))
        return true
    }

    override suspend fun resync(id: Long): Boolean {
        find(id) ?: return false
        scheduler.enqueueSync(id, "manual_resync", epgDao.countForSources(listOf(id)))
        return true
    }

    override suspend fun cancelSync(id: Long): Boolean {
        find(id) ?: return false
        scheduler.cancelSync(id)
        return true
    }

    override suspend fun delete(id: Long): Boolean {
        find(id) ?: return false
        scheduler.cancelSync(id)
        Log.d(TAG, "guide delete id=$id")
        // Programmes first, then the entry, and never half-way: see EpgSourcesViewModel.delete.
        scope.launch {
            withContext(NonCancellable) {
                epgRepository.clear(id)
                store.remove(id)
            }
        }
        return true
    }

    private suspend fun find(id: Long): EpgSource? = store.getAll().firstOrNull { it.id == id }

    private suspend fun json(source: EpgSource): JSONObject = GuideJson.of(
        source = source,
        refresh = settings.epgAutoRefresh.first()[source.id] ?: EpgRefresh.OFF,
        channels = epgDao.countGuideChannels(listOf(source.id)),
        programmes = epgDao.countForSources(listOf(source.id)),
        sync = scheduler.observeSync(source.id).first(),
    )

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
