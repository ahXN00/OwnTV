package tv.own.owntv.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.home.durationText
import tv.own.owntv.features.live.LivePlate
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageGroupLabel
import tv.own.owntv.ui.stage.StageLiveBadge
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageSwitch
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/*
 * The TV Guide in the Stage design (P4-01 … P4-05): the preview and the focused programme on top, the
 * ruler, the channel labels, and every menu the guide opens. EpgScreen owns the state and the focus
 * plumbing; these only draw.
 */

private const val Tabular = "tnum"

/**
 * The top-left video (640×360, radius 28): the focused channel through Live TV's preview player — no
 * second decoder — with the LIVE badge and "259 · Sky Cinema Family · Toy Story 4" along the bottom.
 */
@Composable
internal fun GuideVideo(
    channel: ChannelEntity?,
    channelLine: String?,
    nowTitle: String?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewState by previewEngine.state.collectAsStateWithLifecycle()
    val playing = showVideo && previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val r = 28.mpx
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .drawBehind { drawBoxShadow(Color.Black.copy(alpha = 0.55f), 80.mpx.toPx(), r.toPx(), dy = 30.mpx.toPx()) }
            .clip(RoundedCornerShape(r))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (channel != null) {
            ChannelLogoTile(logoUrl = channel.displayLogoUrl, modifier = Modifier.size(200.mpx), fill = Color.Transparent) {
                OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(48.mpx))
            }
        }
        if (playing) tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
        if (showVideo && previewState == tv.own.owntv.player.LivePreviewEngine.State.LOADING) OwnTVSpinner(sizeDp = 24)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
        Box(Modifier.fillMaxSize().drawBehind { drawInnerRing(Color.White.copy(alpha = 0.08f), 1.mpx.toPx(), r.toPx()) })
        if (channel != null) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 20.mpx, top = 18.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
            Text(
                buildAnnotatedString {
                    append(channelLine.orEmpty())
                    if (!nowTitle.isNullOrBlank()) withStyle(SpanStyle(color = StageColors.Muted, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(" · $nowTitle") }
                },
                style = stageText(19, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.mpx, end = 22.mpx, bottom = 18.mpx),
            )
        }
    }
}

/** "Starts in 58 min · 20:05 – 21:40 · 95 min · Sky Cinema Family" — or "Live now · …", or just the times once it has been on. */
@Composable
internal fun programmeEyebrow(p: EpgProgrammeEntity, channelName: String, now: Long): String {
    val formatTime = rememberSystemTimeFormatter()
    val range = stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs))
    val lead = when {
        p.startMs > now -> stringResource(R.string.content_epg_starts_in, durationText(((p.startMs - now) + 59_999) / 60_000))
        p.stopMs > now -> stringResource(R.string.content_live_now)
        else -> null
    }
    val length = ((p.stopMs - p.startMs) / 60_000L).toInt().takeIf { it > 0 }?.let { stringResource(R.string.player_duration_minutes, it) }
    return listOfNotNull(lead, range, length, channelName).joinToString(" · ")
}

