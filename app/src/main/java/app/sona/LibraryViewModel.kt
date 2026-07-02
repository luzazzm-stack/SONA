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
) {
    val favoriteSongs: List<Song> get() = songs.filter { it.id in favorites }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    init {
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
            val songs = withContext(Dispatchers.IO) {
                runCatching { MediaStoreRepository.songs(getApplication()) }.getOrDefault(emptyList())
            }
            val albums = MediaStoreRepository.albums(songs)
            val artists = MediaStoreRepository.artists(songs)
            val folders = MediaStoreRepository.folders(songs)
            _state.update {
                it.copy(loaded = true, loading = false, songs = songs, albums = albums, artists = artists, folders = folders)
            }
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { FavoritesStore.toggle(getApplication(), songId) }
    }
}
