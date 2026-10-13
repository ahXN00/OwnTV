package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.HostPool
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageAction
import tv.own.owntv.features.settings.StageActionColumn
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsNote
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The Xtream form's backup servers while the form is open (host failover, ui-design Q1–Q4). Nothing
 * here is written until the form's own Save: the list, the automatic-switch toggle and a pending
 * "Use first".
 */
@Stable
class BackupServersForm(initial: SourceEntity?, inUseNow: String?) {
    var backups by mutableStateOf(HostPool.sanitize(HostPool.decode(initial?.backupUrls), initial?.url))
    var failoverEnabled by mutableStateOf(initial?.failoverEnabled ?: true)
    /** A backup the user chose with Use first; it becomes the in-use server on Save. */
    var useFirst by mutableStateOf<String?>(null)
    /** The server in use when the form opened (an origin), for the IN USE marker. */
    val inUse: String? = inUseNow

    /** The server the list marks IN USE, given the form's [primary] text. */
    fun marked(primary: String): String? = useFirst ?: inUse?.takeIf { it in backups } ?: HostPool.origin(primary)
}

/** What the form hands its Save: the backup list (origins), the switch, and a pending Use first. */
data class BackupServersChoice(
    val backups: List<String> = emptyList(),
    val failoverEnabled: Boolean = true,
    val useFirst: String? = null,
)

/** The Backup servers row's description in the form, for [primary] as typed. */
@Composable
fun backupServersSummary(form: BackupServersForm, primary: String): String = when {
    HostPool.hasPathPrefix(primary) -> stringResource(R.string.settings_sources_backup_path_note)
    form.backups.isEmpty() -> stringResource(R.string.settings_sources_backup_summary_none)
    else -> form.marked(primary)
        ?.takeIf { it != HostPool.origin(primary) }
        ?.let { pluralStringResource(R.plurals.settings_sources_backup_summary_active, form.backups.size, form.backups.size, HostPool.display(it)) }
        ?: pluralStringResource(R.plurals.settings_sources_backup_summary_count, form.backups.size, form.backups.size)
}

