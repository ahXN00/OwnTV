package tv.own.owntv

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.room.useWriterConnection
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import tv.own.owntv.core.util.Perf
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.features.profiles.AddProfileOnboarding
import tv.own.owntv.features.profiles.ProfileGate
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.setup.Onboarding
import tv.own.owntv.features.shell.OwnTVShell
import tv.own.owntv.features.shell.ShellViewModel
import tv.own.owntv.ui.theme.BlurredBackdrop
import tv.own.owntv.ui.theme.LocalBackground
import tv.own.owntv.ui.theme.LocalBlurredBackdrop
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.LocalUiFontScaleFactor
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

open class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OwnTVHome"

        /** Bounded wait for the startup database probe (see [probeDatabase]). */
        private const val DB_PROBE_TIMEOUT_MS = 5_000L

        /**
         * Hard ceiling on the splash. A stuck DataStore/profile read must never leave the user
         * staring at a static logo — past this the splash dismisses whatever the state is.
         *
         * Kept short deliberately: the old build drew its (blank) first frame at ~1.1s, so anything
         * beyond ~2s of splash is a perceived-responsiveness regression even though the app is doing
         * strictly less work than before.
         */
        private const val SPLASH_TIMEOUT_MS = 2_000L

        /**
         * How much longer, once the profile is known, the splash waits for the start-up screen. Home settles
         * at once, so only a start on the last channel ever waits; on a 55k-channel playlist on the TCL that
         * took 2.2–3.3 s from launch, and a shorter wait showed Home for a moment before Live TV or the Guide.
         */
        private const val STARTUP_SETTLE_TIMEOUT_MS = 3_000L
    }

    /**
     * False while the app has no destination to draw yet (active profile id unknown, or the
     * profile query has not returned on a cold start) — exactly the two `when` branches that used to
     * render a blank window. Latched true and never cleared, so a mid-session empty-profile window
     * during backup restore cannot bring the splash back; the foreground gate still refuses to
     * compose the shell until the loaded list and active-id check are valid.
     */
    @Volatile
    private var contentReady = false

    /**
     * Whether the shell has opened where the profile's start-up choice says (the TV Guide, Live TV, the
     * player...). The shell first composes on Home and applies that choice a moment later, so the splash
     * stays up until then and Home never flashes on the way. Owned by this activity, so a relaunch in a
     * process that is still alive waits again.
     */
    @Volatile
    private var startupSettled = false

    private val player: tv.own.owntv.player.OwnTVPlayer by inject()
    private val previewEngine: tv.own.owntv.player.LivePreviewEngine by inject()
    private val heroPreviewEngine: tv.own.owntv.player.HeroPreviewEngine by inject()
    private val engines: tv.own.owntv.player.PlaybackEngines by inject()
    private val handOff: tv.own.owntv.features.pip.AppHandOff by inject()
    // Activity-scoped: the same instance Compose retrieves via koinViewModel() inside setContent.
    private val shellViewModel: ShellViewModel by viewModel()
    // The sole locale authority (SharedPreferences-backed; see docs/internationalization.md 0b).
    private val localeStore: tv.own.owntv.core.i18n.LocaleStore by inject()
    private var pendingDeepLink by mutableStateOf<LauncherDeepLink?>(null)

    /**
     * Wrap the Activity base with the selected locale so its own `Resources` resolve correctly —
     * Application wrapping alone leaves the Activity wrong, because
     * `ActivityThread.createBaseContextForActivity` never derives from the Application object
     * (see docs/internationalization.md 0b, "Both Application and Activity must wrap").
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val tag = tv.own.owntv.core.i18n.LocaleStore.from(newBase).readBlocking()
        super.attachBaseContext(tv.own.owntv.core.i18n.AppLocale.wrap(newBase, tag))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        automationWake.wakeIfAsked(intent)
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onNewIntent deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
    }

    /** Turns the screen on for an API start, until OwnTV stops. */
    private val automationWake by lazy { tv.own.owntv.features.automation.AutomationWake(this, get()) }

    override fun onStop() {
        super.onStop()
        automationWake.drop()
        // Backgrounded (Home / another app), exited, logged out, or the picture-in-picture window closed:
        // stop playback and free the demuxer cache + decoder buffers — holding them while invisible got
        // the process LMK-killed on real TVs. Every engine: mpv, the Live engine (its audio must stop
        // too), each Multiview tile and the hero. A picture-in-picture window only pauses the activity,
        // so playback there never reaches it. The relock first: it reads what is playing before the
        // engines free it.
        if (!isChangingConfigurations) { shellViewModel.relockCategories(); engines.onAppBackgrounded() }
        handOff.onActivityStopped()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        handOff.onActivityResumed()
    }

    /** Every screen this activity opens (a trailer in YouTube, a system settings page) is a hand-off. */
    override fun startActivity(intent: Intent, options: Bundle?) {
        tv.own.owntv.features.pip.startAsHandOff(intent) { super.startActivity(it, options) }
    }

    override fun onStart() {
        super.onStart()
        // Paired with onStop: bring back what was freed while backgrounded (notably the TV screensaver, which
        // kicks in during a long pause) — a VOD restored paused at its position, and a live channel re-tuned
        // to the live edge — so Play resumes instead of sitting on a dead/empty stream. No-op on fresh launch.
        // Not before the relock check is done: what it found sealed must not come back first.
        shellViewModel.afterSealCheck { engines.onAppForegrounded() }
        // Staleness-based auto refresh on resume (interval modes only — STARTUP is cold-start only). The
        // ViewModel throttles this internally so a quick toggle doesn't re-run the check.
        shellViewModel.checkAutoRefresh(includeStartup = false)
    }

    /**
     * Probe the database before anything touches it. Room opens lazily, so a failed migration would
     * otherwise surface as a random crash inside whichever coroutine queried first — and with the
     * destructive fallback gone that crash would repeat on every launch. Opening it here, on a
     * worker thread with a bounded wait, turns that into a deterministic recovery screen.
     *
     * On a healthy install this is a version check on an already-migrated file: milliseconds, and it
     * is work the first query would have done anyway. A timeout is treated as "healthy" so a slow
     * device never sits on a blank window.
     */
    private fun probeDatabase(): String? {
        var error: String? = null
        val worker = Thread {
            // Core configures a SQLiteDriver, and `openHelper` throws outright
            // once one is set. Opening a connection is what this probe was always really doing —
            // forcing Room to run the migration chain now rather than inside the first query.
            runCatching {
                kotlinx.coroutines.runBlocking {
                    get<tv.own.owntv.core.database.OwnTVDatabase>().useWriterConnection { }
                }
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
        worker.start()
        worker.join(DB_PROBE_TIMEOUT_MS)
        if (error != null) Log.e(TAG, "database probe failed: $error")
        return error
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be installed BEFORE super.onCreate() per the library's contract. It also applies
        // postSplashScreenTheme (Theme.OwnTV), so the activity ends up in the same theme as before.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        tv.own.owntv.features.pip.PictureInPictureWindow.attach(this, get())
        // The window only pauses the activity, so onStop's relock never runs while it is open: going into
        // it is leaving the app, and a channel from a category the PIN opened stops there too.
        addOnPictureInPictureModeChangedListener { if (it.isInPictureInPictureMode) shellViewModel.relockCategories() }
        val splashDeadline = SystemClock.uptimeMillis() + SPLASH_TIMEOUT_MS
        var readyAt = 0L
        splash.setKeepOnScreenCondition {
            val now = SystemClock.uptimeMillis()
            if (!contentReady) return@setKeepOnScreenCondition now < splashDeadline
            if (readyAt == 0L) readyAt = now
            // Then until the shell is on the start-up screen, so Home does not flash first.
            !startupSettled && now < readyAt + STARTUP_SETTLE_TIMEOUT_MS
        }
        automationWake.wakeIfAsked(intent)
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onCreate deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
        val dbError = probeDatabase()
        if (dbError != null) {
            contentReady = true // the recovery screen IS the destination — don't hold the splash over it
            startupSettled = true
            setContent {
                tv.own.owntv.features.recovery.DatabaseRecoveryScreen(
                    message = dbError,
                    onRetry = { recreate() },
                    onResetData = {
                        // Explicit, twice-confirmed user choice — the only thing that clears a
                        // database SQLite refuses to open. Never automatic: automatic deletion was a real bug once.
                        runCatching { deleteDatabase(tv.own.owntv.core.database.OwnTVDatabase.NAME) }
                        finishAffinity()
                    },
                )
            }
            return
        }
        Perf.stamp("db-probed") // main thread was blocked here: everything above is pre-composition
        setContent {
            // First composition pass — splits "process start → Compose is running" from
            // "Compose is running → the destination's data arrived".
            // Deliberately a Unit-returning remember: the point is the side effect happening DURING
            // the first composition pass. LaunchedEffect/SideEffect would run after it and measure
            // something else.
            @Suppress("RememberReturnType")
            remember { Perf.stamp("first-composition"); Unit }

            // Hold the screen on while video is actually playing, so the TV screensaver doesn't
            // start mid-channel/episode; released when paused/stopped (then the screensaver is fine).
            // Buffering counts as playing: the screensaver timer runs from the last remote press, so
            // releasing for a one-second rebuffer deep into a film started the screensaver at once.
            val playing by player.isPlaying.collectAsStateWithLifecycle()
            val buffering by player.buffering.collectAsStateWithLifecycle()
            val failed by player.error.collectAsStateWithLifecycle()
            val keepAwake = (playing || buffering) && failed == null
            LaunchedEffect(keepAwake) {
                if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val viewModel: ShellViewModel = koinViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
            val focusHighlight by viewModel.focusHighlight.collectAsStateWithLifecycle()
            val focusHighlightWidth by viewModel.focusHighlightWidth.collectAsStateWithLifecycle()
            val uiZoomPercent by viewModel.uiZoomPercent.collectAsStateWithLifecycle()
            val fontCustomization by viewModel.fontCustomization.collectAsStateWithLifecycle()
            val animationLevel by viewModel.animationLevel.collectAsStateWithLifecycle()
            // Animations Off also stills the launch screen. Android shows it before the app runs, so the
            // choice can only be handed over for the next launch, and only from Android 13 on.
            LaunchedEffect(animationLevel) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val still = animationLevel == tv.own.owntv.core.theme.AnimationLevel.OFF
                    splashScreen.setSplashScreenTheme(
                        if (still) stillLaunchTheme(tv.own.owntv.core.brand.AppIconSwitcher.applied(this@MainActivity)) else 0,
                    )
                }
            }
            val bgImagePath by viewModel.bgImagePath.collectAsStateWithLifecycle()
            val glassConfig by viewModel.glassConfig.collectAsStateWithLifecycle()
            val background by viewModel.backgroundConfig.collectAsStateWithLifecycle()
            val avatarId by viewModel.avatarId.collectAsStateWithLifecycle()
            val avatarPath by viewModel.avatarPath.collectAsStateWithLifecycle()
            val profileName by viewModel.profileName.collectAsStateWithLifecycle()
            val sourceSummary by viewModel.sourceSummary.collectAsStateWithLifecycle()
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            val activePlaylistId by viewModel.activePlaylistId.collectAsStateWithLifecycle()
            val weather by viewModel.weather.collectAsStateWithLifecycle()
            val weatherFahrenheit by viewModel.weatherFahrenheit.collectAsStateWithLifecycle()
            val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
            val visibleSections by viewModel.visibleSections.collectAsStateWithLifecycle()
            val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
            val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

            val profilesVm: ProfilesViewModel = koinViewModel()
            val profileState by profilesVm.profileState.collectAsStateWithLifecycle()
            val profiles = (profileState as? tv.own.owntv.features.profiles.ProfileLoadState.Loaded)?.profiles.orEmpty()
            val profilesLoaded = profileState is tv.own.owntv.features.profiles.ProfileLoadState.Loaded
            // Activity-scoped gate/session state: configuration-only retention through the Activity's
            // ViewModelStore. Survives recreation (font scale, dark mode, a script-family language
            // switch) but is cleared on process death — an authentication result must never be restored
            // after a kill-and-restore, so there is deliberately no SavedStateHandle / rememberSaveable
            // (see docs/internationalization.md, "The profile gate: configuration-only retention").
            val gateSession: ProfileGateSessionViewModel = koinViewModel()
            val authenticatedProfileId = gateSession.authenticatedProfileId
            val addingProfile = gateSession.addingProfile
            val switchProfileRequested = gateSession.switchProfileRequested
            // The active id and the Room list are separate asynchronous streams. Neither one is a
            // launch decision by itself: an id >= 0 can point at a locked profile that Room has not
            // emitted yet, and a loaded empty list can be a restore/recovery window. The shell stays
            // out of the composition until the list is Loaded and the gate decision is complete.
            val loadedProfileId = activeProfileId
            val activeProfileKnown = loadedProfileId != null &&
                (loadedProfileId < 0L || profiles.any { it.id == loadedProfileId })
            // Authentication is profile-bound. Invalidate the old session before a changed active id
            // can become a shell destination (profile deletion, backup restore, or another writer).
            LaunchedEffect(loadedProfileId) {
                gateSession.invalidateIfNotProfile(loadedProfileId)
            }
            LaunchedEffect(loadedProfileId != null) {
                if (loadedProfileId != null) Perf.stamp("profile-id-loaded")
            }
            val shouldShowProfileGate = profilesLoaded && tv.own.owntv.features.profiles.profileGateRequired(profiles)
            // Keep the pure launch policy in the actual composition path as well as in its unit
            // tests. The policy checks the Room list, active-id membership, and authentication as
            // one decision; a future branch must not accidentally make the shell depend on only
            // one of the asynchronous inputs again.
            val gateRequired = shouldShowProfileGate || switchProfileRequested
            val authenticatedForActiveProfile = loadedProfileId != null &&
                authenticatedProfileId == loadedProfileId
            val shellReady = tv.own.owntv.features.profiles.shellMayCompose(
                profileState = profileState,
                activeProfileId = loadedProfileId,
                authenticatedProfileId = authenticatedProfileId,
                gateRequired = gateRequired,
            )
            // Back from a user-requested switch returns to the shell (the cold-start gate exits the app).
            BackHandler(enabled = switchProfileRequested && !addingProfile) {
                gateSession.cancelSwitchProfileRequest(loadedProfileId)
            }
            LaunchedEffect(profilesLoaded) {
                if (profilesLoaded) Perf.stamp("profiles-loaded")
            }
            val destinationReady = loadedProfileId != null && profilesLoaded
            // A profile or PIN gate is the destination itself; the shell's start-up choice comes after it.
            LaunchedEffect(destinationReady, shellReady) {
                if (destinationReady && !shellReady) startupSettled = true
            }
            LaunchedEffect(destinationReady) {
                if (destinationReady && !contentReady) {
                    contentReady = true
                    Perf.stamp("destination-ready") // splash hand-off — the number startup work moves
                }
            }

            // "Refresh on startup" — re-sync sources once the active profile is known.
            LaunchedEffect(activeProfileId) {
                if ((activeProfileId ?: -1L) >= 0L) viewModel.checkAutoRefresh(includeStartup = true)
            }

            OwnTVTheme(
                themeMode = themeMode,
                accent = accent,
                systemInDarkTheme = isSystemInDarkTheme(),
                customAccent = customAccent,
                focusHighlight = focusHighlight,
                focusBorderWidthDp = focusHighlightWidth,
                animationLevel = animationLevel,
                mainFontFamily = fontCustomization.mainFamily,
                popupFontFamily = fontCustomization.popupFamily,
                popupFontSizePercent = fontCustomization.popupFontSizePercent,
                popupSizePercent = fontCustomization.popupSizePercent,
            ) {
                val base = LocalDensity.current
                // Root viewport size in px — the area the background image fills and that the blurred
                // backdrop stands in for. Captured here (top of the tree) so glass panels can map their
                // own on-screen rect into the blurred bitmap's coordinate space.
                var rootSizePx by remember { mutableStateOf(Size.Zero) }
                // Glass & background: a picture is drawn blurred (its Blur) and glass frosts it, so the
                // blurred copy is built whenever a picture is shown, glass on or off. The blur itself is
                // CPU work done once per picture, so every device gets it.
                val needsBackdropAssets = background.showsPicture
                val supportsFrostPyramid = true
                val blurred by produceState<BlurredBackdrop?>(
                    initialValue = null,
                    bgImagePath,
                    needsBackdropAssets,
                    supportsFrostPyramid,
                    rootSizePx,
                ) {
                    if (!needsBackdropAssets || rootSizePx.width <= 0f || rootSizePx.height <= 0f) {
                        value = null
                        return@produceState
                    }
                    // Decode + blur on a background dispatcher; never block the main thread.
                    val path = bgImagePath
                    value = produceBlurredBackdrop(path, rootSizePx, supportsFrostPyramid)
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        base.density * UiZoom.factor(uiZoomPercent),
                        base.fontScale * UiFontScale.factor(fontCustomization.sizePercent),
                    ),
                    LocalUiFontScaleFactor provides UiFontScale.factor(fontCustomization.sizePercent),
                    LocalGlass provides glassConfig,
                    LocalBlurredBackdrop provides blurred,
                    LocalBackground provides background,
                ) {
                    // Wrap the shell in the locale locals (LocalResources/LocalContext/
                    // LocalConfiguration/LocalLayoutDirection) for the currently selected locale, and
                    // trigger the single documented Activity.recreate() across a script-family switch.
                    // Same-script switches apply instantly here. See docs/internationalization.md 0b.
                    tv.own.owntv.core.i18n.LocalizedContent(localeStore) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            // Solid base color first (fallback if no image / image fails to load).
                            .background(tv.own.owntv.ui.theme.LocalStagePalette.current.page)
                            .onGloballyPositioned {
                                rootSizePx = Size(it.size.width.toFloat(), it.size.height.toFloat())
                            },
                    ) {
                        // Background image sits behind everything when a path is set; otherwise the solid
                        // base color shows through and glass renders over that instead.
                        if (background.showsPicture) BackgroundLayer(background, blurred)

                        Box(modifier = Modifier.fillMaxSize()) {
                        val profile = activeProfileId
                        when {
                            profile == null || !profilesLoaded -> Unit // active id / Room list loading
                            // Adding a profile from the gate → onboard the new profile.
                            addingProfile -> AddProfileOnboarding(gateSession, Modifier.fillMaxSize())
                            // First run (no profile yet) → full onboarding.
                            profile < 0L -> Onboarding(
                                firstRun = true,
                                onDone = { profileId -> gateSession.authenticateProfile(profileId) },
                                onCancel = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            // A loaded list that does not contain the persisted active id is also
                            // recovery/onboarding, never permission to enter OwnTVShell. This covers
                            // both an empty restore window and a stale id left by an interrupted restore.
                            !activeProfileKnown -> Onboarding(
                                firstRun = false,
                                onDone = { profileId -> gateSession.authenticateProfile(profileId) },
                                onCancel = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Run 2+ (or a single locked profile): "Who's watching?" — choose a profile or add one.
                            // Also opens when the sidebar avatar is single-clicked (switchProfileRequested),
                            // which is the only way to switch when there's a single unpinned profile.
                            gateRequired && !authenticatedForActiveProfile -> ProfileGate(
                                onEnter = { profileId -> gateSession.authenticateProfile(profileId) },
                                onAddProfile = { gateSession.startAddingProfile() },
                                modifier = Modifier.fillMaxSize(),
                            )
                            shellReady -> OwnTVShell(
                                selectedSection = selectedSection,
                                visibleSections = visibleSections,
                                onSelectSection = viewModel::selectSection,
                                themeMode = themeMode,
                                uiZoomPercent = uiZoomPercent,
                                onSetZoom = viewModel::setUiZoom,
                                fontCustomization = fontCustomization,
                                onSetFontCustomization = viewModel::setFontCustomization,
                                avatarId = avatarId,
                                onSetAvatar = viewModel::setAvatar,
                                avatarPath = avatarPath,
                                onSetCustomAvatar = { file -> viewModel.setCustomAvatar(file) },
                                onClearCustomAvatar = viewModel::clearCustomAvatar,
                                profileName = profileName,
                                sourceSummary = sourceSummary,
                                playlists = playlists,
                                activePlaylistId = activePlaylistId,
                                onSelectPlaylist = viewModel::setActivePlaylist,
                                weatherInfo = weather,
                                weatherFahrenheit = weatherFahrenheit,
                                activeProfileId = activeProfileId,
                                pendingDeepLink = pendingDeepLink,
                                onDeepLinkConsumed = { pendingDeepLink = null },
                                isOffline = !isOnline,
                                onExitApp = { finish() },
                                onStartupSettled = { startupSettled = true },
                                onSwitchProfile = {
                                    // Stop playback and return to the "Who's watching?" gate — no app restart.
                                    player.onAppBackgrounded(); player.discardBackgroundRestore(); previewEngine.stop(); previewEngine.discardBackgroundRestore(); heroPreviewEngine.stop()
                                    // Force the gate open even with a single unpinned profile (cold-start gate would
                                    // skip it). requestSwitchProfile() clears the active profile
                                    // authentication and raises the request.
                                    gateSession.requestSwitchProfile()
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // No unexpected combination of asynchronous state may fall through to
                            // the shell. The known loading/onboarding/gate cases are above; a blank
                            // frame is safer than rendering protected content if a new state is added.
                            else -> Unit
                        }
                        } // end foreground Box (above the background layer)
                    }
                    } // end LocalizedContent
                }
            }
        }
    }
}

/** The user's picture behind the entire shell (Glass & background). Callers check [BackgroundConfig.showsPicture]. */
@androidx.compose.runtime.Composable
private fun BackgroundLayer(background: tv.own.owntv.core.theme.BackgroundConfig, blurred: BlurredBackdrop?) {
    tv.own.owntv.ui.components.BackgroundPicture(background, blurred, Modifier.fillMaxSize())
}
