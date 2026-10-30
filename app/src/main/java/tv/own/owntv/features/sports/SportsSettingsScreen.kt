package tv.own.owntv.features.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.sports.AutoRebuild
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.MinQuality
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsRefreshPolicy
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsHeading
import tv.own.owntv.features.settings.StageSettingsNote
import tv.own.owntv.features.settings.settingHelp
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.theme.mpx

/** The pages of Settings → Sports: the main page and the ones its rows open. */
private enum class SportsSettingsPage { MAIN, LEAGUES, TEAMS, ALERTS, LANGUAGES, COUNTRIES, PLAYLISTS, REMEMBERED, ABOUT }

/** Commentary languages offered (ISO 639-1, upper case): the ones provider prefixes and names carry. */
private val LANGUAGES = listOf("DE", "EN", "FR", "ES", "IT", "TR", "AR", "PT", "NL", "PL", "RU", "EL", "RO", "SV", "NO", "DA", "FI", "HU", "CS", "UR", "HI", "BN")

/** Channel countries offered: the provider prefixes ("DE|"), two letters as Settings keeps them. */
private val COUNTRIES = listOf("DE", "AT", "CH", "UK", "US", "FR", "BE", "ES", "IT", "TR", "AR", "PT", "BR", "NL", "PL", "IN", "PK", "SA", "AE", "QA", "CA", "AU", "IE", "SE", "NO", "DK")

/**
 * Settings → Sports, drawn as every Settings page is, with its sub-pages: Sports
 * and leagues (two panes: sports, then the chosen sport's leagues), My teams (search + suggested), Goal
 * alerts (the TV's in-player toast only), languages, countries, playlists, Remembered channels and About
 * sports data. Every value is the profile's; backups carry it.
 */
@Composable
fun SportsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, vm: SportsSettingsViewModel = koinViewModel()) {
    val c by vm.config.collectAsStateWithLifecycle()
    val shown by vm.shownInMenu.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(SportsSettingsPage.MAIN) }
    val title = stringResource(R.string.sports_title)
    val settings = stringResource(R.string.common_nav_settings)
    val main = { page = SportsSettingsPage.MAIN }
    // Each page opens with focus on its first row, as every Settings page does (not left on the rail).
    val rowsFocus = remember(page) { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(page) {
        for (attempt in 0 until 10) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { rowsFocus.requestFocus() }.getOrDefault(false)) break
        }
    }
    when (page) {
        SportsSettingsPage.MAIN -> StageFullPage(
            parents = emptyList(), title = title,
            count = pluralStringResource(R.plurals.settings_setting_count, MAIN_ROWS, MAIN_ROWS),
            onBack = onBack, modifier = modifier, rowsFocus = rowsFocus,
        ) { MainRows(vm, c, shown) { page = it } }
        SportsSettingsPage.LEAGUES -> StageFullPage(
            parents = listOf(settings, title),
            title = stringResource(page.titleRes),
            count = "",
            onBack = main, modifier = modifier, rowsFocus = rowsFocus,
            list = { pos -> LeaguePicker(vm, c, rowsFocus, pos) },
        )
        else -> StageFullPage(
            parents = listOf(settings, title),
            title = stringResource(page.titleRes),
            count = "",
            onBack = main, modifier = modifier, rowsFocus = rowsFocus,
        ) {
            when (page) {
                SportsSettingsPage.TEAMS -> TeamRows(vm, c)
                SportsSettingsPage.ALERTS -> AlertRows(vm, c)
                SportsSettingsPage.LANGUAGES -> CodeRows(LANGUAGES, c.preferredLanguages(vm.appLanguage), { languageName(it) }) { code ->
                    vm.update { s -> val now = s.preferredLanguages(vm.appLanguage); s.copy(languages = if (code in now) now - code else now + code) }
                }
                SportsSettingsPage.COUNTRIES -> CodeRows(COUNTRIES, c.countries, { it }) { code ->
                    vm.update { s -> s.copy(countries = if (code in s.countries) s.countries - code else s.countries + code) }
                }
                SportsSettingsPage.PLAYLISTS -> PlaylistRows(vm, c)
                SportsSettingsPage.REMEMBERED -> RememberedRows(vm, c)
                SportsSettingsPage.ABOUT -> StageSettingsNote(stringResource(R.string.sports_about_summary), stringResource(R.string.sports_live_refresh_description))
                SportsSettingsPage.MAIN, SportsSettingsPage.LEAGUES -> Unit
            }
        }
    }
}