/** The details line (G1): "Animation · Family · 2023 · FSK 0 · 95 min" — only what the feed provides. */
@Composable
internal fun programmeDetailsLine(p: EpgProgrammeEntity): String? {
    val d = p.details
    val length = d.lengthMin?.let { stringResource(R.string.player_duration_minutes, it) }
    return (d.categoryList + listOfNotNull(d.episode, d.year?.toString(), d.rating, length))
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The programme block beside the video: eyebrow (accent, clock), title 60/800, details, 2-line synopsis. */
@Composable
internal fun GuideProgrammeBlock(
    eyebrow: String?,
    title: String?,
    details: String?,
    synopsis: String?,
    modifier: Modifier = Modifier,
) {
    val a = stageAccent
    Column(modifier) {
        if (eyebrow != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVIcon(OwnTVIcon.CLOCK, a.accent, Modifier.size(18.mpx))
                Text(eyebrow, style = stageText(18, 700, 0.02.em), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (title != null) {
            Text(
                title, style = stageText(60, 800, (-1.5).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.mpx, bottom = 6.mpx),
            )
        }
        if (details != null) Text(details, style = stageText(19, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            synopsis.orEmpty(),
            style = stageText(19, 400).copy(lineHeight = (19 * 1.5f).mpxSp), color = Color(0xFFCCD6D2),
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 1000.mpx).padding(top = 12.mpx, bottom = 20.mpx),
        )
    }
}

/**
 * The ruler above the grid: a time every half hour (17/700 muted, tabular), none within 12 minutes of
 * now, and the accent chip on the now-line ("19:07"). Drawn from the shared scroll position.
 */
@Composable
internal fun GuideRuler(
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    scrollPx: Int,
    modifier: Modifier = Modifier,
) {
    val formatTime = rememberSystemTimeFormatter()
    val a = stageAccent
    val slotMs = GuideGridDefaults.SlotMin * 60_000L
    val first = windowStart + (slotMs - windowStart % slotMs) % slotMs
    val times = generateSequence(first) { it + slotMs }.takeWhile { it < windowEnd }
        .filter { kotlin.math.abs(it - now) > 12 * 60_000L }.toList()
    val nowIn = now in windowStart..windowEnd
    Layout(
        modifier = modifier,
        content = {
            times.forEach { t ->
                Text(formatTime(t), style = stageText(17, 700).copy(fontFeatureSettings = Tabular), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (nowIn) {
                Text(
                    formatTime(now), style = stageText(15, 800).copy(fontFeatureSettings = Tabular), color = a.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(a.accent, RoundedCornerShape(8.mpx)).padding(horizontal = 9.mpx, vertical = 3.mpx),
                )
            }
        },
    ) { measurables, constraints ->
        val ppm = GuideGridDefaults.PxPerMin.toPx()
        val chipShift = 34.mpx.toPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, pl ->
                val isChip = nowIn && i == placeables.lastIndex
                val t = if (isChip) now else times[i]
                val x = ((t - windowStart) / 60_000f * ppm - scrollPx - if (isChip) chipShift else 0f).toInt()
                if (x + pl.width < 0 || x > constraints.maxWidth) return@forEachIndexed
                // The chip sits 4 higher than the labels (top 518 against 522).
                pl.place(IntOffset(x, if (isChip) 0 else 4.mpx.roundToPx()))
            }
        }
    }
}

/**
 * A channel's label column (P4-01): the playlist's 7 px dot, the number (38 wide, 17/700 dim), the
 * logo on a 58×40 plate, the name 17/700. The row the focus is in has its number and name in accent.
 */
@Composable
internal fun GuideChannelLabel(channel: ChannelEntity, name: String, dot: Color?, selected: Boolean, numberWidth: Dp, modifier: Modifier = Modifier) {
    val a = stageAccent
    Row(
        modifier.padding(start = 8.mpx),
        horizontalArrangement = Arrangement.spacedBy(10.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.mpx).background(dot ?: Color.Transparent, RoundedCornerShape(50)))
        Text(
            channel.number?.toString().orEmpty(),
            style = stageText(17, 700).copy(fontFeatureSettings = Tabular), color = if (selected) a.accent else StageColors.Dim,
            // Always whole (owner): the column fits the longest number in the list; only the name is cut.
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(numberWidth),
        )
        LivePlate(channel.displayLogoUrl, 58.mpx, 40.mpx, radius = 10.mpx)
        Text(name, style = stageText(17, 700), color = if (selected) a.accent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** The 3 px accent now-line with its glow, over the rows. */
internal fun Modifier.guideNowLine(accent: Color): Modifier = drawBehind {
    val w = 3.mpx.toPx()
    drawBoxShadow(accent, 14.mpx.toPx(), w / 2f)
    drawRoundRect(accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f))
}

// ---------------------------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------------------------

/** Every guide menu: the Stage menu over the scrim, at the mockup's place ([x] of 1920, [top]), scrolling at large zooms. */
@Composable
internal fun GuideMenuHost(x: Float, top: Dp, width: Dp, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onDismiss() }
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f)).longPressMenuGuard()) {
            StageMenu(
                Modifier
                    .padding(start = (maxWidth * (x / 1920f)).coerceAtMost(maxWidth - width), top = top, bottom = 24.mpx)
                    .width(width)
                    .verticalScroll(rememberScrollState())
                    .trapAllFocusExit()
                    .focusGroup(),
                content = content,
            )
        }
    }
}