@Composable
fun BackupServersScreen(
    form: BackupServersForm,
    primary: String,
    parentTitle: String,
    onBack: () -> Unit,
    onTestAll: (() -> Unit)? = null,
) {
    val title = stringResource(R.string.settings_sources_backup_title)
    val back = stringResource(R.string.common_back)
    val ok = stringResource(R.string.common_ok)
    val pathPrefix = HostPool.hasPathPrefix(primary)
    val primaryOrigin = HostPool.origin(primary)
    var addText by remember { mutableStateOf("") }
    var addAbout by remember { mutableStateOf<String?>(null) }
    var editIndex by remember { mutableIntStateOf(-1) }
    var editText by remember { mutableStateOf("") }
    var editAbout by remember { mutableStateOf<String?>(null) }
    var contextIndex by remember { mutableIntStateOf(-1) }
    var moving by remember { mutableIntStateOf(-1) }
    var beforeMove by remember { mutableStateOf<List<String>>(emptyList()) }
    val addFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    val rowFocus = remember(form.backups.size) { List(form.backups.size) { FocusRequester() } }
    val primaryFocus = remember { FocusRequester() }
    val marked = form.marked(primary)
    // Focus after a change of rows lands on the row that changed (Move, Edit, Use first).
    var focusAfter by remember { mutableIntStateOf(-2) }
    LaunchedEffect(focusAfter, form.backups) {
        val i = focusAfter
        if (i >= -1) {
            kotlinx.coroutines.delay(60)
            runCatching { if (i == -1) addFocus.requestFocus() else rowFocus.getOrNull(i)?.requestFocus() }
            focusAfter = -2
        }
    }

    val fullText = pluralStringResource(R.plurals.settings_sources_backup_full, HostPool.MAX_BACKUPS, HostPool.MAX_BACKUPS)
    val addedTemplate = R.plurals.settings_sources_backup_added
    val res = androidx.compose.ui.platform.LocalResources.current

    fun addTyped() {
        val text = addText.trim()
        if (text.isEmpty()) { addAbout = null; return }
        val r = HostPool.add(text, primary, form.backups)
        form.backups = r.backups
        addAbout = when {
            r.added == 0 && r.duplicates == 0 && r.backups.size >= HostPool.MAX_BACKUPS -> fullText
            r.added + r.duplicates + r.invalid.size == 1 && r.invalid.size == 1 ->
                if (r.backups.size >= HostPool.MAX_BACKUPS) fullText else res.getString(R.string.settings_sources_backup_invalid, r.invalid.first())
            r.added + r.duplicates + r.invalid.size > 1 || r.duplicates > 0 ->
                res.getQuantityString(addedTemplate, r.added, r.added, r.duplicates, r.invalid.size)
            else -> null
        }
        // A clean add clears the field; anything skipped stays so the user can see and fix it.
        addText = if (r.invalid.isEmpty()) "" else r.invalid.joinToString(" ")
    }

    fun finishEdit(i: Int) {
        val text = editText.trim()
        val o = HostPool.normalize(text, primary)
        val others = form.backups.filterIndexed { j, _ -> j != i }
        when {
            text.isEmpty() -> { editIndex = -1; editAbout = null }
            o == null -> editAbout = res.getString(R.string.settings_sources_backup_invalid, text)
            o == primaryOrigin || o in others -> { editIndex = -1; editAbout = null }
            else -> {
                val old = form.backups[i]
                form.backups = form.backups.toMutableList().also { it[i] = o }
                if (form.useFirst == old) form.useFirst = o
                editIndex = -1
                editAbout = null
                focusAfter = i
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        StageFullPage(
            parents = listOf(parentTitle),
            title = title,
            count = form.backups.size.takeIf { it > 0 }?.toString().orEmpty(),
            onBack = onBack,
            handleBack = moving < 0,
            rowsFocus = if (form.backups.isEmpty()) addFocus else rowFocus.getOrNull(form.backups.indexOf(marked)) ?: primaryFocus,
            settingsRoot = false,
        ) {
            if (pathPrefix) {
                StageSettingsNote(
                    stringResource(R.string.settings_sources_backup_path_note),
                    stringResource(R.string.settings_sources_backup_path_note_detail),
                )
            }
            val addLabel = stringResource(R.string.settings_sources_backup_add)
            StageFieldRow(
                icon = OwnTVIcon.ADD,
                label = addLabel,
                value = addText,
                onValueChange = { addText = it; addAbout = null },
                help = SettingHelp(addLabel, listOfNotNull(addAbout, stringResource(R.string.settings_sources_backup_add_help), stringResource(R.string.settings_sources_backup_same_login)).joinToString("\n")),
                placeholder = addAbout ?: stringResource(R.string.settings_sources_backup_add_help),
                keyboardType = KeyboardType.Uri,
                onDone = ::addTyped,
                backLabel = parentTitle,
                modifier = Modifier.focusRequester(addFocus),
            )
            if (form.backups.isEmpty()) StageSettingsNote(stringResource(R.string.settings_sources_backup_empty), null)

            // Row 0: the primary, read-only here (changed in Server URL).
            val primaryText = stringResource(R.string.settings_sources_backup_primary, HostPool.display(primary))
            val inUseWord = stringResource(R.string.settings_sources_backup_in_use)
            val primaryInUse = marked == primaryOrigin
            val talkSep = stringResource(R.string.settings_sources_backup_talkback_separator)
            val dotSep = stringResource(R.string.content_genres_separator)
            StageSettingRow(
                icon = OwnTVIcon.STAR,
                title = primaryText,
                desc = if (primaryInUse) stringResource(R.string.settings_sources_backup_in_use_desc) else stringResource(R.string.settings_sources_backup_primary_help),
                value = if (primaryInUse) SettingValue.Action(inUseWord) else null,
                onClick = {},
                help = SettingHelp(primaryText, stringResource(R.string.settings_sources_backup_primary_help), hints = listOf(back to parentTitle)),
                modifier = Modifier.focusRequester(primaryFocus).semantics {
                    contentDescription = if (primaryInUse) listOf(primaryText, inUseWord).joinToString(talkSep) else primaryText
                },
            )

            val notSecureWord = stringResource(R.string.settings_sources_backup_not_secure)
            val notSecureDesc = stringResource(R.string.settings_sources_backup_not_secure_desc)
            val inUseDesc = stringResource(R.string.settings_sources_backup_in_use_desc)
            val editLabel = stringResource(R.string.common_edit)
            val moveLabel = stringResource(R.string.settings_sources_backup_move_title)
            val useFirstLabel = stringResource(R.string.settings_sources_backup_use_first)
            val useFirstDesc = stringResource(R.string.settings_sources_backup_use_first_desc)
            val deleteLabel = stringResource(R.string.common_delete)
            val actionsKey = stringResource(R.string.settings_key_actions)
            form.backups.forEachIndexed { i, host ->
                val shown = HostPool.display(host)
                val rowDesc = stringResource(R.string.settings_sources_backup_row_desc, i + 1)
                if (editIndex == i) {
                    StageFieldRow(
                        icon = OwnTVIcon.PENCIL,
                        label = rowDesc,
                        value = editText,
                        onValueChange = { editText = it; editAbout = null },
                        help = SettingHelp(rowDesc, listOfNotNull(editAbout, stringResource(R.string.settings_sources_backup_add_help), stringResource(R.string.settings_sources_backup_same_login)).joinToString("\n")),
                        placeholder = editAbout ?: shown,
                        keyboardType = KeyboardType.Uri,
                        onDone = { finishEdit(i) },
                        backLabel = title,
                        modifier = Modifier.focusRequester(rowFocus[i]),
                    )
                    return@forEachIndexed
                }
                val inUse = host == marked
                val notSecure = HostPool.isNotSecure(host, primary)
                val desc = listOfNotNull(rowDesc, notSecureWord.takeIf { notSecure }, inUseDesc.takeIf { inUse }).joinToString(dotSep)
                val actions = listOfNotNull(
                    StageAction(OwnTVIcon.PENCIL, editLabel, {
                        contextIndex = i; editText = host; editAbout = null; editIndex = i; focusAfter = i
                    }),
                    StageAction(OwnTVIcon.MOVE, moveLabel, {
                        contextIndex = i; beforeMove = form.backups; moving = i
                    }).takeIf { form.backups.size > 1 },
                    StageAction(OwnTVIcon.STAR, useFirstLabel, {
                        form.backups = listOf(host) + form.backups.filter { it != host }
                        form.useFirst = host
                        contextIndex = 0
                        focusAfter = 0
                    }).takeIf { !pathPrefix },
                    StageAction(OwnTVIcon.TRASH, deleteLabel, {
                        form.backups = form.backups.filter { it != host }
                        if (form.useFirst == host) form.useFirst = null
                        contextIndex = -1
                        focusAfter = if (form.backups.isEmpty()) -1 else (i.coerceAtMost(form.backups.lastIndex))
                    }, danger = true),
                )
                StageSettingRow(
                    icon = if (notSecure) OwnTVIcon.WARNING else OwnTVIcon.NETWORK,
                    title = shown,
                    desc = desc,
                    value = if (inUse) SettingValue.Action(inUseWord) else null,
                    onClick = { contextIndex = i; runCatching { actionsFocus.requestFocus() } },
                    keepPanel = contextIndex == i,
                    help = SettingHelp(
                        title = host,
                        text = listOfNotNull(desc, notSecureDesc.takeIf { notSecure }, useFirstDesc.takeIf { !pathPrefix && !inUse }).joinToString("\n"),
                        hints = listOf("▶" to actionsKey, ok to actionsKey, back to parentTitle),
                        extra = { StageActionColumn(actions, rowFocus[i], actionsFocus) },
                    ),
                    modifier = Modifier
                        .focusRequester(rowFocus[i])
                        .focusProperties { right = actionsFocus }
                        .onFocusChanged { if (it.isFocused) contextIndex = i }
                        .semantics {
                            contentDescription = listOfNotNull(
                                rowDesc,
                                shown,
                                notSecureWord.lowercase().takeIf { notSecure },
                                inUseWord.lowercase().takeIf { inUse },
                            ).joinToString(talkSep)
                        },
                )
            }
            if (onTestAll != null && form.backups.isNotEmpty()) {
                val testAllTitle = pluralStringResource(
                    R.plurals.settings_sources_backup_test_all_title,
                    form.backups.size + 1,
                    form.backups.size + 1,
                )
                StageSettingRow(
                    icon = OwnTVIcon.INFO,
                    title = testAllTitle,
                    desc = null,
                    value = null,
                    onClick = onTestAll,
                    help = SettingHelp(testAllTitle, testAllTitle, hints = listOf(ok to testAllTitle, back to parentTitle)),
                )
            }
            Text(
                stringResource(R.string.settings_sources_backup_save_hint),
                style = stageText(17, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(horizontal = 22.mpx, vertical = 12.mpx),
            )
        }

        if (moving in form.backups.indices) {
            MoveOrderOverlay(
                title = moveLabelText(),
                itemNames = form.backups.map(HostPool::display),
                activeIndex = moving,
                onMoveUp = {
                    if (moving > 0) {
                        form.backups = form.backups.toMutableList().also { java.util.Collections.swap(it, moving, moving - 1) }
                        moving -= 1
                    }
                },
                onMoveDown = {
                    if (moving < form.backups.lastIndex) {
                        form.backups = form.backups.toMutableList().also { java.util.Collections.swap(it, moving, moving + 1) }
                        moving += 1
                    }
                },
                onCommit = { focusAfter = moving; contextIndex = moving; moving = -1 },
                onCancel = { form.backups = beforeMove; focusAfter = form.backups.indexOf(beforeMove.getOrNull(contextIndex)); moving = -1 },
            )
        }
    }
}

@Composable
private fun moveLabelText(): String = stringResource(R.string.settings_sources_backup_move_title)