private val SportsSettingsPage.titleRes: Int get() = when (this) {
    SportsSettingsPage.MAIN -> R.string.sports_title
    SportsSettingsPage.LEAGUES -> R.string.sports_leagues
    SportsSettingsPage.TEAMS -> R.string.sports_my_teams
    SportsSettingsPage.ALERTS -> R.string.sports_channel_goal_alerts
    SportsSettingsPage.LANGUAGES -> R.string.sports_languages
    SportsSettingsPage.COUNTRIES -> R.string.sports_countries
    SportsSettingsPage.PLAYLISTS -> R.string.settings_sort_playlist
    SportsSettingsPage.REMEMBERED -> R.string.sports_remembered_channels
    SportsSettingsPage.ABOUT -> R.string.sports_about
}

/** One row with its panel text, as every Settings page builds them. */
@Composable
private fun Row(icon: OwnTVIcon, title: String, desc: String?, value: SettingValue?, onStep: ((Int) -> Unit)? = null, onClick: () -> Unit) {
    val choices = (value as? SettingValue.Segmented)?.options.orEmpty()
    StageSettingRow(
        icon = icon, title = title, desc = desc, value = value, onClick = onClick, onStep = onStep,
        help = settingHelp(null, title, desc, value, choices = choices, pinnable = false),
    )
}

/** Rows on the main page (the "N settings" beside its title): 5 + 6 + 3 + 2 + 4. */
private const val MAIN_ROWS = 20