/** What a programme can do right now, worked out by the screen; null actions are shown disabled. */
internal class GuideProgrammeActions(
    val reminderSet: Boolean,
    val onReminder: (() -> Unit)?,
    val onWatchChannel: () -> Unit,
    val onWatchFromStart: (() -> Unit)?,
    val startsAt: Long,
    val catchupPlayer: SettingsRepository.CatchupPlayer,
    val onCatchupPlayer: (SettingsRepository.CatchupPlayer) -> Unit,
    val recordLabel: String,
    val onRecord: (() -> Unit)?,
    val seriesActive: Boolean,
    val onSeries: (() -> Unit)?,
    val onRecordFromCatchup: (() -> Unit)?,
    val isFavorite: Boolean,
    val onFavorite: () -> Unit,
    val onPickEpg: (() -> Unit)?,
    /** Auto-match this one channel by name; its result opens in the review popup. */
    val onAutoMatch: (() -> Unit)?,
    val epgOffsetValue: String?,
    val onEpgOffset: (() -> Unit)?,
)

/**
 * What [p] on [ch] can do at [now], through the guide's view model: Remind me until it starts, Watch
 * from start once the archive has it, Record / Stop / Cancel by the recording already there, Record
 * every showing while it is still to come, Record from catch-up after it. The TV Guide and Live TV's
 * schedule both build their menu from this, so the two never disagree.
 */
@Composable
internal fun guideProgrammeActions(
    vm: EpgViewModel,
    ch: ChannelEntity,
    p: EpgProgrammeEntity,
    now: Long,
    onWatch: () -> Unit,
    onPlayFromStart: () -> Unit,
    onPickEpg: () -> Unit,
    onEpgOffset: () -> Unit,
    /** Only where the review popup is on screen to show the result (the guide, not Live TV's list). */
    autoMatch: Boolean = true,
): GuideProgrammeActions {
    val recordingRows by vm.recordingRows.collectAsStateWithLifecycle()
    val reminderRows by vm.reminderRows.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteChannelIds.collectAsStateWithLifecycle()
    val catchupPlayer by vm.catchupPlayer.collectAsStateWithLifecycle()
    val recording = remember(recordingRows, ch.id, p.startMs) { vm.recordingFor(ch, p) }
    val seriesRule by androidx.compose.runtime.produceState<tv.own.owntv.core.database.entity.RecordingRuleEntity?>(null, ch.id, p.title, recordingRows) {
        value = vm.seriesRuleFor(ch, p)
    }
    val canCatchup = vm.canCatchup(ch, p, now)
    val upcoming = p.stopMs > now
    val status = recording?.status
    return GuideProgrammeActions(
        reminderSet = reminderRows.any { it.channelId == ch.id && it.startMs == p.startMs },
        onReminder = if (p.startMs > now) ({ vm.toggleReminder(ch, p) }) else null,
        onWatchChannel = onWatch,
        onWatchFromStart = if (canCatchup) onPlayFromStart else null,
        startsAt = p.startMs,
        catchupPlayer = catchupPlayer,
        onCatchupPlayer = vm::setCatchupPlayer,
        recordLabel = stringResource(
            when (status) {
                tv.own.owntv.core.model.RecordingStatus.RECORDING -> R.string.recording_stop
                tv.own.owntv.core.model.RecordingStatus.SCHEDULED -> R.string.common_cancel
                else -> R.string.recording_record
            },
        ),
        onRecord = when (status) {
            tv.own.owntv.core.model.RecordingStatus.RECORDING -> ({ vm.stopRecording(recording) })
            tv.own.owntv.core.model.RecordingStatus.SCHEDULED -> ({ vm.cancelRecording(recording) })
            else -> if (upcoming) ({ vm.record(ch, p) }) else null
        },
        seriesActive = seriesRule != null,
        onSeries = if (upcoming) ({ seriesRule?.let { vm.stopSeries(it) } ?: vm.recordSeries(ch, p) }) else null,
        onRecordFromCatchup = if (!upcoming && canCatchup && recording == null) ({ vm.record(ch, p) }) else null,
        isFavorite = ch.id in favoriteIds,
        onFavorite = { vm.toggleFavoriteChannel(ch) },
        onPickEpg = onPickEpg,
        onAutoMatch = if (autoMatch) ({ vm.autoMatchOne(ch) }) else null,
        epgOffsetValue = vm.currentEpgShift(ch)?.let { tv.own.owntv.features.live.liveEpgShiftLabel(it) }
            ?: stringResource(R.string.content_epg_offset_global_short),
        onEpgOffset = onEpgOffset,
    )
}

