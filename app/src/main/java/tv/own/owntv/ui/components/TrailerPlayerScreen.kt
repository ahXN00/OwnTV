package tv.own.owntv.ui.components

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.net.toUri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * In-app YouTube trailer player in the Stage design: a fullscreen WebView-backed IFrame
 * player (`android-youtube-player`) in its own window over everything (rail and clock included), with a
 * HUD in the Stage look — the eyebrow "TRAILER" and the [title] at the top, a play / pause button, the
 * progress track, the time and the key hints at the bottom, each over a dark wash. The HUD fades after
 * a few seconds without a key, so YouTube's own subtitles are not covered; any key brings it back.
 * OK = pause / play, ◀ ▶ = 10 s, Back = exit. Focus stays in Compose; the WebView is a pure video
 * surface, which sidesteps the classic "iframe steals D-pad focus" problem.
 *
 * Graceful fallback (required by the plan): if the WebView is missing/ancient or the video errors,
 * we hand off to an external "Open in YouTube" intent and exit — the button always does *something*.
 */
@Composable
fun TrailerPlayerScreen(videoKey: String, title: String? = null, onExit: () -> Unit) =
    // Video keeps its dark chrome in every theme.
    androidx.compose.runtime.CompositionLocalProvider(tv.own.owntv.ui.theme.LocalStagePalette provides tv.own.owntv.ui.theme.StagePalette.Dark) {
        TrailerPlayerContent(videoKey, title, onExit)
    }

