package com.saucyfy.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** One playable YouTube track. */
data class Track(val videoId: String, val title: String, val artist: String) {
    val thumb: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}

/** Pulls the 11-character video id out of a YouTube / YouTube Music / youtu.be / shorts link. */
fun extractVideoId(s: String): String? {
    val t = s.trim()
    if (!t.contains("youtu")) return null
    return Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([0-9A-Za-z_-]{11})").find(t)?.groupValues?.get(1)
}

fun unescapeXml(s: String): String = s
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

/** Keyless access to a YouTube channel: public RSS feed, link lookup (oEmbed), optional API-key search. */
class YtApi(private val http: Http) {
    private val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    /** Channel name + its latest uploads (YouTube's public feed returns the newest 15). */
    suspend fun feed(channelId: String): Pair<String, List<Track>> {
        val (code, xml) = http.call(
            Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId").build()
        )
        if (code !in 200..299) throw IOException("Couldn't load the channel's uploads (HTTP $code)")
        val name = Regex("<title>(.*?)</title>").find(xml)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: "Your channel"
        val tracks = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val b = m.groupValues[1]
            val id = Regex("<yt:videoId>([^<]+)</yt:videoId>").find(b)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("<title>(.*?)</title>").find(b)?.groupValues?.get(1)?.let { unescapeXml(it) }
                ?: return@mapNotNull null
            Track(id, title, name)
        }.toList()
        return name to tracks
    }

    /** Accepts a channel id (UC...), a /channel/UC... link, an @handle or a youtube.com/@handle link. */
    suspend fun resolve(input: String, key: String?): String {
        val s = input.trim()
        Regex("UC[0-9A-Za-z_-]{22}").find(s)?.let { return it.value }
        val handle = s.substringAfterLast("/").substringBefore("?").removePrefix("@").trim()
        if (handle.isEmpty()) throw IOException("Enter a channel link, @handle or channel id")
        val enc = URLEncoder.encode(handle, "UTF-8")
        if (!key.isNullOrBlank()) {
            val url = "https://www.googleapis.com/youtube/v3/channels?part=id&forHandle=%40$enc&key=$key"
            val (c, b) = http.call(Request.Builder().url(url).build())
            if (c in 200..299) {
                val items = JSON.parseToJsonElement(b).jsonObject["items"] as? JsonArray
                val id = (items?.firstOrNull() as? JsonObject)?.str("id")
                if (id != null) return id
            }
        }
        val (code, html) = http.call(
            Request.Builder().url("https://www.youtube.com/@$enc")
                .header("User-Agent", ua)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "CONSENT=YES+1; SOCS=CAI")
                .build()
        )
        if (code in 200..299) {
            Regex("(?:channel_id=|\"channelId\":\"|\"externalId\":\"|channel/)(UC[0-9A-Za-z_-]{22})")
                .find(html)?.let { return it.groupValues[1] }
        }
        throw IOException("Couldn't find that channel. Paste the full channel link (youtube.com/channel/UC...) instead.")
    }

    /** Title + channel of any embeddable video link. */
    suspend fun oembed(videoId: String): Track {
        val url = "https://www.youtube.com/oembed?format=json&url=" +
            URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
        val (code, body) = http.call(Request.Builder().url(url).build())
        if (code !in 200..299) throw IOException("Couldn't read that video (private or not embeddable)")
        val o = JSON.parseToJsonElement(body).jsonObject
        return Track(videoId, o.str("title") ?: "YouTube video", o.str("author_name") ?: "")
    }

    /** Full-channel search. Needs the optional YouTube Data API key. */
    suspend fun apiSearch(channelId: String, key: String, q: String): List<Track> {
        val url = HttpUrl.Builder().scheme("https").host("www.googleapis.com")
            .addPathSegments("youtube/v3/search")
            .addQueryParameter("part", "snippet")
            .addQueryParameter("channelId", channelId)
            .addQueryParameter("q", q)
            .addQueryParameter("type", "video")
            .addQueryParameter("maxResults", "25")
            .addQueryParameter("key", key)
            .build()
        val (code, body) = http.call(Request.Builder().url(url).build())
        if (code !in 200..299) throw IOException(if (code == 403) "API key rejected or quota used up" else "HTTP $code")
        val arr = JSON.parseToJsonElement(body).jsonObject["items"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonObject)?.str("videoId") ?: return@mapNotNull null
            val sn = o["snippet"] as? JsonObject ?: return@mapNotNull null
            Track(id, unescapeXml(sn.str("title") ?: return@mapNotNull null), sn.str("channelTitle") ?: "")
        }
    }
}