/**
 * ☰ Programme options (P4-02), 560 wide at the right: "Wish" + "20:05 – 21:40 · 259 Sky Cinema Family";
 * NEW Remind me (5 min before) · WATCH Watch channel, Watch from start ("after 20:05" until it starts),
 * Play catch-up in… (Ask) · RECORD Record, Record every showing, Record from catch-up · CHANNEL
 * favourite, Pick EPG manually, EPG time offset (Global). Also Live TV's ▶ Schedule → OK menu.
 */
@Composable
internal fun GuideProgrammeMenu(title: String, subtitle: String, actions: GuideProgrammeActions, leadMinutes: Int, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val formatTime = rememberSystemTimeFormatter()
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val player = actions.catchupPlayer
    val playerLabel = stringResource(
        when (player) {
            SettingsRepository.CatchupPlayer.ASK -> R.string.settings_catchup_player_ask
            SettingsRepository.CatchupPlayer.INTERNAL -> R.string.settings_catchup_player_internal
            SettingsRepository.CatchupPlayer.EXTERNAL -> R.string.settings_catchup_player_external
        },
    )
    GuideMenuHost(x = 1150f, top = 64.mpx, width = 560.mpx, onDismiss = onDismiss) {
        StageMenuHeader(title = title, subtitle = subtitle)
        StageGroupLabel(stringResource(R.string.content_menu_group_new).uppercase(locale))
        StageMenuItem(
            text = stringResource(if (actions.reminderSet) R.string.content_reminder_set else R.string.content_remind_me),
            icon = OwnTVIcon.BELL,
            value = if (actions.reminderSet) null else tv.own.owntv.features.live.reminderLeadText(leadMinutes),
            checked = actions.reminderSet,
            enabled = actions.onReminder != null,
            onClick = { actions.onReminder?.invoke(); onDismiss() },
            modifier = if (actions.onReminder != null) Modifier.focusRequester(focus) else Modifier,
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_watch).uppercase(locale))
        StageMenuItem(
            text = stringResource(R.string.content_epg_watch_channel), icon = OwnTVIcon.LIVE_TV,
            onClick = { onDismiss(); actions.onWatchChannel() },
            modifier = if (actions.onReminder == null) Modifier.focusRequester(focus) else Modifier,
        )
        StageMenuItem(
            text = stringResource(R.string.content_epg_watch_start), icon = OwnTVIcon.REWIND,
            value = if (actions.onWatchFromStart == null && actions.startsAt > System.currentTimeMillis()) {
                stringResource(R.string.content_epg_after_time, formatTime(actions.startsAt))
            } else null,
            enabled = actions.onWatchFromStart != null,
            onClick = { onDismiss(); actions.onWatchFromStart?.invoke() },
        )
        // Steps through the three choices in place: the value is the setting, and the menu stays open.
        StageMenuItem(
            text = stringResource(R.string.settings_catchup_player) + "…", icon = OwnTVIcon.EXTERNAL,
            value = playerLabel,
            onClick = {
                val all = SettingsRepository.CatchupPlayer.entries
                actions.onCatchupPlayer(all[(all.indexOf(player) + 1) % all.size])
            },
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_record).uppercase(locale))
        StageMenuItem(
            text = actions.recordLabel, icon = OwnTVIcon.REC, iconFilled = true,
            enabled = actions.onRecord != null,
            onClick = { actions.onRecord?.invoke(); onDismiss() },
        )
        StageMenuItem(
            text = stringResource(if (actions.seriesActive) R.string.recording_stop_series else R.string.recording_record_series),
            icon = OwnTVIcon.REC, iconFilled = true,
            enabled = actions.onSeries != null,
            onClick = { actions.onSeries?.invoke(); onDismiss() },
        )
        StageMenuItem(
            text = stringResource(R.string.recording_from_archive), icon = OwnTVIcon.REWIND,
            enabled = actions.onRecordFromCatchup != null,
            onClick = { actions.onRecordFromCatchup?.invoke(); onDismiss() },
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_channel).uppercase(locale))
        StageMenuItem(
            text = stringResource(if (actions.isFavorite) R.string.content_epg_remove_favourite else R.string.content_epg_add_favourite),
            icon = OwnTVIcon.FAVORITE,
            onClick = { actions.onFavorite(); onDismiss() },
        )
        if (actions.onAutoMatch != null) {
            StageMenuItem(text = stringResource(R.string.content_epg_match_button), icon = OwnTVIcon.SPARKLE, onClick = { onDismiss(); actions.onAutoMatch.invoke() })
        }
        if (actions.onPickEpg != null) {
            StageMenuItem(text = stringResource(R.string.content_epg_pick_manually), icon = OwnTVIcon.EPG, onClick = { onDismiss(); actions.onPickEpg.invoke() })
        }
        if (actions.onEpgOffset != null) {
            StageMenuItem(
                text = stringResource(R.string.content_epg_time_offset), icon = OwnTVIcon.CLOCK, value = actions.epgOffsetValue,
                onClick = { onDismiss(); actions.onEpgOffset.invoke() },
            )
        }
    }
}

