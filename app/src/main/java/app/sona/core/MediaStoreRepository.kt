package app.sona.core

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Scans on-device audio via MediaStore and groups it into albums / artists / folders. */
object MediaStoreRepository {

    fun songs(context: Context): List<Song> {
        val out = ArrayList<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val dataI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idI)
                val songUri = ContentUris.withAppendedId(collection, id)
                val albumId = c.getLong(albumIdI)
                val dur = c.getLong(durI)
                if (dur in 1..4999) continue // skip very short clips / notification sounds
                out.add(
                    Song(
                        id = id,
                        title = c.getString(titleI) ?: "Unknown",
                        artist = c.getString(artistI)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                        album = c.getString(albumI) ?: "Unknown album",
                        albumId = albumId,
                        durationMs = dur,
                        uri = songUri,
                        // The SONG's own cover (.../audio/media/<id>/albumart), not the album's: untagged files
                        // (YouTube downloads) share one "Download" album per folder, and that album has no art —
                        // every one of them showed a blank monogram although the files carry embedded covers.
                        artworkUri = Uri.withAppendedPath(songUri, "albumart"),
                        track = c.getInt(trackI),
                        path = c.getString(dataI) ?: "",
                        dateAdded = c.getLong(addedI),
                    )
                )
            }
        }
        return out
    }

    fun albums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (albumId, list) ->
                val first = list.first()
                Album(albumId, first.album, first.artist, list.size, first.artworkUri)
            }
            .sortedBy { it.title.lowercase() }

    fun artists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (name, list) -> Artist(name, list.size, list.map { it.albumId }.distinct().size) }
            .sortedBy { it.name.lowercase() }

    fun folders(songs: List<Song>): List<Folder> =
        songs.filter { it.path.contains('/') }
            .groupBy { it.path.substringBeforeLast('/') }
            .map { (path, list) -> Folder(path, path.substringAfterLast('/'), list.size) }
            .sortedBy { it.name.lowercase() }
}
