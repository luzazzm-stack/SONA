package app.sona.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favDataStore by preferencesDataStore(name = "sona_favorites")
private val FAV_KEY = stringSetPreferencesKey("favorite_song_ids")

/** Simple favorites persistence: a set of song id strings. */
object FavoritesStore {
    fun flow(context: Context): Flow<Set<Long>> =
        context.favDataStore.data.map { prefs ->
            prefs[FAV_KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        }

    suspend fun toggle(context: Context, songId: Long) {
        context.favDataStore.edit { prefs ->
            val cur = prefs[FAV_KEY].orEmpty().toMutableSet()
            val key = songId.toString()
            if (!cur.add(key)) cur.remove(key)
            prefs[FAV_KEY] = cur
        }
    }
}
