package tv.own.owntv.features.shell

import tv.own.owntv.core.nav.MainSection

/**
 * Where to go when the Settings PIN prompt is dismissed: back to the section Settings was opened
 * from, or to More (where Settings lives) when that section is no longer in the rail.
 */
internal fun sectionAfterLockedSettings(previous: MainSection, visible: Set<MainSection>): MainSection =
    when (previous) {
        MainSection.SETTINGS -> MainSection.MORE
        MainSection.SEARCH, MainSection.MORE -> previous
        else -> if (previous in visible) previous else MainSection.MORE
    }
