package com.saucyfy.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

val JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

enum class MediaType(val key: String, val label: String) {
    MOVIE("movie", "Movie"),
    TV("tv", "Show"),
    ANIME("anime", "Anime"),
    MANGA("manga", "Manga"),
    MUSIC("music", "Music");

    companion object {
        fun from(k: String?): MediaType = entries.firstOrNull { it.key == k } ?: MOVIE
    }
}

val MediaType.plural: String
    get() = when (this) {
        MediaType.MOVIE -> "Movies"
        MediaType.TV -> "Shows"
        MediaType.ANIME -> "Anime"
        MediaType.MANGA -> "Manga"
        MediaType.MUSIC -> "Music"
    }

/** Movies and songs are "one tap" items: no season / episode / chapter to keep track of. */
val MediaType.isSingle: Boolean get() = this == MediaType.MOVIE || this == MediaType.MUSIC

enum class Status(val key: String, val label: String) {
    WATCHING("watching", "Watching"),
    WATCHED("watched", "Finished"),
    PLANNED("plan_to_watch", "To watch"),
    DROPPED("dropped", "Dropped");

    companion object {
        fun from(k: String?): Status = entries.firstOrNull { it.key == k } ?: PLANNED
    }
}

/** Status wording that fits the kind of media (reading a manga, listening to a song...). */
fun Status.labelFor(t: MediaType): String = when (t) {
    MediaType.MANGA -> when (this) {
        Status.WATCHING -> "Reading"
        Status.WATCHED -> "Finished"
        Status.PLANNED -> "To read"
        Status.DROPPED -> "Dropped"
    }
    MediaType.MUSIC -> when (this) {
        Status.WATCHING -> "Listening"
        Status.WATCHED -> "Listened"
        Status.PLANNED -> "To listen"
        Status.DROPPED -> "Dropped"
    }
    else -> label
}

/** Real season / episode / chapter limits for a title, so the counters can't go past what exists. */
data class Limits(
    val seasons: Map<Int, Int>? = null, // season number -> episode count (TV)
    val totalEpisodes: Int? = null // anime episodes or manga chapters
)

/** A search / discover result from a public metadata API. */
data class MediaResult(
    val externalId: String,
    val type: MediaType,
    val title: String,
    val year: String,
    val posterUrl: String?,
    val overview: String,
    val score: Double?,
    val episodes: Int? = null
)

/** Everything the detail sheet needs, whether it came from search or the library. */
data class Detail(
    val type: MediaType,
    val externalId: String,
    val title: String,
    val year: String,
    val posterUrl: String?,
    val overview: String,
    val score: Double?,
    val episodes: Int? = null
)

fun MediaResult.toDetail() = Detail(type, externalId, title, year, posterUrl, overview, score, episodes)

@Serializable
data class LibraryItem(
    val id: String? = null,
    @SerialName("media_type") val mediaType: String,
    @SerialName("external_id") val externalId: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    val year: String? = null,
    val overview: String? = null,
    val status: String = "plan_to_watch",
    val rating: Int? = null,
    val season: Int? = null, // TV: season, manga: volume
    val episode: Int? = null, // TV / anime: episode, manga: chapter
    val notes: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

val LibraryItem.type: MediaType get() = MediaType.from(mediaType)
val LibraryItem.statusEnum: Status get() = Status.from(status)

fun LibraryItem.toDetail() = Detail(type, externalId, title, year ?: "", posterUrl, overview ?: "", null, null)

fun LibraryItem.progressLabel(): String? {
    if (type.isSingle) return null
    val s = season
    val e = episode
    if (s == null && e == null) return null
    return buildString {
        if (type == MediaType.TV && s != null) append("S$s")
        if (type == MediaType.MANGA && s != null) append("Vol $s")
        if (e != null) {
            if (isNotEmpty()) append(" · ")
            append(if (type == MediaType.MANGA) "Ch $e" else "E$e")
        }
    }
}

@Serializable
data class LibraryUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("external_id") val externalId: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String?,
    val year: String?,
    val overview: String?,
    val status: String,
    val rating: Int?,
    val season: Int?,
    val episode: Int?,
    val notes: String?
)

fun LibraryItem.toUpsert(userId: String, episode: Int? = this.episode) = LibraryUpsert(
    userId = userId,
    mediaType = mediaType,
    externalId = externalId,
    title = title,
    posterUrl = posterUrl,
    year = year,
    overview = overview,
    status = status,
    rating = rating,
    season = season,
    episode = episode,
    notes = notes
)

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String = "",
    val name: String = "",
    val avatar: String? = null
)
