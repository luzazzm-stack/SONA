package app.sona.core

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?,
    val track: Int,
    val path: String,
    val dateAdded: Long,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: Uri?,
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int,
)

enum class SonaRepeat { OFF, ALL, ONE }

fun Long.asDuration(): String {
    if (this <= 0) return "0:00"
    val totalSec = this / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
