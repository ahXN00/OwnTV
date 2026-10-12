package tv.own.owntv.tifo.search

import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.tifo.sports.SportsMatching

/**
 * The Search tab's query, without the playlist's separator rows ("##### NBC #####"). Core lists
 * channels by name and keeps the first [SearchReader.LIMIT], and "#" sorts before every letter, so
 * a broadcaster with many separators ("NBC") filled the list with them and no real channel. This
 * reads wider, drops the separators, then keeps as many channels as a broadcaster can have.
 */
object TifoSearch {

    suspend fun search(reader: SearchReader, profileId: Long, sources: ActiveProfileSources, query: String): SearchResults {
        val wide = reader.search(profileId, sources, query, WIDE_LIMIT)
        return wide.copy(
            channels = wide.channels.filterNot { SportsMatching.isSeparator(it.channel.name) }.take(MAX_CHANNELS),
            movies = wide.movies.take(SearchReader.LIMIT),
            series = wide.series.take(SearchReader.LIMIT),
        )
    }

    /** How many rows of each kind are read before the separators are dropped. */
    private const val WIDE_LIMIT = 600

    /** How many channels a search lists: every "NBC" channel of a big playlist. */
    private const val MAX_CHANNELS = 200
}
