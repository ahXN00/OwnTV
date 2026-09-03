package tv.own.owntv.features.customize

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which way a move (single row or whole span) shifts the affected rows. Shared by the category
 *  list ([CustomizeViewModel]) and the items list ([CustomizeItemsViewModel]). */
enum class MoveKind { UP, DOWN, TOP, BOTTOM }

/**
 * Shifts the contiguous block of [list] rows [lo]..[hi] as one unit and returns the reordered list.
 * The block keeps its internal order; a move that would run off either end returns null (no-op).
 * Shared by both Customize ViewModels, which then persist the result (category order in DataStore,
 * item order via [tv.own.owntv.core.database.dao.ContentOrderDao.replaceContext]).
 */
fun <T> moveBlock(list: List<T>, lo: Int, hi: Int, kind: MoveKind): List<T>? {
    if (lo < 0 || hi > list.lastIndex || lo > hi) return null
    val target = when (kind) {
        MoveKind.UP -> if (lo == 0) return null else lo - 1
        MoveKind.DOWN -> if (hi == list.lastIndex) return null else lo + 1
        MoveKind.TOP -> if (lo == 0) return null else 0
        MoveKind.BOTTOM -> if (hi == list.lastIndex) return null else list.size - (hi - lo + 1)
    }
    val block = list.subList(lo, hi + 1).toList()
    return list.toMutableList().apply {
        subList(lo, hi + 1).clear()
        addAll(target, block)
    }
}

/**
 * Shared span-selection state for both category and item lists. Encapsulates the range anchor, end,
 * mode, and key-in-range computation that [CustomizeViewModel] and [CustomizeItemsViewModel] both
 * need. Persistence (hide/show, move/reorder) stays in each ViewModel — this only tracks the span.
 *
 * @param T  The row type (must provide a stable key).
 * @param rows  StateFlow of the current row list in display order.
 * @param getKey   Extracts the stable key from a row.
 * @param scope    Coroutine scope for [selectedKeys] state-in.
 */
class SpanSelector<T>(
    private val rows: StateFlow<List<T>>,
    private val getKey: (T) -> String,
    scope: CoroutineScope,
) {
    /** What a span selection in progress will do once its end is picked. */
    enum class Mode { HIDE, MOVE, RENAME }

    private val _anchorKey = MutableStateFlow<String?>(null)
    val anchorKey: StateFlow<String?> = _anchorKey.asStateFlow()

    private val _mode = MutableStateFlow(Mode.HIDE)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /** MOVE spans only: once the end has been picked the block stays selected for repeated moves. */
    private val _endKey = MutableStateFlow<String?>(null)
    val endKey: StateFlow<String?> = _endKey.asStateFlow()

    fun beginRange(key: String) {
        _mode.value = Mode.HIDE
        _endKey.value = null
        _anchorKey.value = key
    }

    fun beginMoveRange(key: String) {
        _mode.value = Mode.MOVE
        _endKey.value = null
        _anchorKey.value = key
    }

    /**
     * Starts a RENAME span: the user long-presses a row to anchor, presses another row to extend
     * the span, and the bulk-rename review opens for exactly the rows between the two presses.
     * A single row long-pressed twice (no second press) renames just that row.
     */
    fun beginRenameRange(key: String) {
        _mode.value = Mode.RENAME
        _endKey.value = null
        _anchorKey.value = key
    }

    /**
     * Ends a RENAME span at [key] and returns the span's keys (anchor..[key], inclusive, in
     * displayed order). Clears the span state. Returns null if there is no active RENAME span —
     * the caller then opens single-row rename instead. Also used by the span hint bar, which only
     * wants the keys without clearing.
     */
    fun finishRenameRange(endKey: String): List<String>? {
        val keys = keysBetween(_anchorKey.value ?: return null, endKey) ?: return null
        cancel()
        return keys
    }

    fun cancel() {
        _anchorKey.value = null
        _endKey.value = null
        _mode.value = Mode.HIDE
    }

    /** Sets the end of a MOVE span (called on the first move after anchoring). */
    fun setEndKey(key: String) { _endKey.value = key }

    /**
     * Keys of every row between the current anchor and [endRow], inclusive, in displayed
     * order — independent of which end was picked first. Null if there is no valid anchor.
     */
    fun keysInRange(endRow: T): List<String>? =
        keysBetween(_anchorKey.value ?: return null, getKey(endRow))

    /** Keys of every row between [anchorKey] and [endKey], inclusive, in displayed order. */
    private fun keysBetween(anchorKey: String, endKey: String): List<String>? {
        val current = rows.value
        val anchorIndex = current.indexOfFirst { getKey(it) == anchorKey }
        val endIndex = current.indexOfFirst { getKey(it) == endKey }
        if (anchorIndex < 0 || endIndex < 0) return null
        val lo = minOf(anchorIndex, endIndex)
        val hi = maxOf(anchorIndex, endIndex)
        return current.subList(lo, hi + 1).map { getKey(it) }
    }

    /**
     * Keys of every row in the span currently selected — the anchor alone until an end is picked.
     * Drives the block highlight; empty when no range is in progress.
     */
    val selectedKeys: StateFlow<Set<String>> =
        combine(_anchorKey, _endKey, rows) { anchorKey, endKey, current ->
            if (anchorKey == null) return@combine emptySet()
            val anchorIndex = current.indexOfFirst { getKey(it) == anchorKey }
            if (anchorIndex < 0) return@combine emptySet()
            val endIndex = endKey?.let { k -> current.indexOfFirst { getKey(it) == k } } ?: anchorIndex
            if (endIndex < 0) return@combine setOf(anchorKey)
            current.subList(minOf(anchorIndex, endIndex), maxOf(anchorIndex, endIndex) + 1)
                .mapTo(mutableSetOf()) { getKey(it) }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())
}
