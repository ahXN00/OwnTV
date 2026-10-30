package tv.own.owntv.features.automation

import android.content.Context
import android.util.Log
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.MetadataDao

/** Empties the image (Coil) and metadata (TMDB details) caches for the automation API. */
class AutomationCache(private val context: Context, private val metadataDao: MetadataDao) : CacheBackend {

    override suspend fun clear(targets: Set<CacheTarget>) {
        if (CacheTarget.IMAGES in targets) {
            val loader = SingletonImageLoader.get(context)
            withContext(Dispatchers.Main) { loader.memoryCache?.clear() }
            withContext(Dispatchers.IO) { loader.diskCache?.clear() }
        }
        if (CacheTarget.METADATA in targets) metadataDao.clearCache()
        Log.d(TAG, "cache cleared: ${targets.joinToString { it.apiName }}")
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
