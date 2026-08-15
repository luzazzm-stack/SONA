package app.sona

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sona.core.Album
import app.sona.core.Artist
import app.sona.core.FavoritesStore
import app.sona.core.Folder
import app.sona.core.MediaStoreRepository
import app.sona.core.Song
import app.sona.core.SortMode
import app.sona.core.UiPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LibraryState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val sort: SortMode = SortMode.Title,
    val tab: Int = 0,
) {
    val favoriteSongs: List<Song> get() = songs.filter { it.id in favorites }
}

private data class Built(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val folders: List<Folder>,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    init {
        // Started before load() is ever called; the tiny prefs read lands well ahead of the
        // MediaStore scan, so the first loaded render is already in the persisted sort order.
        viewModelScope.launch {
            UiPrefsStore.flow(getApplication()).collect { prefs ->
                _state.update { it.copy(sort = prefs.sort, tab = prefs.libraryTab) }
            }
        }
        viewModelScope.launch {
            FavoritesStore.flow(getApplication()).collect { favs ->
                _state.update { it.copy(favorites = favs) }
            }
        }
    }

    fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val built = withContext(Dispatchers.IO) {
                val songs = runCatching { MediaStoreRepository.songs(getApplication()) }.getOrDefault(emptyList())
                Built(songs, MediaStoreRepository.albums(songs), MediaStoreRepository.artists(songs), MediaStoreRepository.folders(songs))
            }
            _state.update {
                it.copy(loaded = true, loading = false, songs = built.songs, albums = built.albums, artists = built.artists, folders = built.folders)
            }
        }
    }

    fun setSort(mode: SortMode) {
        _state.update { it.copy(sort = mode) } // instant; the prefs collector re-emits the same value
        viewModelScope.launch { UiPrefsStore.setSort(getApplication(), mode) }
    }

    fun setTab(tab: Int) {
        _state.update { it.copy(tab = tab) }
        viewModelScope.launch { UiPrefsStore.setLibraryTab(getApplication(), tab) }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { FavoritesStore.toggle(getApplication(), songId) }
    }
}