/** The main page: What to show · Watching · While watching · Channels · Data. */
@Composable
private fun MainRows(vm: SportsSettingsViewModel, c: SportsConfig, shown: Boolean, open: (SportsSettingsPage) -> Unit) {
    val sep = stringResource(R.string.content_epg_bits_separator)
    val leagues = c.leagues
    val sports = SportsCatalog.sports.filter { s -> leagues.any { SportsCatalog.league(it)?.sport == s } }

    StageSettingsHeading(stringResource(R.string.sports_settings_what), 5, first = true)
    Row(OwnTVIcon.TILES, stringResource(R.string.sports_show_in_menu), stringResource(R.string.sports_show_in_menu_description), SettingValue.Switch(shown)) { vm.setShownInMenu(!shown) }
    Row(
        OwnTVIcon.LAYERS, stringResource(R.string.sports_leagues),
        (sports.take(4).map { sportLabel(it) } + pluralStringResource(R.plurals.sports_league_count, leagues.size, leagues.size)).joinToString(sep),
        SettingValue.Opens(stringResource(R.string.common_edit)),
    ) { open(SportsSettingsPage.LEAGUES) }
    val buttons = listOf(stringResource(R.string.sports_sport_buttons_mine), stringResource(R.string.sports_sport_buttons_all))
    val toggleAll: (Int) -> Unit = { vm.update { s -> s.copy(allSports = !s.allSports) } }
    Row(OwnTVIcon.TILES, stringResource(R.string.sports_sport_buttons), stringResource(R.string.sports_sport_buttons_description),
        SettingValue.Segmented(buttons, if (c.allSports) 1 else 0), onStep = toggleAll) { toggleAll(1) }
    Row(OwnTVIcon.STAR, stringResource(R.string.sports_my_teams), c.teams.joinToString(", ") { it.name }.ifBlank { null }, SettingValue.Opens(c.teams.size.toString())) { open(SportsSettingsPage.TEAMS) }
    Row(OwnTVIcon.EYE_OFF, stringResource(R.string.sports_spoiler_free), stringResource(R.string.sports_spoiler_free_description), SettingValue.Switch(c.spoilerFree)) {
        vm.update { it.copy(spoilerFree = !it.spoilerFree) }
    }

    StageSettingsHeading(stringResource(R.string.sports_settings_watching), 6)
    val langs = c.preferredLanguages(vm.appLanguage).map { languageName(it) }
    Row(OwnTVIcon.LANGUAGE, stringResource(R.string.sports_languages), stringResource(R.string.sports_languages_description),
        SettingValue.Opens(langs.joinToString(", ").ifBlank { stringResource(R.string.sports_any) })) { open(SportsSettingsPage.LANGUAGES) }
    Row(OwnTVIcon.NETWORK, stringResource(R.string.sports_countries), stringResource(R.string.sports_countries_description),
        SettingValue.Opens(c.countries.joinToString(", ").ifBlank { stringResource(R.string.sports_any) })) { open(SportsSettingsPage.COUNTRIES) }
    val qualities = MinQuality.entries.map { qualityLabel(it) }
    val stepQuality: (Int) -> Unit = { d -> vm.update { s -> s.copy(minQuality = MinQuality.entries[(s.minQuality.ordinal + d).mod(MinQuality.entries.size)]) } }
    Row(OwnTVIcon.VIDEO, stringResource(R.string.settings_vp_cat_picture), stringResource(R.string.sports_picture_description),
        SettingValue.Choice(qualities[c.minQuality.ordinal]), onStep = stepQuality) { stepQuality(1) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    // Only this device's playlists count: ids restored from another device name nothing here (core WatchFilter.from).
    val chosenLists = playlists.filter { it.first in c.sourceIds }
    Row(OwnTVIcon.PLAYLIST, stringResource(R.string.settings_sort_playlist), null,
        SettingValue.Opens(if (chosenLists.isEmpty()) stringResource(R.string.sports_all_playlists) else chosenLists.joinToString(", ") { it.second })) { open(SportsSettingsPage.PLAYLISTS) }
    Row(OwnTVIcon.EPG, stringResource(R.string.sports_only_confirmed), stringResource(R.string.sports_only_confirmed_description), SettingValue.Switch(c.onlyConfirmed)) {
        vm.update { it.copy(onlyConfirmed = !it.onlyConfirmed) }
    }
    Row(OwnTVIcon.LAYERS, stringResource(R.string.sports_strict_filter), stringResource(R.string.sports_strict_filter_description), SettingValue.Switch(c.strictFilter)) {
        vm.update { it.copy(strictFilter = !it.strictFilter) }
    }

    StageSettingsHeading(stringResource(R.string.sports_settings_while_watching), 3)
    Row(OwnTVIcon.LIVE_TV, stringResource(R.string.sports_score_info_bar), stringResource(R.string.sports_score_info_bar_description), SettingValue.Switch(c.scoreInInfoBar)) {
        vm.update { it.copy(scoreInInfoBar = !it.scoreInInfoBar) }
    }
    Row(OwnTVIcon.BELL, stringResource(R.string.sports_channel_goal_alerts), stringResource(R.string.sports_goal_alerts_description), SettingValue.Opens(alertsSummary(c))) { open(SportsSettingsPage.ALERTS) }
    val leads = SportsConfig.REMINDER_LEAD_CHOICES
    val stepLead: (Int) -> Unit = { d -> vm.update { s -> s.copy(reminderLeadMinutes = leads[(leads.indexOf(s.reminderLeadMinutes).coerceAtLeast(0) + d).mod(leads.size)]) } }
    Row(OwnTVIcon.CLOCK, stringResource(R.string.sports_channel_match_reminders), stringResource(R.string.sports_match_reminders_description),
        SettingValue.Stepper(pluralStringResource(R.plurals.recording_minutes, c.reminderLeadMinutes, c.reminderLeadMinutes)), onStep = stepLead) { stepLead(1) }

    StageSettingsHeading(stringResource(R.string.content_channel_overlay_title), 2)
    val first = c.rememberedChannels.entries.firstOrNull()
    val rememberedLine = first?.let { (k, ch) ->
        val name = SportsCatalog.league(k)?.name ?: k.substringAfter('/')
        if (c.rememberedChannels.size == 1) stringResource(R.string.sports_remembered_one, name, ch.name)
        else pluralStringResource(R.plurals.sports_remembered_summary, c.rememberedChannels.size - 1, name, ch.name, c.rememberedChannels.size - 1)
    }
    Row(OwnTVIcon.PLAYLIST, stringResource(R.string.sports_remembered_channels), rememberedLine, SettingValue.Opens(c.rememberedChannels.size.toString())) { open(SportsSettingsPage.REMEMBERED) }
    Row(OwnTVIcon.EPG, stringResource(R.string.sports_show_likely), stringResource(R.string.sports_show_likely_description), SettingValue.Switch(c.showLikely)) {
        vm.update { it.copy(showLikely = !it.showLikely) }
    }

    StageSettingsHeading(stringResource(R.string.sports_settings_data), 4)
    val modes = AutoRebuild.entries
    val stepMode: (Int) -> Unit = { d -> vm.update { s -> s.copy(autoRebuild = modes[(s.autoRebuild.ordinal + d).mod(modes.size)]) } }
    Row(OwnTVIcon.HISTORY, stringResource(R.string.sports_auto_rebuild), stringResource(R.string.sports_auto_rebuild_description),
        SettingValue.Stepper(rebuildLabel(c.autoRebuild)), onStep = stepMode) { stepMode(1) }
    val refresh = SportsRefreshPolicy.LIVE_SECONDS_CHOICES
    val stepRefresh: (Int) -> Unit = { d -> vm.update { s -> s.copy(liveRefreshSeconds = refresh[(refresh.indexOf(s.liveRefreshSeconds).coerceAtLeast(0) + d).mod(refresh.size)]) } }
    Row(OwnTVIcon.LIVE_TV, stringResource(R.string.sports_live_refresh), stringResource(R.string.sports_live_refresh_description),
        SettingValue.Stepper(pluralStringResource(R.plurals.settings_nav_hide_after_seconds, c.liveRefreshSeconds, c.liveRefreshSeconds)), onStep = stepRefresh) { stepRefresh(1) }
    val rebuilding by vm.rebuilding.collectAsStateWithLifecycle()
    val rebuiltAt by vm.rebuiltAt.collectAsStateWithLifecycle()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val last = rebuiltAt?.let { at -> (dayOf(at, locale) ?: stringResource(R.string.content_epg_today)) + sep + timeText(at) }
    Row(OwnTVIcon.REFRESH, stringResource(R.string.sports_refresh_data_now),
        if (rebuilding) stringResource(R.string.sports_rebuilding) else last?.let { stringResource(R.string.sports_last_updated, it) }, null) { if (!rebuilding) vm.rebuild() }
    Row(OwnTVIcon.INFO, stringResource(R.string.sports_about), stringResource(R.string.sports_about_summary), SettingValue.Opens(null)) { open(SportsSettingsPage.ABOUT) }
}

/** "My teams", "Followed leagues", both, or Off — what Goal alerts covers on this profile. */
@Composable
private fun alertsSummary(c: SportsConfig): String {
    val a = c.alerts
    if (!a.enabled) return stringResource(R.string.common_off)
    return listOfNotNull(
        stringResource(R.string.sports_my_teams).takeIf { a.myTeams },
        stringResource(R.string.sports_followed_leagues).takeIf { a.followedLeagues },
    ).joinToString(", ").ifBlank { stringResource(R.string.common_on) }
}

/** An Auto rebuild choice as the row shows it: "Every 4 hours", "On every app start", "Manual". */
@Composable
private fun rebuildLabel(mode: AutoRebuild): String = when (mode) {
    AutoRebuild.APP_START -> stringResource(R.string.sports_auto_rebuild_app_start)
    AutoRebuild.MANUAL -> stringResource(R.string.settings_manual)
    else -> SportsRefreshPolicy.hoursOf(mode).let { h -> pluralStringResource(R.plurals.sports_auto_rebuild_hours, h, h) }
}

/** One reorderable list in Sports and leagues: the sports, or one sport's chosen leagues. */
private class MoveList(val title: String, val keys: List<String>, val names: List<String>, val start: Int, val commit: (List<String>) -> Unit)

/**
 * Sports and leagues, two panes: the sports on the left, each with how many of its leagues are
 * chosen; the focused sport's leagues on the right, each a switch, the chosen ones first in the user's
 * order. OK or ▶ on a sport moves into its leagues. Hold OK on a sport or a chosen league opens the
 * Move window ([MovePopup]).
 * That order is the hub's and Home's.
 */
@Composable
private fun LeaguePicker(vm: SportsSettingsViewModel, c: SportsConfig, rowsFocus: androidx.compose.ui.focus.FocusRequester, modifier: Modifier) {
    val sports = c.orderSports(SportsCatalog.sports)
    var selected by rememberSaveable { mutableStateOf(sports.first()) }
    val leaguesFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // The leagues pane is ordered once per sport (and after a Move), not on every switch: a league that
    // jumped to the top when switched on took the focus with it.
    var orderVersion by remember { mutableStateOf(0) }
    // A saved Move re-orders the pane once the new order has reached the settings (the save is async).
    var reorderPending by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(c.leagues) { if (reorderPending) { reorderPending = false; orderVersion++ } }
    val sportFocus = remember { SportsCatalog.sports.associateWith { androidx.compose.ui.focus.FocusRequester() } }
    // Hold OK opens the Move overlay below straight away (no menu in between).
    var moving by remember { mutableStateOf<MoveList?>(null) }
    val warning = stringResource(R.string.sports_load_time_warning)
    val holdOk = stringResource(R.string.content_key_hold_ok)
    val move = stringResource(R.string.content_move)
    val back = stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings)
    val sportNames = sports.map { sportLabel(it) }
    val cricketName = stringResource(R.string.sports_sport_cricket)
    fun startMove(list: MoveList) { moving = list }
    fun leagueName(l: tv.own.owntv.core.sports.model.League) = if (l.key == "cricket/all") cricketName else l.name
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.mpx)) {
        androidx.compose.foundation.layout.Column(
            Modifier.width(380.mpx).fillMaxHeight().focusRequester(rowsFocus).focusGroup()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 40.mpx),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.mpx),
        ) {
            sports.forEachIndexed { i, s ->
                androidx.compose.runtime.key(s) {
                    val chosen = SportsCatalog.leaguesOf(s).count { it.key in c.leagues }
                    val label = sportNames[i]
                    val value = SettingValue.Opens(chosen.takeIf { it > 0 }?.toString())
                    val help = settingHelp(null, label, warning, value, pinnable = false).let { h ->
                        h.copy(hints = listOf(stringResource(R.string.common_ok) to stringResource(R.string.settings_key_open), holdOk to move, back))
                    }
                    StageSettingRow(
                        icon = OwnTVIcon.SPORTS, title = label, desc = null, value = value,
                        onClick = { runCatching { leaguesFocus.requestFocus() } },
                        onLongClick = {
                            startMove(MoveList(label, sports.map { it.name }, sportNames, i) { keys ->
                                vm.setSportOrder(keys.mapNotNull { k -> tv.own.owntv.core.sports.model.Sport.entries.firstOrNull { it.name == k } })
                            })
                        },
                        help = help,
                        modifier = Modifier.focusRequester(sportFocus.getValue(s)).onFocusChanged { if (it.isFocused) selected = s },
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Column(
            // ◀ from a league returns to the sport it belongs to, not the nearest row (which would switch this pane).
            Modifier.weight(1f).fillMaxHeight().focusRequester(leaguesFocus)
                .onPreviewKeyEvent { e ->
                    val left = e.key == androidx.compose.ui.input.key.Key.DirectionLeft
                    if (left && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) runCatching { sportFocus.getValue(selected).requestFocus() }
                    left
                }
                .focusGroup()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 40.mpx),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.mpx),
        ) {
            // The chosen leagues first, in the user's order, then the rest in catalogue order.
            val all = SportsCatalog.leaguesOf(selected)
            val chosenHere = c.leagues.mapNotNull { k -> all.firstOrNull { it.key == k } }
            val ordered = remember(selected, orderVersion) { chosenHere + all.filter { it !in chosenHere } }
            ordered.forEach { l ->
                androidx.compose.runtime.key(l.key) {
                    val name = leagueName(l)
                    val on = l.key in c.leagues
                    val value = SettingValue.Switch(on)
                    val help = settingHelp(null, name, l.country, value, pinnable = false).let { h ->
                        if (on) h.copy(hints = h.hints.dropLast(1) + (holdOk to move) + back) else h
                    }
                    StageSettingRow(
                        icon = OwnTVIcon.SPORTS, title = name, desc = l.country, value = value,
                        onClick = { vm.toggleLeague(l.key) },
                        // Only a chosen league has a place in the order, and only with another one to swap with.
                        // Never null: switching between a plain and a long-click handler drops the focus.
                        onLongClick = {
                            if (on && chosenHere.size > 1) {
                                startMove(MoveList(name, chosenHere.map { it.key }, chosenHere.map { leagueName(it) }, chosenHere.indexOf(l)) { keys ->
                                    vm.setLeagueOrder(keys)
                                })
                            }
                        },
                        help = help,
                    )
                }
            }
        }
    }

    moving?.let { list -> MovePopup(list, move, onDone = { moving = null; reorderPending = true }, onCancel = { moving = null }) }
}

