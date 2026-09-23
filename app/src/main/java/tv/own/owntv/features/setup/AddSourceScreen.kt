package tv.own.owntv.features.setup

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.companion.CompanionPayload
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.HlsSupport
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.HlsProbe
import tv.own.owntv.core.parser.HlsTest
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SourceTestDialog
import tv.own.owntv.features.settings.SourceTestUi
import tv.own.owntv.core.repository.SourceTester
import tv.own.owntv.core.setup.MAG_USER_AGENTS
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.DayStepperDialog
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class SourceKind { XTREAM, M3U, STALKER }

/** UI state of the Xtream "Test HLS support" probe. Local to this screen — the probe is one short
 *  request and saves nothing unless the source already exists. */
private sealed interface HlsTestUi {
    data object Idle : HlsTestUi
    data object Testing : HlsTestUi
    data class Complete(val test: HlsTest) : HlsTestUi
    data class Failed(val rawMessage: String) : HlsTestUi
}


@Composable
fun AddSourceScreen(
    onStartXtream: (
        name: String,
        server: String,
        user: String,
        pass: String,
        userAgent: String,
        referer: String,
        epgUrl: String,
        autoRefresh: PlaylistRefresh,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
        isDefault: Boolean,
        preferHls: Boolean,
    ) -> Unit,
    onStartM3u: (name: String, url: String, userAgent: String, referer: String, epgUrl: String, autoRefresh: PlaylistRefresh, isDefault: Boolean) -> Unit,
    // The last submission from the Remote companion screen, retained as a StateFlow so it survives the
    // Remote → Manual hand-off (this screen mounts after the remote browser posted). When present, the matching
    // type is selected and the fields pre-filled; the user then presses Start Import. Consumed once via
    // [onRemotePayloadConsumed] so it can't re-fill a later, unrelated Manual add.
    remotePayload: kotlinx.coroutines.flow.StateFlow<CompanionPayload?>? = null,
    onRemotePayloadConsumed: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceEntity? = null,
    initialAutoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
    initialIsDefault: Boolean = false,
    showDefaultToggle: Boolean = true,
    // Stalker portal (plan Phase B). Null = the Stalker option is hidden (e.g. the setup wizard).
    onStartStalker: ((
        name: String,
        portalUrl: String,
        mac: String,
        serialNumber: String,
        deviceId: String,
        deviceId2: String,
        signature: String,
        userAgent: String,
        referer: String,
        autoRefresh: PlaylistRefresh,
        isDefault: Boolean,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
    ) -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    val editing = initial != null
    var kind by remember {
        mutableStateOf(
            when (initial?.type) {
                SourceType.M3U -> SourceKind.M3U
                SourceType.STALKER -> SourceKind.STALKER
                else -> SourceKind.XTREAM
            },
        )
    }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var server by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.XTREAM) initial.url else "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var m3uUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.M3U) initial.url else "") }
    var portalUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.STALKER) initial.url else "") }
    var mac by remember(initial) { mutableStateOf(initial?.mac ?: "") }
    var stalkerSerialNumber by remember(initial) { mutableStateOf(initial?.stalkerSerialNumber ?: "") }
    var stalkerDeviceId by remember(initial) { mutableStateOf(initial?.stalkerDeviceId ?: "") }
    var stalkerDeviceId2 by remember(initial) { mutableStateOf(initial?.stalkerDeviceId2 ?: "") }
    var stalkerSignature by remember(initial) { mutableStateOf(initial?.stalkerSignature ?: "") }
    var showUaPresetPicker by remember { mutableStateOf(false) }
    var epgUrl by remember(initial) { mutableStateOf(initial?.epgUrl ?: "") }
    var userAgent by remember(initial) { mutableStateOf(initial?.userAgent ?: "") }
    var referer by remember(initial) { mutableStateOf(initial?.httpReferer ?: "") }
    var autoRefresh by remember(initialAutoRefresh) { mutableStateOf(initialAutoRefresh) }
    var isDefault by remember(initialIsDefault) { mutableStateOf(initialIsDefault) }
    var preferHls by remember(initial) { mutableStateOf(initial?.preferHls == true) }
    // Edit: On(=Now)/Off from persisted flags. Add: default all Now for Xtream; Stalker defaults
    // Live Now + Movies/Series Later when the kind switches (see LaunchedEffect below).
    var syncLive by remember(initial) {
        mutableStateOf(if (initial?.syncLive == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var syncMovies by remember(initial) {
        mutableStateOf(if (initial?.syncMovies == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var syncSeries by remember(initial) {
        mutableStateOf(if (initial?.syncSeries == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var hasRemoteStalkerScopes by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    var showManualDaysPicker by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val startImportFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // Profile-wide "hide new categories on sync" default (issue #87, Phase 4). Written straight to
    // DataStore — no callback signature change — so it lands on the ACTIVE profile at add time. The row
    // is only shown while ADDING (an edit re-syncs nothing new) and only when a profile exists (the
    // setup wizard may still be profile-less; the value defaults to off there).
    val settings: SettingsRepository = koinInject()
    val profileDao: ProfileDao = koinInject()
    val scope = rememberCoroutineScope()
    var hideNewCatsProfile by remember { mutableStateOf(-1L) }
    LaunchedEffect(Unit) {
        val preferred = settings.activeProfileId.first()
        hideNewCatsProfile = profileDao.resolveExistingProfileId(preferred) ?: -1L
    }
    val hideNewCats by settings.hideNewCategoriesDefault(hideNewCatsProfile)
        .collectAsStateWithLifecycle(initialValue = false)

    // ---- "Test HLS support" (Xtream) ----
    // A panel's `allowed_output_formats` is a claim, and a common one to get wrong in both directions,
    // so the button also *requests* an `.m3u8` channel and reads the answer. Driven from here rather
    // than a ViewModel so both hosts (the setup wizard and Settings → Manage sources) get it without
    // duplicating the plumbing — it's one short request that stores nothing unless the source exists.
    val xtreamClient: XtreamClient = koinInject()
    val channelDao: ChannelDao = koinInject()
    val sourceDao: SourceDao = koinInject()
    var hlsTest by remember { mutableStateOf<HlsTestUi>(HlsTestUi.Idle) }
    // A tested verdict outranks whatever the last sync recorded, so the note under the toggle updates
    // right away — including while ADDING, where there is no row to write to yet.
    var testedSupport by remember(initial) { mutableStateOf<HlsSupport?>(null) }
    // A verdict belongs to the credentials it was measured against. Editing any of them drops it —
    // a green "HLS works" sitting next to a server URL it never tested is worse than no answer.
    LaunchedEffect(server, username, password) {
        hlsTest = HlsTestUi.Idle
        testedSupport = null
    }

    // ---- "Test connection" (all three source types) ----
    // Runs against what is TYPED, not what is saved, so an account can be checked while adding it.
    // Read-only: it asks the provider for its account status and stores nothing.
    val sourceTester: SourceTester = koinInject()
    var sourceTest by remember { mutableStateOf<SourceTestUi?>(null) }
    // True while the "this stops playback and takes a while" confirmation is up.
    var confirmMeasure by remember { mutableStateOf(false) }
    // Cancelling this closes the probe's streams — it releases them in a `finally` — so Skip hands
    // the provider's connections straight back instead of leaving them held.
    var measureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val connectionLimits: tv.own.owntv.core.live.ConnectionLimits = koinInject()
    val player: tv.own.owntv.player.OwnTVPlayer = koinInject()
    val livePreview: tv.own.owntv.player.LivePreviewEngine = koinInject()
    val enginePool: tv.own.owntv.player.LiveEnginePool = koinInject()

    fun formSource(): SourceEntity {
        fun opt(value: String) = value.trim().takeIf { it.isNotBlank() }
        val ua = opt(userAgent)
        val ref = opt(referer)
        return when (kind) {
            SourceKind.XTREAM -> SourceEntity(
                id = initial?.id ?: 0L, name = name, type = SourceType.XTREAM,
                url = server.trim(), username = username.trim(), password = password, userAgent = ua, httpReferer = ref,
            )
            SourceKind.M3U -> SourceEntity(
                id = initial?.id ?: 0L, name = name, type = SourceType.M3U,
                url = m3uUrl.trim(), userAgent = ua, httpReferer = ref,
            )
            SourceKind.STALKER -> SourceEntity(
                id = initial?.id ?: 0L, name = name, type = SourceType.STALKER,
                url = portalUrl.trim(), mac = mac.trim(),
                stalkerSerialNumber = opt(stalkerSerialNumber),
                stalkerDeviceId = opt(stalkerDeviceId),
                stalkerDeviceId2 = opt(stalkerDeviceId2),
                stalkerSignature = opt(stalkerSignature),
                userAgent = ua,
                httpReferer = ref,
            )
        }
    }

    /**
     * The credentials check: one short request, and the reason this button exists before saving.
     *
     * It does *not* measure the connection limit. There is no playlist row to store an answer
     * against yet, and the first sync measures it anyway — so doing it here would spend two minutes
     * and two connections producing a number that is thrown away.
     */
    fun runSourceTest() {
        val probe = formSource()
        val label = name.ifBlank { probe.url }
        sourceTest = SourceTestUi.Running(label)
        scope.launch {
            val result = sourceTester.test(probe)
            // Dismissed while the request was in flight — don't reopen the dialog behind the user.
            if (sourceTest != null) sourceTest = SourceTestUi.Done(label, result)
        }
    }

    /**
     * Measure the limit from the edit form, for a playlist that already exists.
     *
     * Offered only when editing, because the result is saved against the playlist's row. Playback is
     * stopped first and the user has already agreed to the warning that says so.
     */
    fun runConnectionMeasurement() {
        val source = initial ?: return
        val label = name.ifBlank { source.url }
        measureJob?.cancel()
        measureJob = scope.launch {
            runCatching { player.stop() }
            runCatching { livePreview.stop() }
            runCatching { enginePool.releaseAll() }
            sourceTest = SourceTestUi.Measuring(label, tv.own.owntv.core.live.ProbeProgress(1, 1, tv.own.owntv.core.live.MAX_PROBE_STREAMS))
            val limit = connectionLimits.measureAndStore(source, force = true) { progress ->
                if (sourceTest is SourceTestUi.Measuring) sourceTest = SourceTestUi.Measuring(label, progress)
            }
            val result = sourceTester.test(source)
            if (sourceTest != null) sourceTest = SourceTestUi.Done(label, result, limit)
        }
    }

    fun runHlsTest() {
        hlsTest = HlsTestUi.Testing
        scope.launch {
            val probeSource = SourceEntity(
                id = initial?.id ?: 0L,
                name = name,
                type = SourceType.XTREAM,
                url = server.trim(),
                username = username.trim(),
                password = password,
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
            )
            // Stream ids belong to the panel that issued them, so a saved channel is only a valid
            // shortcut while the form still points at the same account.
            val sameAccount = initial != null && initial.type == SourceType.XTREAM &&
                initial.url.trim() == probeSource.url &&
                initial.username?.trim() == probeSource.username &&
                initial.password == probeSource.password
            val result = try {
                val known = if (sameAccount && probeSource.id > 0) channelDao.anyRemoteId(probeSource.id) else null
                xtreamClient.testHlsSupport(probeSource, known)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                hlsTest = HlsTestUi.Failed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            hlsTest = HlsTestUi.Complete(result)
            // Only a decisive probe is worth storing. "Busy" and "couldn't verify" fall back to the
            // panel's claim for the wording, but must not overwrite an earlier proven answer.
            val proven = when (result.probe) {
                HlsProbe.Served -> HlsSupport.SUPPORTED
                is HlsProbe.NotServed -> HlsSupport.UNSUPPORTED
                else -> null
            }
            testedSupport = proven ?: result.declared?.let(HlsSupport::of) ?: HlsSupport.UNKNOWN
            if (proven != null && sameAccount && probeSource.id > 0) {
                sourceDao.updateHlsSupport(probeSource.id, proven)
            }
        }
    }

    // Pre-fill from a Remote (companion) submission handed off by the host. StateFlow replays its
    // current value to this new collector, so the payload posted before this screen mounted still lands.
    LaunchedEffect(remotePayload) {
        remotePayload?.collect { payload ->
            if (payload == null || editing) return@collect
            name = payload.name
            userAgent = payload.userAgent
            epgUrl = payload.epgUrl
            isDefault = payload.isDefault
            autoRefresh = PlaylistRefresh.parse(payload.autoRefresh)
            when (payload.type) {
                SourceType.M3U -> {
                    hasRemoteStalkerScopes = false
                    m3uUrl = payload.server
                    kind = SourceKind.M3U
                }
                SourceType.STALKER -> {
                    portalUrl = payload.portalUrl
                    mac = payload.mac
                    stalkerSerialNumber = payload.serialNumber
                    stalkerDeviceId = payload.deviceId
                    stalkerDeviceId2 = payload.deviceId2
                    stalkerSignature = payload.signature
                    syncLive = payload.syncLive
                    syncMovies = payload.syncMovies
                    syncSeries = payload.syncSeries
                    hasRemoteStalkerScopes = true
                    kind = SourceKind.STALKER
                }
                else -> {
                    server = payload.server
                    username = payload.user
                    password = payload.pass
                    syncLive = payload.syncLive
                    syncMovies = payload.syncMovies
                    syncSeries = payload.syncSeries
                    hasRemoteStalkerScopes = false
                    kind = SourceKind.XTREAM
                }
            }
            onRemotePayloadConsumed() // one-shot: don't re-fill a later, unrelated Manual add
            runCatching { kotlinx.coroutines.delay(150); startImportFocus.requestFocus() }
        }
    }

    // Stalker add defaults: Live Now, Movies/Series Later (VOD has no bulk endpoint). Skip when
    // editing, retrying a failed add, or applying explicit choices from a remote Stalker payload.
    LaunchedEffect(kind, initial, hasRemoteStalkerScopes) {
        if (initial != null || kind != SourceKind.STALKER || hasRemoteStalkerScopes) return@LaunchedEffect
        if (syncLive == SyncScopeChoice.Now && syncMovies == SyncScopeChoice.Now && syncSeries == SyncScopeChoice.Now) {
            syncMovies = SyncScopeChoice.Later
            syncSeries = SyncScopeChoice.Later
        }
    }

    val showContentToggles = kind == SourceKind.XTREAM || kind == SourceKind.STALKER
    val hasAnySectionOn = syncLive != SyncScopeChoice.Off || syncMovies != SyncScopeChoice.Off || syncSeries != SyncScopeChoice.Off
    val macValid = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac) != null
    val canStart = when (kind) {
        SourceKind.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank() && hasAnySectionOn
        SourceKind.M3U -> m3uUrl.isNotBlank()
        SourceKind.STALKER -> tv.own.owntv.core.stalker.StalkerClient.isValidPortalUrl(portalUrl) && macValid && hasAnySectionOn
    }

    val canTest = when (kind) {
        SourceKind.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        SourceKind.M3U -> m3uUrl.isNotBlank() && !m3uUrl.startsWith("/")
        SourceKind.STALKER -> tv.own.owntv.core.stalker.StalkerClient.isValidPortalUrl(portalUrl) && macValid
    }

    Box(modifier.fillMaxSize().roundedPanel()) {
      Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (editing) stringResource(R.string.setup_edit_source) else stringResource(R.string.setup_add_your_source), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                if (editing) stringResource(R.string.setup_edit_source_description) else stringResource(R.string.setup_byo_source_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            // Source type selector (locked while editing — the type can't change, so initial focus
            // goes to the Name field instead of a dead chip).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // While editing, the type is fixed — show only the matching chip.
                if (!editing || kind == SourceKind.XTREAM) {
                    KindChip(stringResource(R.string.setup_xtream), kind == SourceKind.XTREAM, Modifier.weight(1f).then(if (!editing) Modifier.focusRequester(firstFocus) else Modifier)) { if (!editing) kind = SourceKind.XTREAM }
                }
                if (!editing || kind == SourceKind.M3U) {
                    KindChip(stringResource(R.string.setup_m3u), kind == SourceKind.M3U, Modifier.weight(1f)) { if (!editing) kind = SourceKind.M3U }
                }
                if (onStartStalker != null && (!editing || kind == SourceKind.STALKER)) {
                    KindChip(stringResource(R.string.setup_stalker_mac), kind == SourceKind.STALKER, Modifier.weight(1f)) { if (!editing) kind = SourceKind.STALKER }
                }
            }
            Spacer(Modifier.height(20.dp))

            OwnTVTextField(name, { name = it }, label = stringResource(R.string.setup_source_name_optional), placeholder = stringResource(R.string.setup_default_iptv), modifier = Modifier.fillMaxWidth(), focusRequester = if (editing) firstFocus else null)
            Spacer(Modifier.height(14.dp))

            when (kind) {
                SourceKind.XTREAM -> {
                    OwnTVTextField(server, { server = it }, label = stringResource(R.string.setup_server_url), placeholder = stringResource(R.string.setup_server_example), keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(username, { username = it }, label = stringResource(R.string.setup_username), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(password, { password = it }, label = if (editing) stringResource(R.string.setup_password_keep) else stringResource(R.string.setup_password), isPassword = true, modifier = Modifier.fillMaxWidth())
                }
                SourceKind.M3U -> {
                    val pickedName = remember(m3uUrl) {
                        if (m3uUrl.startsWith("/")) java.io.File(m3uUrl).name else null
                    }
                    OwnTVTextField(m3uUrl, { m3uUrl = it }, label = stringResource(R.string.setup_playlist_url_local_file), placeholder = stringResource(R.string.setup_playlist_example), keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        label = if (pickedName != null) stringResource(R.string.setup_local_file_prefix, pickedName) else stringResource(R.string.setup_local_file_choose),
                        onClick = { showFileBrowser = true },
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SourceKind.STALKER -> {
                    OwnTVTextField(portalUrl, { portalUrl = it }, label = stringResource(R.string.setup_portal_url), placeholder = stringResource(R.string.setup_portal_example), keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(mac, { mac = it }, label = stringResource(R.string.setup_mac_address), placeholder = stringResource(R.string.setup_mac_example), modifier = Modifier.fillMaxWidth())
                    if (mac.isNotBlank() && !macValid) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.setup_mac_invalid), style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.setup_stalker_advanced_identity), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    OwnTVTextField(stalkerSerialNumber, { stalkerSerialNumber = it }, label = stringResource(R.string.setup_stalker_serial_number_optional), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OwnTVTextField(stalkerDeviceId, { stalkerDeviceId = it }, label = stringResource(R.string.setup_stalker_device_id_optional), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OwnTVTextField(stalkerDeviceId2, { stalkerDeviceId2 = it }, label = stringResource(R.string.setup_stalker_device_id2_optional), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OwnTVTextField(stalkerSignature, { stalkerSignature = it }, label = stringResource(R.string.setup_stalker_signature_optional), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        label = stringResource(R.string.setup_device_model_preset),
                        onClick = { showUaPresetPicker = true },
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // EPG is managed separately now (Settings → EPG Sources), so no EPG field here. For an
            // Xtream server the guide URL is still derived automatically; M3U EPG can be added there.
            Spacer(Modifier.height(14.dp))
            OwnTVTextField(userAgent, { userAgent = it }, label = stringResource(R.string.setup_user_agent_optional), placeholder = stringResource(R.string.setup_user_agent_example), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OwnTVTextField(referer, { referer = it }, label = stringResource(R.string.setup_referer_optional), placeholder = stringResource(R.string.setup_referer_example), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            OwnTVButton(
                label = stringResource(if (sourceTest is SourceTestUi.Running) R.string.setup_testing else R.string.setup_test_connection),
                onClick = { if (sourceTest == null) runSourceTest() },
                style = OwnTVButtonStyle.SECONDARY,
                enabled = canTest,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            // Auto-refresh dropdown (replaces the old binary "Refresh on startup" toggle). Off/Startup or a
            // staleness threshold — the source is refreshed when its data is at least this old.
            AutoRefreshRow(selected = autoRefresh) { showAutoRefreshPicker = true }

            if (showDefaultToggle) {
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = stringResource(R.string.setup_default_playlist),
                    desc = stringResource(R.string.setup_default_playlist_description),
                    checked = isDefault,
                ) { isDefault = it }
            }

            // Shown for every Xtream source, on Add (incl. the setup wizard and the Remote hand-off)
            // as well as Edit: `hlsSupported` is only known AFTER the first sync has read
            // user_info.allowed_output_formats, so gating the row on it would hide the option on a
            // fresh install entirely. Detection only refines the wording below — it never disables the
            // toggle, because a panel that under-reports its formats must not veto the user's choice.
            if (kind == SourceKind.XTREAM) {
                Spacer(Modifier.height(16.dp))
                OwnTVButton(
                    label = stringResource(
                        if (hlsTest is HlsTestUi.Testing) R.string.setup_hls_testing else R.string.setup_hls_test_support,
                    ),
                    // Re-entry is blocked HERE rather than through `enabled`: a disabled FocusableSurface
                    // is not a focus target, so flipping it off under the user's cursor would drop D-pad
                    // focus off the screen for the length of the probe.
                    onClick = { if (hlsTest !is HlsTestUi.Testing) runHlsTest() },
                    style = OwnTVButtonStyle.SECONDARY,
                    // Needs the credentials, but not a synced playlist: the probe pulls one stream id
                    // straight off the panel when the source is new.
                    enabled = server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val t = hlsTest) {
                    is HlsTestUi.Complete -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t.test.displayText(LocalContext.current.resources),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (t.test.probe is HlsProbe.Served) colors.primary else Color(0xFFEF4444),
                        )
                    }
                    is HlsTestUi.Failed -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.setup_hls_test_failed, t.rawMessage),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444),
                        )
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = stringResource(R.string.setup_prefer_hls_live_tv),
                    desc = stringResource(
                        when (testedSupport ?: initial?.hlsSupported ?: HlsSupport.UNKNOWN) {
                            HlsSupport.SUPPORTED -> R.string.setup_prefer_hls_description_supported
                            HlsSupport.UNSUPPORTED -> R.string.setup_prefer_hls_description_unsupported
                            HlsSupport.UNKNOWN -> R.string.setup_prefer_hls_description
                        },
                    ),
                    checked = preferHls,
                ) { preferHls = it }
            }

            if (showContentToggles) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.setup_what_to_sync), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (editing) {
                        stringResource(R.string.setup_sync_off_editing)
                    } else {
                        stringResource(R.string.setup_sync_choices)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SyncScopeRow(label = stringResource(R.string.setup_live_tv), desc = stringResource(R.string.setup_channels_categories), value = syncLive, editing = editing) { syncLive = it }
                Spacer(Modifier.height(8.dp))
                SyncScopeRow(label = stringResource(R.string.setup_movies), desc = stringResource(R.string.setup_vod_movie_catalog), value = syncMovies, editing = editing) { syncMovies = it }
                Spacer(Modifier.height(8.dp))
                SyncScopeRow(label = stringResource(R.string.setup_series), desc = stringResource(R.string.setup_tv_series_catalog), value = syncSeries, editing = editing) { syncSeries = it }
            }

            if (!editing && hideNewCatsProfile >= 0) {
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = stringResource(R.string.setup_hide_new_categories),
                    desc = stringResource(R.string.setup_hide_new_categories_description),
                    checked = hideNewCats,
                ) { hidden -> scope.launch { settings.setHideNewCategoriesDefault(hideNewCatsProfile, hidden) } }
            }

            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    label = if (editing) stringResource(R.string.setup_update_source_save) else stringResource(R.string.setup_start_import),
                    onClick = {
                        when (kind) {
                            SourceKind.XTREAM -> onStartXtream(name, server, username, password, userAgent, referer, epgUrl, autoRefresh, syncLive, syncMovies, syncSeries, isDefault, preferHls)
                            SourceKind.M3U -> onStartM3u(name, m3uUrl, userAgent, referer, epgUrl, autoRefresh, isDefault)
                            SourceKind.STALKER -> onStartStalker?.invoke(
                                name, portalUrl, mac, stalkerSerialNumber, stalkerDeviceId, stalkerDeviceId2,
                                stalkerSignature, userAgent, referer, autoRefresh, isDefault, syncLive, syncMovies, syncSeries,
                            )
                        }
                    },
                    enabled = canStart,
                    modifier = Modifier.focusRequester(startImportFocus),
                )
            }
        }
      }
      // In-app, TV-safe file picker (SAF / system file picker is missing on many TVs).
      if (showFileBrowser) {
          StorageBrowser(
              title = stringResource(R.string.setup_pick_playlist_file),
              mode = BrowseMode.FILE,
              fileExtensions = setOf("m3u", "m3u8"),
              onPick = { file ->
                  showFileBrowser = false
                  m3uUrl = file.absolutePath
                  if (name.isBlank()) name = file.nameWithoutExtension
              },
              onDismiss = { showFileBrowser = false },
          )
      }
      if (showUaPresetPicker) {
          PickerDialog(
              title = stringResource(R.string.setup_device_model_preset_title),
              options = MAG_USER_AGENTS.map { it.labelRes.toString() to stringResource(it.labelRes) },
              selected = MAG_USER_AGENTS.firstOrNull { it.userAgent == userAgent }?.labelRes?.toString()
                  ?: MAG_USER_AGENTS.first().labelRes.toString(),
              onSelect = { labelRes ->
                  userAgent = MAG_USER_AGENTS.firstOrNull { it.labelRes.toString() == labelRes }?.userAgent.orEmpty()
                  showUaPresetPicker = false
              },
              onDismiss = { showUaPresetPicker = false },
          )
      }
      if (showAutoRefreshPicker) {
          PickerDialog(
              title = stringResource(R.string.setup_auto_refresh_title),
              options = PlaylistAutoRefresh.entries.map { it.name to refreshModeLabel(it) },
              selected = autoRefresh.mode.name,
              onSelect = { value ->
                  val mode = runCatching { PlaylistAutoRefresh.valueOf(value) }.getOrDefault(PlaylistAutoRefresh.OFF)
                  showAutoRefreshPicker = false
                  // Manual needs a day count, so it hands straight over to the stepper; cancelling
                  // there leaves the previous selection untouched.
                  if (mode == PlaylistAutoRefresh.MANUAL) showManualDaysPicker = true
                  else autoRefresh = PlaylistRefresh(mode, autoRefresh.manualDays)
              },
              onDismiss = { showAutoRefreshPicker = false },
          )
      }
      if (showManualDaysPicker) {
          ManualDaysDialog(
              initialDays = autoRefresh.manualDays,
              onConfirm = { days ->
                  autoRefresh = PlaylistRefresh(PlaylistAutoRefresh.MANUAL, days)
                  showManualDaysPicker = false
              },
              onDismiss = { showManualDaysPicker = false },
          )
      }
      if (confirmMeasure) {
          tv.own.owntv.features.settings.ConfirmDialog(
              title = stringResource(R.string.settings_sources_probe_title),
              message = stringResource(R.string.settings_sources_probe_warning),
              onConfirm = { confirmMeasure = false; runConnectionMeasurement() },
              onDismiss = { confirmMeasure = false },
              confirmLabel = R.string.settings_sources_retest,
          )
      }
      sourceTest?.let { state ->
          SourceTestDialog(
              state = state,
              onDismiss = { sourceTest = null },
              // Editing only: an unsaved playlist has nowhere to keep the answer.
              onRetest = if (initial != null) { { confirmMeasure = true } } else null,
              onSkip = { measureJob?.cancel(); measureJob = null; sourceTest = null },
          )
      }
    }
}

/** Picker entry for a mode. Manual reads as an ellipsis label — it opens the day stepper. */
@Composable
private fun refreshModeLabel(mode: PlaylistAutoRefresh): String = stringResource(
    when (mode) {
        PlaylistAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        PlaylistAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        PlaylistAutoRefresh.HOURS_6 -> R.string.settings_sources_refresh_6h
        PlaylistAutoRefresh.HOURS_12 -> R.string.settings_sources_refresh_12h
        PlaylistAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)

/** The chosen selection as shown on a row: Manual spells out the day count it resolved to. */
@Composable
internal fun playlistAutoRefreshLabel(refresh: PlaylistRefresh): String =
    if (refresh.mode == PlaylistAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        refreshModeLabel(refresh.mode)
    }

/**
 * Day stepper for the Manual auto-refresh interval — [DayStepperDialog] with the playlist's own
 * wording and bounds.
 */
@Composable
private fun ManualDaysDialog(initialDays: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    DayStepperDialog(
        title = stringResource(R.string.settings_sources_refresh_days_title),
        hint = stringResource(R.string.settings_sources_refresh_days_hint),
        initialDays = initialDays,
        minDays = PlaylistRefresh.MIN_MANUAL_DAYS,
        maxDays = PlaylistRefresh.MAX_MANUAL_DAYS,
        label = { days -> pluralStringResource(R.plurals.settings_sources_refresh_days, days, days) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** A focusable settings row showing the current auto-refresh selection; opens a picker on click. */
@Composable
private fun AutoRefreshRow(selected: PlaylistRefresh, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_auto_refresh), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    stringResource(R.string.setup_auto_refresh_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                playlistAutoRefreshLabel(selected),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { onToggle(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.size(52.dp, 30.dp).clip(CircleShape).background(if (checked) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

/** Single focusable row; D-pad ◀/▶ (or click) cycles Now/Later/Off — Edit uses On/Off only. */
@Composable
private fun SyncScopeRow(
    label: String,
    desc: String,
    value: SyncScopeChoice,
    editing: Boolean,
    onChange: (SyncScopeChoice) -> Unit,
) {
    val colors = OwnTVTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val options = if (editing) {
        listOf(SyncScopeChoice.Now, SyncScopeChoice.Off)
    } else {
        listOf(SyncScopeChoice.Now, SyncScopeChoice.Later, SyncScopeChoice.Off)
    }
    fun cycle(delta: Int) {
        val idx = options.indexOf(value).coerceAtLeast(0)
        onChange(options[(idx + delta + options.size) % options.size])
    }
    @Composable
    fun optionLabel(choice: SyncScopeChoice): String = when (choice) {
        SyncScopeChoice.Now -> if (editing) stringResource(R.string.setup_on) else stringResource(R.string.setup_now)
        SyncScopeChoice.Later -> stringResource(R.string.setup_later)
        SyncScopeChoice.Off -> stringResource(R.string.setup_off)
    }
    FocusableSurface(
        onClick = { cycle(+1) },
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key.horizontalDirection(layoutDirection)) {
                    HorizontalDirection.START -> { cycle(-1); true }
                    HorizontalDirection.END -> { cycle(+1); true }
                    null -> false
                }
            },
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.setup_scope_value, optionLabel(value)),
                style = MaterialTheme.typography.titleMedium,
                color = if (value == SyncScopeChoice.Off) colors.onSurfaceVariant else colors.primary,
            )
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        shape = RoundedCornerShape(14.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}
