package tv.own.owntv.features.home

import androidx.compose.runtime.Immutable

import org.json.JSONArray
import org.json.JSONObject

enum class HomeRow(
    val title: String,
    val settingsDesc: String,
    val implemented: Boolean = true,
) {
    HERO("持续观看", "顶部的大型预览行"),
    RECENT_CHANNELS("最近频道", "最近收看的直播频道"),
    FAVORITE_CHANNELS("收藏频道", "您收藏的直播频道"),
    CONTINUE_MOVIES("继续观看电影", "有保存位置的电影"),
    CONTINUE_SERIES("继续观看剧集", "要继续或播放下一个的剧集"),
}

enum class HomeLiveRowMode(
    val label: String,
) {
    CARDS("Cards"),
    ON_NOW("On Now");

    fun toggled(): HomeLiveRowMode = when (this) {
        CARDS -> ON_NOW
        ON_NOW -> CARDS
    }
}

enum class HeroKind {
    LIVE, MOVIES, SERIES,
}

@Immutable
data class HomeConfig(
    val order: List<HomeRow> = HomeRow.entries.toList(),
    val hidden: Set<HomeRow> = setOf(HomeRow.RECENT_CHANNELS),
    val heroIncludeLive: Boolean = true,
    val heroIncludeMovies: Boolean = true,
    val heroIncludeSeries: Boolean = true,
    val recentLiveMode: HomeLiveRowMode = HomeLiveRowMode.CARDS,
    val favoriteLiveMode: HomeLiveRowMode = HomeLiveRowMode.ON_NOW,
) {
    val visibleOrder: List<HomeRow>
        get() = order.filter { it.implemented && it !in hidden }

    val settingsRows: List<HomeRow>
        get() = order.filter { it.implemented }

    fun toJson(): JSONObject = JSONObject().apply {
        put("order", JSONArray(order.map { it.name }))
        put("hidden", JSONArray(hidden.map { it.name }))
        put("heroLive", heroIncludeLive)
        put("heroMovies", heroIncludeMovies)
        put("heroSeries", heroIncludeSeries)
        put("recentLiveMode", recentLiveMode.name)
        put("favoriteLiveMode", favoriteLiveMode.name)
    }

    companion object {
        fun fromJson(raw: String?): HomeConfig {
            if (raw.isNullOrBlank()) return HomeConfig()
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return HomeConfig()
            val storedOrder = obj.optJSONArray("order").toHomeRows()
            val hidden = if (obj.has("hidden")) obj.optJSONArray("hidden").toHomeRows().toSet()
                else HomeConfig().hidden
            return HomeConfig(
                order = mergeOrder(storedOrder),
                hidden = hidden,
                heroIncludeLive = readBool(obj, "heroLive", "heroIncludeLive", default = true),
                heroIncludeMovies = readBool(obj, "heroMovies", "heroIncludeMovies", default = true),
                heroIncludeSeries = readBool(obj, "heroSeries", "heroIncludeSeries", default = true),
                recentLiveMode = readLiveMode(obj, "recentLiveMode", HomeLiveRowMode.CARDS),
                favoriteLiveMode = readLiveMode(obj, "favoriteLiveMode", HomeLiveRowMode.ON_NOW),
            )
        }

        private fun readBool(obj: JSONObject, primary: String, fallback: String, default: Boolean): Boolean =
            when {
                obj.has(primary) -> obj.optBoolean(primary, default)
                obj.has(fallback) -> obj.optBoolean(fallback, default)
                else -> default
            }

        private fun readLiveMode(obj: JSONObject, key: String, default: HomeLiveRowMode): HomeLiveRowMode =
            runCatching { HomeLiveRowMode.valueOf(obj.optString(key)) }.getOrDefault(default)
    }
}

fun mergeOrder(stored: List<HomeRow>): List<HomeRow> {
    val result = LinkedHashSet<HomeRow>()
    stored.forEach { result += it }
    HomeRow.entries.forEachIndexed { index, row ->
        if (row !in result) {
            val current = result.toMutableList()
            current.add(index.coerceAtMost(current.size), row)
            result.clear()
            result += current
        }
    }
    return result.toList()
}

private fun JSONArray?.toHomeRows(): List<HomeRow> {
    if (this == null) return emptyList()
    val out = ArrayList<HomeRow>(length())
    for (i in 0 until length()) {
        val row = runCatching { HomeRow.valueOf(optString(i)) }.getOrNull() ?: continue
        if (row !in out) out += row
    }
    return out
}