/**
 * The Move window of Sports and leagues, in Stage form and above the help panel: ▲ ▼ choose a row, OK
 * picks it up and puts it down, so several can move in one go; only Save or Cancel closes it.
 */
@Composable
private fun MovePopup(list: MoveList, title: String, onDone: () -> Unit, onCancel: () -> Unit) {
    // Positions into list.keys, in the new order; picked = the one being carried.
    var order by remember { mutableStateOf(list.keys.indices.toList()) }
    var picked by remember { mutableStateOf<Int?>(list.start) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    val accent = tv.own.owntv.ui.theme.stageAccent.accent
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onCancel, title = title, body = stringResource(R.string.sports_move_instructions), width = 760.mpx, scroll = false,
        // Opened by a held OK: the guard keeps its repeats from dropping the item it opens carrying.
        modifier = Modifier.longPressMenuGuard(),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onCancel, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_save), height = 56.mpx, textSize = 19, tinted = true,
                onClick = { list.commit(order.map { list.keys[it] }); onDone() })
        },
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f, fill = false).fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.mpx),
        ) {
            items(order.size, key = { order[it] }) { pos ->
                val item = order[pos]
                val carried = picked == item
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = list.names[item],
                    chosen = carried,
                    onClick = { picked = if (carried) null else item },
                    // A carried row moves with ▲ ▼; its key keeps the focus on it as it goes.
                    modifier = (if (item == list.start) Modifier.focusRequester(first) else Modifier).onPreviewKeyEvent { e ->
                        if (!carried || e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val to = when (e.key) {
                            androidx.compose.ui.input.key.Key.DirectionUp -> pos - 1
                            androidx.compose.ui.input.key.Key.DirectionDown -> pos + 1
                            else -> return@onPreviewKeyEvent false
                        }
                        if (to in order.indices) order = order.toMutableList().apply { add(to, removeAt(pos)) }
                        true
                    },
                    leading = { _ -> tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.MOVE, if (carried) accent else tv.own.owntv.ui.theme.StageColors.Dim) },
                )
            }
        }
    }
}