/** Record ▾ under the programme: Record (or Stop / Cancel), and Record every showing (or its stop). */
@Composable
internal fun GuideRecordMenu(title: String, actions: GuideProgrammeActions, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = 956f, top = 440.mpx, width = 470.mpx, onDismiss = onDismiss) {
        StageMenuHeader(title = title, subtitle = null)
        StageMenuItem(
            text = actions.recordLabel, icon = OwnTVIcon.REC, iconFilled = true, enabled = actions.onRecord != null,
            onClick = { actions.onRecord?.invoke(); onDismiss() },
            modifier = Modifier.focusRequester(focus),
        )
        if (actions.onSeries != null) {
            StageMenuItem(
                text = stringResource(if (actions.seriesActive) R.string.recording_stop_series else R.string.recording_record_series),
                icon = OwnTVIcon.REC, iconFilled = true,
                onClick = { actions.onSeries.invoke(); onDismiss() },
            )
        }
    }
}

/** Order ▾ (P4-04): "Order channels by · Opens this way next time" and the five orders, ✓ on the current one. */
@Composable
internal fun GuideOrderMenu(
    current: SettingsRepository.GuideSort,
    onPick: (SettingsRepository.GuideSort) -> Unit,
    showEmpty: Boolean,
    onShowEmpty: (Boolean) -> Unit,
    showPreview: Boolean,
    onShowPreview: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = 560f, top = 520.mpx, width = 470.mpx, onDismiss = onDismiss) {
        StageMenuHeader(title = stringResource(R.string.content_epg_order_title), subtitle = stringResource(R.string.content_epg_order_hint))
        GuideSortOrder.forEach { (sort, icon) ->
            StageMenuItem(
                text = guideSortLabel(sort), icon = icon, checked = sort == current || (sort == SettingsRepository.GuideSort.PROVIDER && current == SettingsRepository.GuideSort.LIVE_TV),
                onClick = { onPick(sort); onDismiss() },
                modifier = if (sort == current || (sort == SettingsRepository.GuideSort.PROVIDER && current == SettingsRepository.GuideSort.LIVE_TV)) Modifier.focusRequester(focus) else Modifier,
            )
        }
        // SHOW, as the Series page's order menu has it: a filter, not an order (owner, 2026-09-30).
        StageGroupLabel(stringResource(R.string.content_menu_group_show).uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale))
        StageMenuItem(
            text = stringResource(R.string.content_epg_show_empty), icon = OwnTVIcon.EYE_OFF,
            value = stringResource(if (showEmpty) R.string.common_on else R.string.common_off),
            onClick = { onShowEmpty(!showEmpty); onDismiss() },
        )
        StageMenuItem(
            text = stringResource(R.string.content_epg_show_preview), icon = OwnTVIcon.VIDEO,
            value = stringResource(if (showPreview) R.string.common_on else R.string.common_off),
            onClick = { onShowPreview(!showPreview); onDismiss() },
        )
    }
}

/**
 * Hold OK / ☰ where nothing is on (a channel with no guide, or a gap in its feed): what the programme
 * menu offers for the channel itself — watch it, favourite it, and give it a guide.
 */
