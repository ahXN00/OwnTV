package tv.own.owntv.features.shell.components

import tv.own.owntv.ui.theme.stageTone
import tv.own.owntv.ui.theme.LocalStagePalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.theme.BackgroundConfig
import tv.own.owntv.core.theme.BackgroundStyle
import tv.own.owntv.core.theme.PictureLook
import tv.own.owntv.features.home.HomeViewModel
import tv.own.owntv.features.live.LivePlate
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsHeading
import tv.own.owntv.features.settings.backdropOf
import tv.own.owntv.features.settings.dotSeparator
import tv.own.owntv.ui.components.BackgroundPicture
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.drawOuterRing
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.LocalBackground
import tv.own.owntv.ui.theme.LocalBlurredBackdrop
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Settings › Appearance › Glass & background: BACKGROUND — Background (Stage / Picture /
 * Plain), Picture, Picture look (Sharp / Soft / Dark), Darken, Blur, Accent light — and GLASS — the switch
 * and Glass opacity — then Reset. Every row's panel carries a live preview of Live TV on the background.
 * Rows that only apply to a picture show "Picture only" / "—" and cannot be focused without one.
 */
@Composable
internal fun GlassBackgroundPage(
    background: BackgroundConfig,
    glassOn: Boolean,
    alphaPercent: Int,
    onSetStyle: (BackgroundStyle) -> Unit,
    onOpenPicture: () -> Unit,
    onSetLook: (PictureLook) -> Unit,
    onSetDim: (Int) -> Unit,
    onSetBlur: (Int) -> Unit,
    onSetAccentLight: (Boolean) -> Unit,
    onToggleGlass: () -> Unit,
    onSetAlpha: (Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }
    val pic = background.style == BackgroundStyle.PICTURE
    val hasFile = background.imagePath.isNotBlank()
    val preview: @Composable () -> Unit = { GlassBackgroundPreview() }
    val change = stringResource(R.string.settings_key_change)
    val back = stringResource(R.string.common_back) to stringResource(R.string.settings_group_appearance)
    val stepHints = listOf("◀ ▶" to change, back)
    val okHints = listOf(stringResource(R.string.common_ok) to change, back)
    val previewTitle = stringResource(R.string.settings_glass_live_preview)
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_appearance)),
        title = stringResource(R.string.settings_glass_bg_title),
        count = pluralStringResource(R.plurals.settings_setting_count, 9, 9),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        StageSettingsHeading(stringResource(R.string.settings_bg_background), 6, first = true)

        val styles = listOf(BackgroundStyle.STAGE, BackgroundStyle.PICTURE, BackgroundStyle.PLAIN)
        val styleLabels = listOf(stringResource(R.string.settings_bg_stage), stringResource(R.string.settings_bg_picture), stringResource(R.string.settings_bg_plain))
        val styleIndex = styles.indexOf(background.style)
        val bgTitle = stringResource(R.string.settings_bg_background)
        StageSettingRow(
            icon = OwnTVIcon.PALETTE,
            title = bgTitle,
            desc = stringResource(R.string.settings_line_bg_background),
            value = SettingValue.Segmented(styleLabels, styleIndex),
            onClick = { onSetStyle(styles[(styleIndex + 1) % styles.size]) },
            onStep = { d -> onSetStyle(styles[(styleIndex + d).coerceIn(0, styles.lastIndex)]) },
            help = SettingHelp(bgTitle + dotSeparator() + previewTitle, stringResource(R.string.settings_help_bg_background), styleLabels, styleIndex, hints = stepHints, extra = preview),
        )

        val picTitle = stringResource(R.string.settings_bg_picture)
        val picLine = if (hasFile) java.io.File(background.imagePath).name else stringResource(R.string.settings_line_bg_picture_none)
        StageSettingRow(
            icon = OwnTVIcon.FOLDER,
            title = picTitle,
            desc = picLine,
            value = if (hasFile) SettingValue.Opens(stringResource(R.string.settings_bg_change)) else dimValue(stringResource(R.string.settings_bg_none)),
            onClick = onOpenPicture,
            help = SettingHelp(picTitle, stringResource(R.string.settings_line_bg_picture_none), hints = okHints, extra = preview),
        )

        val looks = PictureLook.entries
        val lookLabels = listOf(stringResource(R.string.settings_bg_sharp), stringResource(R.string.settings_bg_soft), stringResource(R.string.settings_bg_dark))
        val lookDescs = listOf(stringResource(R.string.settings_bg_sharp_desc), stringResource(R.string.settings_bg_soft_desc), stringResource(R.string.settings_bg_dark_desc))
        val lookIndex = looks.indexOf(background.look)
        val lookTitle = stringResource(R.string.settings_bg_look)
        val pictureOnly = stringResource(R.string.settings_bg_picture_only)
        StageSettingRow(
            icon = OwnTVIcon.SPARKLE,
            title = lookTitle,
            desc = stringResource(R.string.settings_line_bg_look),
            value = if (pic) SettingValue.Segmented(lookLabels, lookIndex) else dimValue(pictureOnly),
            enabled = pic,
            onClick = { onSetLook(looks[(lookIndex + 1) % looks.size]) },
            onStep = { d -> onSetLook(looks[(lookIndex + d).coerceIn(0, looks.lastIndex)]) },
            help = SettingHelp(
                lookTitle + dotSeparator() + previewTitle,
                stringResource(R.string.settings_help_bg_look),
                lookLabels.zip(lookDescs) { l, d -> "$l — $d" },
                chosen = lookIndex,
                recommended = looks.indexOf(PictureLook.SOFT),
                hints = stepHints,
                extra = preview,
            ),
        )
        PercentRow(OwnTVIcon.LAYERS, R.string.settings_bg_darken, R.string.settings_line_bg_darken, background.dimPct, BackgroundConfig.DIM_MAX, 5, pic, onSetDim, stepHints, preview)
        PercentRow(OwnTVIcon.LAYERS, R.string.settings_bg_blur, R.string.settings_line_bg_blur, background.blurPct, BackgroundConfig.BLUR_MAX, 10, pic, onSetBlur, stepHints, preview)

        val accentTitle = stringResource(R.string.settings_bg_accent_light)
        val accentLine = stringResource(R.string.settings_line_bg_accent_light)
        StageSettingRow(
            icon = OwnTVIcon.SUN,
            title = accentTitle,
            desc = accentLine,
            value = SettingValue.Switch(background.accentLight),
            onClick = { onSetAccentLight(!background.accentLight) },
            help = SettingHelp(accentTitle, accentLine, hints = okHints, extra = preview),
        )

        StageSettingsHeading(stringResource(R.string.settings_glass_section), 2)
        val glassTitle = stringResource(R.string.settings_glass_section)
        val glassHelp = stringResource(R.string.settings_help_glass_chrome)
        StageSettingRow(
            icon = OwnTVIcon.LAYERS,
            title = glassTitle,
            desc = stringResource(R.string.settings_line_glass_switch),
            value = SettingValue.Switch(glassOn),
            onClick = onToggleGlass,
            help = SettingHelp(glassTitle + dotSeparator() + previewTitle, glassHelp, hints = okHints, extra = preview),
        )
        val opacityTitle = stringResource(R.string.settings_glass_opacity)
        StageSettingRow(
            icon = OwnTVIcon.LAYERS,
            title = opacityTitle,
            desc = stringResource(R.string.settings_line_glass_opacity),
            value = if (glassOn) SettingValue.Stepper(stringResource(R.string.settings_surface_transparency, alphaPercent)) else dimValue("—"),
            enabled = glassOn,
            onClick = { onSetAlpha((alphaPercent + 5).let { if (it > 100) 20 else it }) },
            onStep = { d -> onSetAlpha((alphaPercent + d * 5).coerceIn(20, 100)) },
            help = SettingHelp(opacityTitle + dotSeparator() + previewTitle, glassHelp, hints = stepHints, extra = preview),
        )

        val resetTitle = stringResource(tv.own.owntv.R.string.common_reset)
        val resetLine = stringResource(R.string.settings_line_glass_reset, stringResource(R.string.common_percent, tv.own.owntv.core.settings.SettingsRepository.GLASS_ALPHA_DEFAULT_PCT))
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = resetTitle,
            desc = resetLine,
            value = null,
            onClick = onReset,
            help = SettingHelp(resetTitle, resetLine, hints = okHints, extra = preview),
        )
    }
}