/** The Playlist filter: All playlists, or the chosen ones (this device's live playlists). */
@Composable
private fun PlaylistRows(vm: SportsSettingsViewModel, c: SportsConfig) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val chosen = playlists.map { it.first }.filter { it in c.sourceIds }.toSet()
    Row(OwnTVIcon.PLAYLIST, stringResource(R.string.sports_all_playlists), null, SettingValue.Switch(chosen.isEmpty())) {
        vm.update { it.copy(sourceIds = emptySet()) }
    }
    playlists.forEach { (id, name) ->
        Row(OwnTVIcon.PLAYLIST, name, null, SettingValue.Switch(id in chosen)) {
            vm.update { s -> s.copy(sourceIds = if (id in chosen) chosen - id else chosen + id) }
        }
    }
}

/** My teams: the followed ones (OK stops following), a search over the followed leagues' teams, and suggestions. */
@Composable
private fun TeamRows(vm: SportsSettingsViewModel, c: SportsConfig) {
    var query by remember { mutableStateOf("") }
    val searchTitle = stringResource(R.string.sports_search_teams)
    StageFieldRow(
        icon = OwnTVIcon.SEARCH, label = searchTitle, value = query, onValueChange = { query = it },
        help = settingHelp(null, searchTitle, null, null, pinnable = false), placeholder = searchTitle,
    )
    val found by produceState(emptyList<FollowedTeam>(), query) { value = vm.searchTeams(query) }
    found.forEach { t ->
        val on = c.teams.any { it.key == t.key }
        Row(OwnTVIcon.STAR, t.name, sportLabel(t.sport), SettingValue.Switch(on)) { vm.toggleTeam(t) }
    }
    if (c.teams.isNotEmpty()) {
        StageSettingsHeading(stringResource(R.string.sports_my_teams), c.teams.size)
        c.teams.forEach { t -> Row(OwnTVIcon.STAR, t.name, sportLabel(t.sport), SettingValue.Switch(true)) { vm.toggleTeam(t) } }
    }
    val suggestions = vm.suggestions(c)
    if (suggestions.isNotEmpty()) {
        StageSettingsHeading(stringResource(R.string.sports_suggested), suggestions.size)
        suggestions.forEach { s ->
            val line = if (s.team != null) pluralStringResource(R.plurals.sports_watched_matches, s.plays, s.plays) else stringResource(R.string.sports_league_label)
            Row(OwnTVIcon.STAR, s.name, line, SettingValue.Action(stringResource(R.string.sports_follow))) {
                val team = s.team
                if (team != null) vm.toggleTeam(team) else vm.toggleLeague(s.key)
            }
        }
    }
}