@Composable
internal fun GuideChannelMenu(
    channel: ChannelEntity,
    isFavorite: Boolean,
    epgOffsetValue: String,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onAutoMatch: () -> Unit,
    onPickEpg: () -> Unit,
    onEpgOffset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    GuideMenuHost(x = 1150f, top = 64.mpx, width = 560.mpx, onDismiss = onDismiss) {
        StageMenuHeader(
            title = listOfNotNull(channel.number?.toString(), tv.own.owntv.features.live.ProviderTags.parse(channel.name).name).joinToString(" · "),
            subtitle = stringResource(R.string.content_epg_no_programme),
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_watch).uppercase(locale))
        StageMenuItem(text = stringResource(R.string.content_epg_watch_channel), icon = OwnTVIcon.LIVE_TV, onClick = { onDismiss(); onWatch() }, modifier = Modifier.focusRequester(focus))
        StageGroupLabel(stringResource(R.string.content_menu_group_channel).uppercase(locale))
        StageMenuItem(
            text = stringResource(if (isFavorite) R.string.content_epg_remove_favourite else R.string.content_epg_add_favourite),
            icon = OwnTVIcon.FAVORITE, onClick = { onFavorite(); onDismiss() },
        )
        StageMenuItem(text = stringResource(R.string.content_epg_match_button), icon = OwnTVIcon.SPARKLE, onClick = { onDismiss(); onAutoMatch() })
        StageMenuItem(text = stringResource(R.string.content_epg_pick_manually), icon = OwnTVIcon.EPG, onClick = { onDismiss(); onPickEpg() })
        StageMenuItem(text = stringResource(R.string.content_epg_time_offset), icon = OwnTVIcon.CLOCK, value = epgOffsetValue, onClick = { onDismiss(); onEpgOffset() })
    }
}

/** Provider, A–Z, Catch-up first, Favourites first. Channel number went (owner): it is the provider's order. */
private val GuideSortOrder = listOf(
    SettingsRepository.GuideSort.PROVIDER to OwnTVIcon.SORT,
    SettingsRepository.GuideSort.ALPHA to OwnTVIcon.SORT,
    SettingsRepository.GuideSort.CATCHUP to OwnTVIcon.REWIND,
    SettingsRepository.GuideSort.FAVORITES to OwnTVIcon.FAVORITE,
)

@Composable
internal fun guideSortLabel(sort: SettingsRepository.GuideSort): String = stringResource(
    when (sort) {
        // LIVE_TV is the old stored default: it now means the provider's order.
        SettingsRepository.GuideSort.LIVE_TV, SettingsRepository.GuideSort.PROVIDER -> R.string.content_epg_sort_provider
        SettingsRepository.GuideSort.ALPHA -> R.string.content_epg_sort_alpha
        SettingsRepository.GuideSort.CATCHUP -> R.string.content_epg_sort_catchup_first
        SettingsRepository.GuideSort.FAVORITES -> R.string.content_epg_sort_favorites_first
    },
)

