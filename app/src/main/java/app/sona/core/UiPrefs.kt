package app.sona.core

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPrefsDataStore by preferencesDataStore(name = "sona_ui_prefs")
private val SORT_FIELD_KEY = stringPreferencesKey("sort_field")
private val SORT_DESC_KEY = booleanPreferencesKey("sort_descending")
private val LIBRARY_TAB_KEY = intPreferencesKey("library_tab")

/** Library sort order: a field plus its fixed direction (Recent is newest-first). */
enum class SortMode(val label: String, val descending: Boolean = false) {
    Title("Title"), Artist("Artist"), Album("Album"), Recent("Recently added", descending = true), Duration("Duration")
}

@Immutable
data class UiPrefs(val sort: SortMode = SortMode.Title, val libraryTab: Int = 0)

/** Simple UI prefs persistence: library sort (field + direction) and selected library tab. */
object UiPrefsStore {
    fun flow(context: Context): Flow<UiPrefs> =
        context.uiPrefsDataStore.data.map { prefs ->
            UiPrefs(
                sort = prefs[SORT_FIELD_KEY]?.let { name -> SortMode.entries.firstOrNull { it.name == name } } ?: SortMode.Title,
                libraryTab = (prefs[LIBRARY_TAB_KEY] ?: 0).coerceIn(0, 4),
            )
        }

    suspend fun setSort(context: Context, mode: SortMode) {
        context.uiPrefsDataStore.edit { prefs ->
            prefs[SORT_FIELD_KEY] = mode.name
            prefs[SORT_DESC_KEY] = mode.descending
        }
    }

    suspend fun setLibraryTab(context: Context, tab: Int) {
        context.uiPrefsDataStore.edit { prefs -> prefs[LIBRARY_TAB_KEY] = tab }
    }
}