@Composable
private fun TrailerPlayerContent(videoKey: String, title: String?, onExit: () -> Unit) {
    val context = LocalContext.current
    // The stored value may hold several keys, best first (core TrailerKeys): a blocked one moves on to the next.
    val keys = remember(videoKey) { tv.own.owntv.core.metadata.TrailerKeys.split(videoKey).ifEmpty { listOf(videoKey) } }
    var keyIndex by remember { mutableIntStateOf(0) }

    var currentSec by remember { mutableFloatStateOf(0f) }
    var durationSec by remember { mutableFloatStateOf(0f) }
    // None of the keys can play here (or there is no WebView): offer YouTube instead of jumping to it.
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var reportedPlaybackQuality by remember { mutableStateOf<PlayerConstants.PlaybackQuality?>(null) }
    // Bumped on every key: the HUD shows, and hides again HUD_HIDE_MS later while playing.
    var keyStamp by remember { mutableIntStateOf(0) }
    var hudVisible by remember { mutableStateOf(true) }
    LaunchedEffect(keyStamp, playing) {
        hudVisible = true
        // The first time, outlast YouTube's own start overlay (title, share, a centre ⏸ — about 7 s, and
        // not ours to hide): the HUD fading first left that ⏸ hanging alone on screen.
        if (playing) { delay(if (keyStamp == 0) HUD_FIRST_HIDE_MS else HUD_HIDE_MS); hudVisible = false }
    }

    val playFocus = remember { FocusRequester() }

    // Some no-name boxes ship without a usable System WebView — constructing the player view itself
    // can throw there, so treat construction failure like a playback error (external fallback).
    val playerView = remember {
        runCatching {
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Pure video surface: the Compose overlay owns all D-pad focus.
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }.getOrNull()
    }

    if (playerView == null || failed) {
        TrailerBlockedDialog(onOpen = { openInYouTube(context, keys.first()); onExit() }, onCancel = onExit)
        return
    }

    val playerOptions = remember(context) {
        IFramePlayerOptions.Builder(context)
            .controls(0)
            .fullscreen(0)
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .build()
    }

    DisposableEffect(Unit) {
        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
                youTubePlayer.loadVideo(keys[keyIndex], 0f)
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                // The IFrame bridge reports every 100 ms. A TV progress label only needs whole seconds;
                // avoiding ten Compose state writes/layouts per second leaves more UI budget for WebView video.
                val wholeSecond = second.toInt().coerceAtLeast(0)
                if (currentSec.toInt() != wholeSecond) currentSec = wholeSecond.toFloat()
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                durationSec = duration
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                android.util.Log.w("TrailerPlayer", "trailer error key=${keys[keyIndex]} error=$error")
                if (keyIndex < keys.lastIndex) {
                    keyIndex++
                    currentSec = 0f; durationSec = 0f
                    youTubePlayer.loadVideo(keys[keyIndex], 0f)
                } else {
                    failed = true
                }
            }

            override fun onPlaybackQualityChange(
                youTubePlayer: YouTubePlayer,
                playbackQuality: PlayerConstants.PlaybackQuality,
            ) {
                reportedPlaybackQuality = playbackQuality
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                when (state) {
                    PlayerConstants.PlayerState.ENDED -> onExit()
                    PlayerConstants.PlayerState.PLAYING -> playing = true
                    PlayerConstants.PlayerState.PAUSED -> playing = false
                    else -> Unit
                }
            }
        }
        // OwnTV supplies the TV-safe D-pad controls; do not make WebView render a second control layer.
        runCatching { playerView.initialize(listener, playerOptions) }.onFailure { failed = true }
        onDispose { player = null; runCatching { playerView.release() } }
    }

    // Every key shows the HUD; ◀ ▶ seek ±10 s (physical by design: left rewinds in every locale).
    // The play button is the only focus target, so ▲ ▼ are swallowed rather than searched.
    val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        if (e.type != KeyEventType.KeyDown) return@onKey false
        keyStamp++
        val p = player ?: return@onKey false
        when (e.key) {
            // The player reports time only while playing: show the jump at once, paused or not.
            Key.DirectionLeft -> { currentSec = (currentSec - 10f).coerceAtLeast(0f); p.seekTo(currentSec); true }
            Key.DirectionRight -> {
                val target = currentSec + 10f
                currentSec = if (durationSec > 0f) target.coerceAtMost(durationSec - 1f) else target
                p.seekTo(currentSec)
                true
            }
            Key.DirectionUp, Key.DirectionDown -> true
            else -> false
        }
    }

    // Its own window, so the floating rail and the clock never sit over the video. Fullscreen and
    // unclipped: the WebView only renders video on a hardware overlay when nothing forces it to be
    // composited into the app's own layer. A rounded clip, a fractional size and a scrim BEHIND it all
    // did, and every decoded frame was then copied through the GPU instead ("no buffers currently
    // available in the reader queue" / "CopySharedImage: Source shared image is not accessable") and
    // dropped frames continuously. The HUD is drawn over the video, never under it.
    OwnTVPopup(onDismissRequest = onExit, stageLayout = true, stageScaled = false) {
        LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
        BackHandler { onExit() }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent(onKey)
                .trapAllFocusExit()
                .focusGroup(),
        ) {
            val screenW = maxWidth
            fun fx(px: Int) = screenW * (px / 1920f)
            AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

            val hudAlpha by animateFloatAsState(if (hudVisible) 1f else 0f, ownTvTween(320), label = "trailerHud")
            val wash = Color(5, 8, 10)
            // Top: the eyebrow and the title over a 300 high wash.
            Box(
                Modifier.align(Alignment.TopStart).fillMaxWidth().height(300.mpx).graphicsLayer { alpha = hudAlpha }
                    .gradientWash(true, 0f to wash.copy(alpha = 0.85f), 1f to wash.copy(alpha = 0f)),
            ) {
                Column(Modifier.padding(start = fx(84), top = 56.mpx, end = fx(84))) {
                    Text(
                        stringResource(R.string.home_trending_trailer).uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale),
                        style = stageText(15, 800, 0.12.em), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!title.isNullOrBlank()) {
                        Text(
                            title, style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.mpx),
                        )
                    }
                }
            }
            // Bottom: play / pause, the progress track and the time, then the key hints.
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(260.mpx).graphicsLayer { alpha = hudAlpha }
                    .gradientWash(true, 0f to wash.copy(alpha = 0f), 1f to wash.copy(alpha = 0.9f)),
            ) {
                Column(Modifier.align(Alignment.BottomStart).padding(start = fx(84), end = fx(84), bottom = 44.mpx)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.mpx)) {
                        StageButton(
                            text = null, round = true, iconFilled = true,
                            icon = if (playing) OwnTVIcon.PAUSE else OwnTVIcon.PLAY,
                            onClick = { keyStamp++; player?.let { if (playing) it.pause() else it.play() } },
                            modifier = Modifier.focusRequester(playFocus),
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            StageProgress(
                                if (durationSec > 0f) (currentSec / durationSec).coerceIn(0f, 1f) else 0f,
                                Modifier.weight(1f),
                            )
                        }
                        Text(
                            stringResource(R.string.player_trailer_progress, formatSec(currentSec), formatSec(durationSec)),
                            style = stageText(20, 700).copy(fontFeatureSettings = "tnum"), color = StageColors.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (BuildConfig.DEV_TOOLS) {
                            reportedPlaybackQuality?.let { quality ->
                                Text(quality.name, style = stageText(15, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    StageKeyHints(
                        listOf(
                            stringResource(R.string.common_ok) to stringResource(if (playing) R.string.home_trending_pause else R.string.home_trending_play),
                            "◀ ▶" to stringResource(R.string.player_trailer_skip),
                            stringResource(R.string.common_back) to stringResource(R.string.player_trailer_exit),
                        ),
                        Modifier.padding(top = 22.mpx, start = 4.mpx),
                    )
                }
            }
        }
    }
}

/** How long the trailer HUD stays up after the last key while playing. */
private const val HUD_HIDE_MS = 4_000L

/** The first hide after opening: past YouTube's own start overlay. */
private const val HUD_FIRST_HIDE_MS = 8_000L

/**
 * When no trailer video can play in the app (owners can block embedded players, and some boxes have no
 * usable WebView): a Stage panel saying so, with Open in YouTube (tinted, focused) and Cancel. Back cancels.
 */
@Composable
private fun TrailerBlockedDialog(onOpen: () -> Unit, onCancel: () -> Unit) {
    val openFocus = remember { FocusRequester() }
    OwnTVPopup(onDismissRequest = onCancel, stageLayout = true) {
        LaunchedEffect(Unit) { runCatching { openFocus.requestFocus() } }
        BackHandler { onCancel() }
        Box(
            Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f)).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.width(720.mpx).stageGlass(30.mpx, overContent = true).padding(horizontal = 36.mpx, vertical = 32.mpx)) {
                Text(
                    stringResource(R.string.home_trending_trailer).uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale),
                    style = stageText(14, 800, 0.12.em), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.player_trailer_blocked_title), style = stageText(30, 800, (-0.5).mpxSp), color = StageColors.Text,
                    modifier = Modifier.padding(top = 6.mpx),
                )
                Text(
                    stringResource(R.string.player_trailer_blocked_body), style = stageText(18, 400).copy(lineHeight = (18 * 1.45f).mpxSp),
                    color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx, bottom = 28.mpx),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    StageButton(
                        stringResource(R.string.player_trailer_open_youtube), onClick = onOpen, icon = OwnTVIcon.EXTERNAL,
                        height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(openFocus),
                    )
                    StageButton(stringResource(R.string.common_cancel), onClick = onCancel, height = 56.mpx, textSize = 19)
                }
            }
        }
    }
}

/** External fallback: YouTube app if installed, else any browser. Never throws. */
private fun openInYouTube(context: Context, videoKey: String) {
    val app = Intent(Intent.ACTION_VIEW, "vnd.youtube:$videoKey".toUri())
    val web = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$videoKey".toUri())
    runCatching { context.startActivity(app) }
        .recoverCatching { context.startActivity(web) }
}

@Composable
private fun formatSec(s: Float): String {
    val total = s.toInt().coerceAtLeast(0)
    val m = total / 60
    val sec = total % 60
    return stringResource(R.string.common_timestamp_minutes, m, sec)
}
