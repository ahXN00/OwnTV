package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtCategory
import tv.own.owntv.core.parser.XtreamClient
import kotlin.coroutines.CoroutineContext

/**
 * The Xtream import flow (split out of SyncManager, Phase 0 of the Stalker plan): re-syncs preserve
 * existing rows until a phase succeeds — rows are matched by provider remote id, unchanged rows are
 * skipped, changed rows keep their local id, and stale rows are pruned only after the full phase
 * completes. Series episodes are intentionally fetched lazily later (Phase 9), not during sync.
 */
internal class XtreamSyncer(
    private val xtream: XtreamClient,
    private val bulkInsertHelper: BulkInsertHelper,
    private val support: SyncSupport,
) {
    suspend fun sync(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector, contentTypes: SyncContentTypes) {
        val semaphore = Semaphore(2)
        Log.i(TAG, "Xtream sync scheduling sourceId=${s.id} contentTypes=$contentTypes concurrency=2")
        coroutineScope {
            if (contentTypes.live) async { semaphore.withPermit { syncLive(s, progress, stats) } }
            if (contentTypes.movies) async { semaphore.withPermit { syncMovies(s, progress, stats) } }
            if (contentTypes.series) async { semaphore.withPermit { syncSeries(s, progress, stats) } }
        }
    }

    // C5: the three Xtream phases share one generic scaffolding (syncXtreamPhase) — they differ only
    // in phase/table names, the entity mapper, and per-entity DAO lambdas (ContentAdapter). Live is
    // NOT wrapped in guardStep (a live failure fails the whole sync, as before); movies/series are.

    private suspend fun syncLive(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) =
        syncXtreamPhase(
            s, progress, stats,
            XtreamPhase(
                phase = SyncPhase.LIVE, type = MediaType.LIVE,
                table = "channels", ftsTable = "channels_fts",
                countsKey = "channels", timingKey = "live",
                adapter = support.channelAdapter,
                fetchCategories = { report -> xtream.liveCategories(s, report) },
                makeStreams = { catMap ->
                    var order = 0
                    val toChannel = {
                        streamId: String,
                        name: String,
                        icon: String?,
                        epgChannelId: String?,
                        categoryId: String?,
                        num: Int?,
                        archive: Boolean,
                        archiveDays: Int ->
                        ChannelEntity(
                            sourceId = s.id, categoryId = catMap[categoryId], name = name,
                            logoUrl = icon, streamUrl = xtream.liveUrl(s, streamId),
                            epgChannelId = epgChannelId, number = num, remoteId = streamId,
                            sortOrder = order++,
                            catchup = archive, catchupDays = archiveDays,
                        )
                    }
                    XtreamStreams(
                        bulk = { add -> xtream.streamLive(s, transform = toChannel, onItem = add, onProgress = SyncSupport.IgnoreByteProgress) },
                        byCategory = { cat, add -> xtream.streamLive(s, cat.id, transform = toChannel, onItem = add, onProgress = SyncSupport.IgnoreByteProgress) },
                    )
                },
            ),
        )

    private suspend fun syncMovies(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) {
        guardStep("movies", stats) {
            syncXtreamPhase(
                s, progress, stats,
                XtreamPhase(
                    phase = SyncPhase.MOVIES, type = MediaType.MOVIE,
                    table = "movies", ftsTable = "movies_fts",
                    countsKey = "movies",
                    adapter = support.movieAdapter,
                    fetchCategories = { report -> xtream.vodCategories(s, report) },
                    makeStreams = { catMap ->
                        var order = 0
                        val toMovie = {
                            streamId: String,
                            name: String,
                            icon: String?,
                            rating: Double?,
                            plot: String?,
                            categoryId: String?,
                            containerExt: String?,
                            added: Long? ->
                            MovieEntity(
                                sourceId = s.id, categoryId = catMap[categoryId], name = name,
                                posterUrl = icon, rating = rating, plot = plot,
                                streamUrl = xtream.movieUrl(s, streamId, containerExt),
                                containerExt = containerExt, remoteId = streamId,
                                addedAt = added?.takeIf { it > 0L }?.let { if (it < 10_000_000_000L) it * 1000L else it },
                                sortOrder = order++,
                            )
                        }
                        XtreamStreams(
                            bulk = { add -> xtream.streamVod(s, transform = toMovie, onItem = add, onProgress = SyncSupport.IgnoreByteProgress) },
                            byCategory = { cat, add -> xtream.streamVod(s, cat.id, transform = toMovie, onItem = add, onProgress = SyncSupport.IgnoreByteProgress) },
                        )
                    },
                ),
            )
        }
    }

    private suspend fun syncSeries(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) {
        guardStep("series", stats) {
            syncXtreamPhase(
                s, progress, stats,
                XtreamPhase(
                    phase = SyncPhase.SERIES, type = MediaType.SERIES,
                    table = "series", ftsTable = "series_fts",
                    countsKey = "series",
                    adapter = support.seriesAdapter,
                    fetchCategories = { report -> xtream.seriesCategories(s, report) },
                    makeStreams = { catMap ->
                        var order = 0
                        val toSeries = {
                            seriesId: String,
                            name: String,
                            cover: String?,
                            plot: String?,
                            rating: Double?,
                            categoryId: String?,
                            year: Int?, added: Long?, lastModified: Long? ->
                            SeriesEntity(
                                sourceId = s.id, categoryId = catMap[categoryId], name = name,
                                posterUrl = cover, plot = plot, rating = rating,
                                year = year, remoteId = seriesId,
                                addedAt = (lastModified ?: added)?.takeIf { it > 0L }?.let {
                                    if (it < 10_000_000_000L) it * 1000L else it
                                },
                                sortOrder = order++,
                            )
                        }
                        XtreamStreams(
                            bulk = { add -> xtream.streamSeries(s, transform = toSeries, onItem = add, onProgress = SyncSupport.IgnoreByteProgress) },
                            byCategory = { cat, add -> xtream.streamSeries(s, cat.id, transform = toSeries, onItem = add, onProgress = SyncSupport.IgnoreByteProgress) },
                        )
                    },
                ),
            )
        }
    }

    /** Per-phase wiring for [syncXtreamPhase]: names/keys plus the entity mapper and streams. */
    private class XtreamPhase<T>(
        val phase: SyncPhase,
        val type: MediaType,
        val table: String,
        val ftsTable: String,
        val countsKey: String,
        /** Only Live records its own timing key; movies/series get theirs from [guardStep]. */
        val timingKey: String? = null,
        val adapter: ContentAdapter<T>,
        val fetchCategories: suspend (report: (Long, Long?) -> Unit) -> List<XtCategory>,
        /** Built AFTER the category refresh so the mapper can resolve category remote ids → db ids.
         *  The mapper's running sortOrder is shared between bulk and fallback (as before). */
        val makeStreams: (catMap: Map<String, Long>) -> XtreamStreams<T>,
    )

    private class XtreamStreams<T>(
        val bulk: suspend (add: suspend (T) -> Unit) -> Boolean,
        val byCategory: suspend (cat: XtCategory, add: suspend (T) -> Unit) -> Boolean,
    )

    /**
     * One Xtream content phase (C5 — extracted from the three near-identical copies):
     * category refresh → bulk stream (fresh insert or hash-diffed stable upsert) → per-category
     * fallback when the bulk list errors/truncates → prune (only after a COMPLETE bulk pass).
     */
    private suspend fun <T> syncXtreamPhase(
        s: SourceEntity,
        progress: SyncCounters,
        stats: SyncStatsCollector,
        p: XtreamPhase<T>,
    ) = coroutineScope {
        val ctx = currentCoroutineContext()
        val freshSource = s.lastSyncAt == null
        val phaseStart = System.currentTimeMillis()
        val elapsedStart = SystemClock.elapsedRealtime()
        val label = p.phase.label
        Log.i(TAG, "$label phase start sourceId=${s.id} fresh=$freshSource")
        progress.update(p.phase, 0)
        val hashDeferred = if (!freshSource) support.asyncHashLoad(this, label, s.id) { p.adapter.loadHashes(s.id) } else null
        val categoriesStart = SystemClock.elapsedRealtime()
        val cats = p.fetchCategories(SyncSupport.IgnoreByteProgress)
        Log.d(TAG, "$label categories fetched sourceId=${s.id} count=${cats.size} ms=${SystemClock.elapsedRealtime() - categoriesStart}")
        val refreshStart = SystemClock.elapsedRealtime()
        val categories = support.refreshCategories(s, p.type, cats, stats)
        Log.d(TAG, "$label categories refreshed sourceId=${s.id} mapped=${categories.idsByRemoteId.size} ms=${SystemClock.elapsedRealtime() - refreshStart}")
        val streams = p.makeStreams(categories.idsByRemoteId)
        val insertFn: suspend (List<T>) -> UpsertStats = if (freshSource) {
            { rows -> support.insertFresh(rows, p.adapter) }
        } else {
            { rows -> support.upsertStable(rows, hashDeferred!!, p.adapter) }
        }
        val total = intArrayOf(0)
        val remoteIds = if (freshSource) null else HashSet<String>()
        var done = false
        bulkInsertHelper.withOptimizedBulkInsert(
            p.table,
            p.ftsTable,
            eligible = freshSource,
            ftsOnly = true,
        ) {
            val bulkStart = SystemClock.elapsedRealtime()
            Log.i(TAG, "$label bulk start sourceId=${s.id}")
            val chunkSize = if (freshSource) BulkInsertHelper.CHUNK_FRESH else BulkInsertHelper.CHUNK
            done = bulkOrFallback(label) {
                support.chunked<T, Boolean>(ctx, p.phase, label, progress, insertFn, total, remoteIds, p.adapter.remoteIdOf, chunkSize) { add ->
                    streams.bulk(add)
                }
            }
            Log.i(TAG, "$label bulk end sourceId=${s.id} complete=$done unique=${total[0]} ms=${SystemClock.elapsedRealtime() - bulkStart}")
            if (!freshSource && done) {
                support.pruneRemoteIds(label, s.id, remoteIds!!, stats, p.adapter.remoteIdsForSource, p.adapter.deleteByRemoteIds)
                support.pruneCategories(s.id, p.type, categories.seenRemoteIds, label, stats)
            } else if (!freshSource) {
                Log.i(TAG, "$label prune skipped sourceId=${s.id} reason=incomplete_bulk")
            }
        }
        if (!done) {
            stats.usedFallback = true
            val fallbackStart = SystemClock.elapsedRealtime()
            Log.i(TAG, "$label fallback start sourceId=${s.id} categories=${cats.size} bulkPartial=${total[0]}")
            val outcome: FallbackOutcome = sliceByCategory(ctx, p.phase, label, progress, cats, insertFn, total, total[0], remoteIds, p.adapter.remoteIdOf) { cat, add ->
                streams.byCategory(cat, add)
            }
            Log.i(TAG, "$label fallback end sourceId=${s.id} unique=${total[0]} ms=${SystemClock.elapsedRealtime() - fallbackStart}")
            if (!freshSource) pruneAfterFallback(s, p, label, categories, outcome, remoteIds!!, stats)
        }
        progress.update(p.phase, total[0])
        p.timingKey?.let { stats.phaseTiming[it] = System.currentTimeMillis() - phaseStart }
        stats.processedCounts[p.countsKey] = total[0]
        Log.i(TAG, "$label phase end sourceId=${s.id} unique=${total[0]} ms=${SystemClock.elapsedRealtime() - elapsedStart}")
    }

    /**
     * Prune after a per-category fallback (S2). Before this, a fallback run never pruned at all, so
     * titles the provider had removed stayed in the catalog forever on any panel that can't serve the
     * bulk list.
     *
     * The prune is scoped to the categories that were fetched *completely*, never the whole source.
     * That is not just caution about partial failures: a per-category request can never return an
     * item that has no category, so a source-wide prune here would delete every uncategorized row.
     * Scoping also gives partial pruning for free — a run where half the categories failed still
     * cleans the half that succeeded. The Phase 0 catalog-shrink guard applies on top, with the
     * scoped row count as its denominator.
     */
    private suspend fun <T> pruneAfterFallback(
        s: SourceEntity,
        p: XtreamPhase<T>,
        label: String,
        categories: CategoryRefresh,
        outcome: FallbackOutcome,
        seenRemoteIds: Set<String>,
        stats: SyncStatsCollector,
    ) {
        val inCategories = p.adapter.remoteIdsInCategories
        val categoryIds = outcome.pruneScope(categories.idsByRemoteId)
        if (inCategories == null || categoryIds.isEmpty()) {
            Log.i(
                TAG,
                "$label prune skipped sourceId=${s.id} reason=fallback_no_complete_category " +
                    "succeeded=${outcome.succeededCategoryRemoteIds.size} aborted=${outcome.aborted} stoppedEarly=${outcome.stoppedEarly}",
            )
            return
        }
        Log.i(TAG, "$label fallback prune scope sourceId=${s.id} categories=${categoryIds.size}/${outcome.attempted} complete=${outcome.complete}")
        support.pruneRemoteIds(
            label = "$label fallback",
            sourceId = s.id,
            seenRemoteIds = seenRemoteIds,
            stats = stats,
            loadExisting = { src -> categoryIds.chunked(SyncSupport.QUERY_CHUNK).flatMap { inCategories(src, it) } },
            deleteRemoteIds = p.adapter.deleteByRemoteIds,
        )
        // The category list itself was fetched in full before the fallback began, so it is safe to
        // trim removed categories even though some of their item lists failed. Deleting a category
        // only nulls its items' categoryId (SET_NULL); it never deletes items.
        support.pruneCategories(s.id, p.type, categories.seenRemoteIds, label, stats)
    }

    private suspend inline fun guardStep(phase: String, stats: SyncStatsCollector, block: suspend () -> Unit) {
        val start = System.currentTimeMillis()
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "$phase import failed — keeping the rest of the import", e)
            stats.phaseErrors[phase] = e.message ?: "unknown"
        } finally {
            stats.phaseTiming[phase] = System.currentTimeMillis() - start
        }
    }

    /**
     * Run a bulk list fetch; if it ERRORS (not just truncates), return false so the caller drops to the
     * smaller per-category requests. Some panels (e.g. peoplestv) return a non-standard HTTP 512 on the giant
     * full `get_series` / `get_vod_streams` response but serve the per-category (`&category_id=X`) requests
     * fine — without this, the bulk error skipped straight past the per-category fallback.
     */
    private suspend inline fun bulkOrFallback(label: String, bulk: suspend () -> Boolean): Boolean =
        try {
            bulk()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "$label bulk fetch failed (${e.message}) — falling back to per-category requests", e)
            false
        }

    /**
     * Fallback for when a provider truncates the single bulk list (issue #15): re-fetch one category at
     * a time (`&category_id=X`, tiny payloads) and upsert progressively. The table is NOT cleared first,
     * so any items the partial bulk already inserted (incl. uncategorized ones missing from the category
     * list) survive — the unique `(sourceId, remoteId)` index dedupes the overlap. [total] is shared with
     * the bulk pass so progress keeps climbing instead of resetting per category.
     *
     * Some panels IGNORE `category_id` and return the whole (truncating) list for every category — that
     * would loop forever re-fetching the same data. If a single category returns ~the bulk's whole count
     * and still truncates (or several categories can't be served), we stop and keep what we have.
     */
    private suspend fun <T> sliceByCategory(
        ctx: CoroutineContext,
        phase: SyncPhase,
        label: String,
        progress: SyncCounters,
        categories: List<XtCategory>,
        insert: suspend (List<T>) -> UpsertStats,
        total: IntArray,
        bulkPartial: Int,
        seenKeys: MutableSet<String>? = null,
        uniqueKey: ((T) -> String?)? = null,
        stream: suspend (cat: XtCategory, add: suspend (T) -> Unit) -> Boolean,
    ): FallbackOutcome {
        var truncations = 0
        val succeeded = ArrayList<String>(categories.size)
        var aborted = false
        var stoppedEarly = false
        Log.i(TAG, "$label fallback categories begin count=${categories.size} bulkPartial=$bulkPartial currentUnique=${total[0]}")
        categories.forEachIndexed loop@{ index, cat ->
            if (aborted || stoppedEarly) return@loop
            ctx.ensureActive()
            // Gentle pacing between the many small requests so we don't trip a rate-limiter (HTTP 429)
            // while looping through every category.
            if (index > 0) delay(SyncSupport.CATEGORY_REQUEST_DELAY_MS)
            val categoryStart = SystemClock.elapsedRealtime()
            val before = total[0]
            Log.d(TAG, "$label fallback category start index=${index + 1}/${categories.size} id=${cat.id} name=${cat.name} beforeUnique=$before")
            // A single failing category (e.g. it 512s/429s) is skipped — keep importing the other categories
            // rather than losing the whole section.
            val complete = try {
                support.chunked<T, Boolean>(ctx, phase, label, progress, insert, total, seenKeys, uniqueKey) { add -> stream(cat) { add(it) } }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // HTTP errors (like 512 "response too large") are specific to one category — skip it and
                // try the next. Network errors (timeout, DNS, connection refused) mean the server is
                // unreachable — abort the entire fallback so we don't spin for minutes retrying every
                // category against a dead server.
                val isServerError = e.message?.startsWith("HTTP") == true
                android.util.Log.w("SyncManager", "$label: category ${cat.id} failed (${e.message}) — ${if (isServerError) "skipping category" else "ABORTING fallback"}", e)
                if (!isServerError) aborted = true
                return@loop
            }
            val delta = total[0] - before
            Log.d(
                TAG,
                "$label fallback category end index=${index + 1}/${categories.size} id=${cat.id} " +
                    "complete=$complete newUnique=$delta totalUnique=${total[0]} ms=${SystemClock.elapsedRealtime() - categoryStart}",
            )
            if (complete) {
                succeeded.add(cat.id)
            } else {
                truncations++
                if ((bulkPartial > 0 && delta >= bulkPartial) || truncations >= 3) {
                    android.util.Log.w(
                        "SyncManager",
                        "$label: per-category fetch still truncating (panel likely ignores category_id) — stopping fallback after ${total[0]} items",
                    )
                    stoppedEarly = true // stop the fallback entirely
                }
            }
        }
        Log.i(
            TAG,
            "$label fallback categories end totalUnique=${total[0]} truncations=$truncations " +
                "succeeded=${succeeded.size}/${categories.size} aborted=$aborted stoppedEarly=$stoppedEarly",
        )
        return FallbackOutcome(
            succeededCategoryRemoteIds = succeeded,
            aborted = aborted,
            stoppedEarly = stoppedEarly,
            attempted = categories.size,
        )
    }

    companion object {
        private const val TAG = SyncSupport.TAG
    }
}
