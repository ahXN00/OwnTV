package tv.own.owntv.features.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.subtitles.LocalSubtitleFolder
import tv.own.owntv.core.subtitles.SubtitleController
import tv.own.owntv.core.subtitles.SubtitleFolderMatch

/**
 * Backs "Find in folder": the chosen folder's subtitle files, best match for the item playing first,
 * narrowed by what is typed in the search box. Picking one attaches it through [SubtitleController].
 */
class FolderSubtitleViewModel(
    private val controller: SubtitleController,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Null while the folder is read; empty when it holds no subtitle files. */
    private val files = MutableStateFlow<List<LocalSubtitleFolder.SubtitleFile>?>(null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** [files] narrowed by [query]; null while loading. */
    val shown: StateFlow<List<LocalSubtitleFolder.SubtitleFile>?> = combine(files, _query) { all, q ->
        all?.let { SubtitleFolderMatch.filter(it, q) { file -> file.name } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasFiles: StateFlow<Boolean> = files.map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val folder: StateFlow<String?> = settings.subtitleFolder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val autoLoad: StateFlow<Boolean> = settings.subtitleFolderAutoLoad.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Non-null while a picked file is being read and attached. */
    private val _applying = MutableStateFlow<String?>(null)
    val applying: StateFlow<String?> = _applying.asStateFlow()

    /** One-shot outcome of a pick: true attached (close the list), false it could not be read. */
    private val _picked = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val picked: SharedFlow<Boolean> = _picked.asSharedFlow()

    /** (Re)reads the folder — each time the list opens, since the ViewModel outlives one opening. */
    fun start() {
        files.value = null
        _query.value = ""
        _applying.value = null
        viewModelScope.launch { files.value = runCatching { controller.folderSubtitles() }.getOrNull().orEmpty() }
    }

    fun setQuery(text: String) { _query.value = text }

    fun setAutoLoad(enabled: Boolean) { viewModelScope.launch { settings.setSubtitleFolderAutoLoad(enabled) } }

    fun select(file: LocalSubtitleFolder.SubtitleFile) {
        if (_applying.value != null) return
        _applying.value = file.location
        viewModelScope.launch {
            val ok = runCatching { controller.applyFromFolder(file) }.isSuccess
            _applying.value = null
            _picked.tryEmit(ok)
        }
    }
}