/** Today ▾: the days the guide holds, the one on screen ticked; picking one jumps there at the same time of day. */
@Composable
internal fun GuideDayMenu(days: List<Long>, currentDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = 250f, top = 520.mpx, width = 400.mpx, onDismiss = onDismiss) {
        days.forEach { day ->
            StageMenuItem(
                text = guideDayLabel(day), icon = OwnTVIcon.CALENDAR, checked = day == currentDay,
                onClick = { onPick(day); onDismiss() },
                modifier = if (day == currentDay) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

/** "Today", "Tomorrow", "Yesterday", else the weekday and date ("Thu 2 Oct"). [day] is local midnight. */
@Composable
internal fun guideDayLabel(day: Long): String {
    val today = localMidnight(System.currentTimeMillis())
    val formatDay = tv.own.owntv.ui.format.rememberBestDateFormatter("EEEdMMM")
    return when ((day - today) / DAY_MS_APPROX) {
        0L -> stringResource(R.string.content_epg_today)
        1L -> stringResource(R.string.content_epg_tomorrow)
        -1L -> stringResource(R.string.content_epg_yesterday)
        else -> formatDay(day)
    }
}

private const val DAY_MS_APPROX = 24L * 60 * 60 * 1000

/** Local midnight of [ms], so days are counted as the viewer lives them (DST included). */
internal fun localMidnight(ms: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ms
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

/** OK on a programme that has been on, with Play catch-up in = Ask: which player takes the archive. */
@Composable
internal fun GuideCatchupChooser(onInternal: () -> Unit, onExternal: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = 750f, top = 440.mpx, width = 470.mpx, onDismiss = onDismiss) {
        StageMenuHeader(title = stringResource(R.string.settings_catchup_player), subtitle = stringResource(R.string.content_epg_player_choice_description))
        StageMenuItem(text = stringResource(R.string.content_epg_own_player), icon = OwnTVIcon.PLAY, onClick = { onDismiss(); onInternal() }, modifier = Modifier.focusRequester(focus))
        StageMenuItem(text = stringResource(R.string.content_epg_external_player), icon = OwnTVIcon.EXTERNAL, onClick = { onDismiss(); onExternal() })
    }
}

/**
 * The Auto-match review (P4-05): a glass popup 1080 wide — "Auto-match EPG · 41 channels matched",
 * "Review 3 matches", the explanation, one row per unsure match (plate, channel, →, feed name and its
 * score, Accept, Skip; the row holding focus is FX), then Skip all · Accept all · Done. Guide logos
 * stay a switch at the left of the footer.
 */
@Composable
internal fun GuideReviewPopup(
    suggestions: List<EpgViewModel.EpgMatchSuggestion>,
    matchedCount: Int?,
    includeLogos: Boolean,
    onIncludeLogos: (Boolean) -> Unit,
    onAccept: (EpgViewModel.EpgMatchSuggestion) -> Unit,
    onSkip: (EpgViewModel.EpgMatchSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onSkipAll: () -> Unit,
    onDone: () -> Unit,
    /** CH+/− paging, as every long list in the app: skip by the user's counts, long press to the ends. */
    chNavEnabled: Boolean,
    chNavUpSkip: Int,
    chNavDownSkip: Int,
) {
    val a = stageAccent
    BackHandler { onDone() }
    val rowFocus = remember { HashMap<Int, FocusRequester>() }
    fun acceptFocus(i: Int) = rowFocus.getOrPut(i) { FocusRequester() }
    val bulkFocus = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var focusedRow by remember { mutableStateOf(0) }
    var listFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { acceptFocus(0).requestFocus() } }
    OwnTVPopup(onDismissRequest = onDone, stageLayout = true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f))) {
            Column(
                Modifier
                    .padding(start = maxWidth * (420f / 1920f), top = 150.mpx, bottom = 24.mpx)
                    .width(1080.mpx)
                    .stageGlass(30.mpx, overContent = true)
                    .padding(30.mpx)
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                    OwnTVIcon(OwnTVIcon.SPARKLE, a.accent, Modifier.size(18.mpx))
                    Text(
                        listOfNotNull(
                            stringResource(R.string.content_epg_match_button),
                            matchedCount?.let { pluralStringResource(R.plurals.content_epg_channels_matched, it, it) },
                        ).joinToString(" · "),
                        style = stageText(18, 700, 0.02.em), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.content_epg_review_count, suggestions.size, suggestions.size),
                    style = stageText(38, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.mpx, bottom = 6.mpx),
                )
                Text(
                    stringResource(R.string.content_epg_review_description),
                    style = stageText(18, 400), color = StageColors.Muted,
                    modifier = Modifier.padding(bottom = 18.mpx),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (78 * 5).mpx)
                        .onFocusChanged { listFocused = it.hasFocus }
                        .then(
                            Modifier.chNavPaging(
                                enabled = chNavEnabled,
                                upSkip = chNavUpSkip,
                                downSkip = chNavDownSkip,
                                isFocused = { listFocused },
                                lastIndex = { suggestions.lastIndex },
                                currentTargetIndex = { focusedRow },
                                onJumpToIndex = { i -> scope.jumpLazyListTo(listState, i) { acceptFocus(i).requestFocus() } },
                            ),
                        ),
                ) {
                    itemsIndexed(suggestions, key = { _, s -> s.channel.id }) { index, s ->
                        GuideReviewRow(
                            s, onAccept = { onAccept(s) }, onSkip = { onSkip(s) },
                            acceptFocus = acceptFocus(index), bulkFocus = bulkFocus,
                            onFocused = { focusedRow = index },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 22.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageSurface(onClick = { onIncludeLogos(!includeLogos) }, radius = 15.mpx) { focused ->
                        Row(
                            Modifier.height(56.mpx).padding(horizontal = 16.mpx),
                            horizontalArrangement = Arrangement.spacedBy(12.mpx),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.content_epg_include_logos), style = stageText(18, 700), color = if (focused) a.onAccent else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StageSwitch(includeLogos)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Bulk actions only make sense for a multi-channel run; a single auto-match shows just accept / skip.
                    if (suggestions.size > 1) {
                        StageButton(stringResource(R.string.content_epg_skip_all), onClick = onSkipAll, height = 56.mpx, textSize = 19)
                        StageButton(stringResource(R.string.content_epg_accept_all), onClick = onAcceptAll, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(bulkFocus))
                    }
                    StageButton(
                        stringResource(R.string.common_done), onClick = onDone, height = 56.mpx, textSize = 19, tinted = true,
                        modifier = if (suggestions.size > 1) Modifier else Modifier.focusRequester(bulkFocus),
                    )
                }
            }
        }
    }
}

/** `.rv`: 78 high, radius 18, the whole row drawn FX while its Accept or Skip has focus. */
@Composable
private fun GuideReviewRow(
    s: EpgViewModel.EpgMatchSuggestion,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    acceptFocus: FocusRequester,
    bulkFocus: FocusRequester,
    onFocused: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(78.mpx)
            .onFocusChanged { hasFocus = it.hasFocus; if (it.hasFocus) onFocused() }
            .then(if (hasFocus) Modifier.stageFocusLook(StageFocus.FX, 18.mpx) else Modifier)
            .padding(horizontal = 14.mpx),
        horizontalArrangement = Arrangement.spacedBy(16.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LivePlate(s.channel.displayLogoUrl, 70.mpx, 50.mpx)
        Text(s.channel.name, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(300.mpx))
        Text("→", style = stageText(22, 400), color = StageColors.Dim)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            Text(s.epgName ?: s.epgChannelId, style = stageText(18, 400), color = Color(0xFFD3DCD8), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(
                stringResource(R.string.common_percent, (s.score * 100).toInt()),
                style = stageText(15, 800), color = Color(0xFFFFD27A), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(Color(255, 200, 80).copy(alpha = 0.14f), RoundedCornerShape(8.mpx)).padding(horizontal = 9.mpx, vertical = 4.mpx),
            )
        }
        StageTool(text = stringResource(R.string.content_epg_accept), icon = OwnTVIcon.CHECK, onClick = onAccept, modifier = Modifier.focusRequester(acceptFocus))
        // ▶ from any row's Skip reaches Accept all (Done for a single match): no scrolling past thousands of rows.
        StageTool(
            text = stringResource(R.string.content_epg_skip), icon = OwnTVIcon.CLOSE, onClick = onSkip,
            modifier = Modifier.focusProperties { right = bulkFocus },
        )
    }
}

/** A reminder's line: "Starts in 5 min · 20:05 – 21:40 · Sky Cinema Family" (the prompt, and Notify only's message). */
@Composable
internal fun reminderEyebrow(reminder: tv.own.owntv.core.database.entity.ReminderEntity): String = programmeEyebrow(
    EpgProgrammeEntity(sourceId = 0, epgChannelId = "", startMs = reminder.startMs, stopMs = reminder.stopMs, title = reminder.title),
    reminder.channelName,
    System.currentTimeMillis(),
)

/**
 * The reminder prompt (G2, Programme reminders = Ask to switch): the programme about to start, with
 * Watch channel and Dismiss. It opens over whatever is on screen, the player included.
 */
@Composable
internal fun ReminderPrompt(
    reminder: tv.own.owntv.core.database.entity.ReminderEntity,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val a = stageAccent
    val focus = remember { FocusRequester() }
    LaunchedEffect(reminder.id) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val eyebrow = reminderEyebrow(reminder)
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(760.mpx)
                    .stageGlass(30.mpx, overContent = true)
                    .padding(30.mpx)
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                    OwnTVIcon(OwnTVIcon.BELL, a.accent, Modifier.size(18.mpx))
                    Text(eyebrow, style = stageText(18, 700, 0.02.em), color = a.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    reminder.title, style = stageText(38, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.mpx, bottom = 24.mpx),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    StageButton(
                        stringResource(R.string.content_epg_watch_channel), onClick = onWatch, icon = OwnTVIcon.LIVE_TV,
                        height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(focus),
                    )
                    StageButton(stringResource(R.string.common_dismiss), onClick = onDismiss, height = 56.mpx, textSize = 19)
                }
            }
        }
    }
}
