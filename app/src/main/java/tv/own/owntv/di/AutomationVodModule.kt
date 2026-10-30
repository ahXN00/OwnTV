package tv.own.owntv.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import tv.own.owntv.features.automation.ApiRouteSet
import tv.own.owntv.features.automation.AutomationVod
import tv.own.owntv.features.automation.vodRoutes

/**
 * Automation API: the vod area. Its routes are an ApiRouteSet the automation module picks up. It shares
 * the ui area's profile, catalog and gate, so a show opens the same way a section does.
 */
val automationVodModule = module {
    single(named("vod")) {
        val backend = AutomationVod(
            catalog = get(),
            seriesDao = get(),
            seriesRepository = get(),
            profile = get(),
            launcher = get(),
            gate = get(),
        )
        ApiRouteSet { vodRoutes(backend) }
    }
}