/**
 * The Appearance row's value: the background ("Stage", "Plain", or the picture's look) and the glass
 * opacity, e.g. "Soft · 56%"; "· Off" with glass off.
 */
@Composable
internal fun glassBackgroundSummary(background: BackgroundConfig, glass: tv.own.owntv.core.theme.GlassConfig): String {
    val bg = when {
        background.showsPicture -> stringResource(
            when (background.look) {
                PictureLook.SHARP -> R.string.settings_bg_sharp
                PictureLook.SOFT -> R.string.settings_bg_soft
                PictureLook.DARK -> R.string.settings_bg_dark
            },
        )
        background.style == BackgroundStyle.PLAIN -> stringResource(R.string.settings_bg_plain)
        else -> stringResource(R.string.settings_bg_stage)
    }
    val g = if (glass.enabled) stringResource(R.string.common_percent, (glass.alpha * 100).roundToInt()) else stringResource(R.string.common_off)
    return bg + dotSeparator() + g
}

/** The value a row shows when it does not apply right now: dim text, no chevron. */
private fun dimValue(text: String) = SettingValue.Custom {
    Text(text, style = stageText(18, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Darken / Blur: "50%" in a stepper, ◀ ▶ by [step] within 0…[max]; "—" without a picture. */
@Composable
private fun PercentRow(
    icon: OwnTVIcon,
    titleRes: Int,
    lineRes: Int,
    value: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    onSet: (Int) -> Unit,
    hints: List<Pair<String, String>>,
    preview: @Composable () -> Unit,
) {
    val title = stringResource(titleRes)
    val line = stringResource(lineRes)
    StageSettingRow(
        icon = icon,
        title = title,
        desc = line,
        value = if (enabled) SettingValue.Stepper(stringResource(R.string.settings_surface_transparency, value)) else dimValue("—"),
        enabled = enabled,
        onClick = { onSet((value + step).let { if (it > max) 0 else it }) },
        onStep = { d -> onSet((value + d * step).coerceIn(0, max)) },
        help = SettingHelp(title, line, hints = hints, extra = preview),
    )
}

/**
 * The panel's live preview: Live TV at a third of the screen's size, on the current background, with the
 * rail and the list drawn the Stage way and your own recent / favourite channels. Not a second Live TV —
 * nothing focusable, no player — but it follows every change on this page at once.
 */
@Composable
private fun GlassBackgroundPreview() {
    val home: HomeViewModel = koinViewModel()
    val state by home.uiState.collectAsStateWithLifecycle()
    val channels = remember(state.recentLive, state.favoriteLive) { (state.recentLive + state.favoriteLive).distinctBy { it.id }.take(8) }
    val still = state.trendingItems.firstOrNull()
    val background = LocalBackground.current
    val blurred = LocalBlurredBackdrop.current
    val a = stageAccent
    val previewRim = StageColors.Ink.copy(alpha = 0.1f)
    Column(Modifier.padding(top = 16.mpx)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.mpx))
                .background(stageTone(Color(0xFF05080A), LocalStagePalette.current.page))
                .drawBehind { drawInnerRing(previewRim, 1.mpx.toPx(), 18.mpx.toPx()) },
        ) {
            val scale = maxWidth / 1920.mpx
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(1920.mpx, 1080.mpx)
                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) },
            ) {
                if (background.showsPicture) BackgroundPicture(background, blurred, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().stageBackground(a.accent))
                // The rail at rest: a glass capsule of icons, Live TV lit.
                Column(
                    Modifier.padding(start = 26.mpx, top = 230.mpx).width(60.mpx).stageGlass(30.mpx).padding(vertical = 18.mpx),
                    verticalArrangement = Arrangement.spacedBy(22.mpx),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    listOf(OwnTVIcon.SEARCH, OwnTVIcon.HOME, OwnTVIcon.LIVE_TV, OwnTVIcon.EPG, OwnTVIcon.MOVIES, OwnTVIcon.SERIES, OwnTVIcon.DOWNLOADS).forEachIndexed { i, icon ->
                        OwnTVIcon(icon, if (i == 2) a.accent else StageColors.Muted, Modifier.size(26.mpx))
                    }
                }
                Text(stringResource(R.string.common_nav_live_tv), style = stageText(46, 800, (-1f / 46f).em), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 128.mpx, top = 54.mpx))
                Column(Modifier.padding(start = 112.mpx, top = 170.mpx).width(800.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    channels.forEachIndexed { i, ch ->
                        val focused = i == 1
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(92.mpx)
                                .then(
                                    if (focused) Modifier
                                        .drawBehind { drawOuterRing(a.focus, 2.mpx.toPx(), 20.mpx.toPx(), inset = -2.mpx.toPx()) }
                                        .background(Brush.horizontalGradient(listOf(a.accent.copy(alpha = 0.34f), a.accent.copy(alpha = 0.14f))), RoundedCornerShape(20.mpx))
                                    else Modifier,
                                )
                                .padding(horizontal = 22.mpx),
                            horizontalArrangement = Arrangement.spacedBy(18.mpx),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text((ch.number ?: (i + 1)).toString(), style = stageText(21, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(52.mpx))
                            LivePlate(ch.logoUrl, 78.mpx, 54.mpx)
                            Text(ch.name, style = stageText(22, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // The Stage: the picture of a programme (the player itself is not drawn here).
                Box(
                    Modifier
                        .padding(start = 940.mpx, top = 120.mpx)
                        .size(916.mpx, 516.mpx)
                        .clip(RoundedCornerShape(28.mpx))
                        .background(Color.Black),
                ) {
                    if (still != null) AsyncImage(model = backdropOf(still), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Text(
            stringResource(R.string.settings_bg_preview_note),
            style = stageText(15, 500), color = StageColors.Muted,
            modifier = Modifier.padding(top = 10.mpx),
        )
    }
}
