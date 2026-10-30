package tv.own.owntv.features.automation

/**
 * A group of API routes declared in its own Koin module (`single(named("…")) { ApiRouteSet { … } }`).
 * The automation module serves every set it finds, so an area adds routes without editing the shared list.
 */
fun interface ApiRouteSet {
    fun routes(): List<ApiRoute>
}
