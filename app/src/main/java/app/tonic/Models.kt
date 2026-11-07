package app.tonic

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumb: String
)

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track>
)