/** Goal alerts on the TV: the in-player toast only (no background alerts on a television). */
@Composable
private fun AlertRows(vm: SportsSettingsViewModel, c: SportsConfig) {
    val a = c.alerts
    Row(OwnTVIcon.BELL, stringResource(R.string.sports_channel_goal_alerts), stringResource(R.string.sports_goal_alerts_description), SettingValue.Switch(a.enabled)) {
        vm.update { it.copy(alerts = it.alerts.copy(enabled = !it.alerts.enabled)) }
    }
    StageSettingsHeading(stringResource(R.string.sports_alerts_for), 2)
    Row(OwnTVIcon.STAR, stringResource(R.string.sports_my_teams), null, SettingValue.Switch(a.myTeams)) {
        vm.update { it.copy(alerts = it.alerts.copy(myTeams = !it.alerts.myTeams)) }
    }
    Row(OwnTVIcon.LAYERS, stringResource(R.string.sports_followed_leagues), stringResource(R.string.sports_followed_leagues_description), SettingValue.Switch(a.followedLeagues)) {
        vm.update { it.copy(alerts = it.alerts.copy(followedLeagues = !it.alerts.followedLeagues)) }
    }
}

/** A list of codes, each a switch: the commentary languages or the countries. */
@Composable
private fun CodeRows(codes: List<String>, chosen: Set<String>, label: @Composable (String) -> String, onToggle: (String) -> Unit) {
    codes.forEach { code -> Row(OwnTVIcon.LANGUAGE, label(code), code, SettingValue.Switch(code in chosen)) { onToggle(code) } }
}

/** Remembered channels: "Bundesliga → Sky Sport Bundesliga 1"; OK forgets it. */
@Composable
private fun RememberedRows(vm: SportsSettingsViewModel, c: SportsConfig) {
    if (c.rememberedChannels.isEmpty()) {
        StageSettingsNote(stringResource(R.string.sports_remembered_channels), null)
        return
    }
    c.rememberedChannels.forEach { (key, ch) ->
        val name = SportsCatalog.league(key)?.name ?: key.substringAfter('/')
        Row(OwnTVIcon.PLAYLIST, name, ch.name, SettingValue.Action(stringResource(R.string.common_delete))) {
            vm.update { it.copy(rememberedChannels = it.rememberedChannels - key) }
        }
    }
}
