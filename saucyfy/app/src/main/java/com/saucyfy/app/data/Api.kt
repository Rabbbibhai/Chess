package com.saucyfy.app.data

import android.content.Context
import com.saucyfy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Cfg {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    val tmdbKey: String = BuildConfig.TMDB_API_KEY.trim()
    const val REDIRECT = "saucyfy://login-callback"
    val configured: Boolean get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()
}

class AuthExpired : Exception("Session expired. Please sign in again.")

class Http {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun call(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { r -> r.code to (r.body?.string() ?: "") }
    }
}

class SessionStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("saucyfy_session", Context.MODE_PRIVATE)

    fun get(): Session? = prefs.getString("s", null)?.let {
        runCatching { JSON.decodeFromString(Session.serializer(), it) }.getOrNull()
    }

    fun save(s: Session) {
        prefs.edit().putString("s", JSON.encodeToString(Session.serializer(), s)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

private val JSON_MT = "application/json".toMediaType()
private fun nowSec() = System.currentTimeMillis() / 1000

class SupabaseApi(private val http: Http, private val store: SessionStore) {
    private val base = Cfg.supabaseUrl

    fun loginUrl(): String =
        "$base/auth/v1/authorize?provider=google&redirect_to=" + URLEncoder.encode(Cfg.REDIRECT, "UTF-8")

    /** Called with the fragment (or query) of the saucyfy://login-callback deep link. */
    suspend fun sessionFromRedirect(raw: String): Session {
        val p = raw.split("&").filter { it.contains("=") }.associate {
            val i = it.indexOf('=')
            URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }
        p["error_description"]?.let { throw IOException(it) }
        val access = p["access_token"] ?: throw IOException("Sign-in did not return a session")
        val refresh = p["refresh_token"] ?: ""
        val expiresIn = p["expires_in"]?.toLongOrNull() ?: 3600L
        val user = fetchUser(access)
        val s = user.copy(accessToken = access, refreshToken = refresh, expiresAt = nowSec() + expiresIn)
        store.save(s)
        return s
    }

    private suspend fun fetchUser(token: String): Session {
        val (code, body) = http.call(
            Request.Builder().url("$base/auth/v1/user")
                .header("apikey", Cfg.anonKey)
                .header("Authorization", "Bearer $token")
                .build()
        )
        if (code !in 200..299) throw IOException("Could not load your profile ($code)")
        val o = JSON.parseToJsonElement(body).jsonObject
        val meta = o["user_metadata"] as? JsonObject
        val email = o.str("email") ?: ""
        val name = meta?.str("full_name") ?: meta?.str("name") ?: email.substringBefore("@")
        val avatar = meta?.str("avatar_url") ?: meta?.str("picture")
        val id = o.str("id") ?: throw IOException("No user id returned")
        return Session("", "", 0L, id, email, name, avatar)
    }

    private suspend fun refresh(s: Session): Session {
        if (s.refreshToken.isBlank()) {
            store.clear(); throw AuthExpired()
        }
        val payload = """{"refresh_token":"${s.refreshToken}"}"""
        val (code, resp) = http.call(
            Request.Builder().url("$base/auth/v1/token?grant_type=refresh_token")
                .header("apikey", Cfg.anonKey)
                .post(payload.toRequestBody(JSON_MT))
                .build()
        )
        if (code !in 200..299) {
            if (code == 400 || code == 401 || code == 403) {
                store.clear(); throw AuthExpired()
            }
            throw IOException("Could not refresh your session ($code)")
        }
        val o = JSON.parseToJsonElement(resp).jsonObject
        val ns = s.copy(
            accessToken = o.str("access_token") ?: throw AuthExpired(),
            refreshToken = o.str("refresh_token") ?: s.refreshToken,
            expiresAt = nowSec() + (o.str("expires_in")?.toLongOrNull() ?: 3600L)
        )
        store.save(ns)
        return ns
    }

    private suspend fun authed(build: (String) -> Request): Pair<Int, String> {
        var s = store.get() ?: throw AuthExpired()
        if (s.expiresAt - 60 < nowSec()) s = refresh(s)
        var r = http.call(build(s.accessToken))
        if (r.first == 401) {
            s = refresh(s)
            r = http.call(build(s.accessToken))
        }
        return r
    }

    suspend fun loadLibrary(): List<LibraryItem> {
        val (code, body) = authed { t ->
            Request.Builder()
                .url("$base/rest/v1/library_items?select=*&order=updated_at.desc")
                .header("apikey", Cfg.anonKey)
                .header("Authorization", "Bearer $t")
                .build()
        }
        if (code !in 200..299) throw IOException("Could not load library ($code). Did you run schema.sql?")
        return JSON.decodeFromString(ListSerializer(LibraryItem.serializer()), body)
    }

    suspend fun upsert(item: LibraryUpsert): LibraryItem {
        val json = JSON.encodeToString(LibraryUpsert.serializer(), item)
        val (code, body) = authed { t ->
            Request.Builder()
                .url("$base/rest/v1/library_items?on_conflict=user_id,media_type,external_id")
                .header("apikey", Cfg.anonKey)
                .header("Authorization", "Bearer $t")
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(json.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException("Could not save ($code)")
        return JSON.decodeFromString(ListSerializer(LibraryItem.serializer()), body).first()
    }

    suspend fun delete(id: String) {
        val (code, _) = authed { t ->
            Request.Builder()
                .url("$base/rest/v1/library_items?id=eq.$id")
                .header("apikey", Cfg.anonKey)
                .header("Authorization", "Bearer $t")
                .delete()
                .build()
        }
        if (code !in 200..299) throw IOException("Could not remove ($code)")
    }
}

class MetadataApi(private val http: Http) {
    val hasTmdb: Boolean get() = Cfg.tmdbKey.isNotBlank()

    suspend fun searchTmdb(q: String, only: MediaType?): List<MediaResult> = when (only) {
        MediaType.MOVIE -> tmdb("search/movie", mapOf("query" to q, "include_adult" to "false"), MediaType.MOVIE)
        MediaType.TV -> tmdb("search/tv", mapOf("query" to q, "include_adult" to "false"), MediaType.TV)
        else -> tmdb("search/multi", mapOf("query" to q, "include_adult" to "false"), null)
    }

    suspend fun trending(): List<MediaResult> = tmdb("trending/all/week", emptyMap(), null)

    suspend fun searchAnime(q: String): List<MediaResult> =
        jikan("anime", mapOf("q" to q, "limit" to "20", "sfw" to "true"))

    suspend fun topAnime(): List<MediaResult> =
        jikan("top/anime", mapOf("filter" to "airing", "limit" to "20", "sfw" to "true"))

    private suspend fun tmdb(path: String, params: Map<String, String>, forced: MediaType?): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("api.themoviedb.org")
            .addPathSegments("3/$path")
            .addQueryParameter("api_key", Cfg.tmdbKey)
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val (code, body) = http.call(Request.Builder().url(url).build())
        if (code !in 200..299) throw IOException("TMDB error $code")
        val arr = JSON.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val mt = forced?.key ?: o.str("media_type")
            val type = when (mt) {
                "movie" -> MediaType.MOVIE
                "tv" -> MediaType.TV
                else -> return@mapNotNull null
            }
            val id = o.str("id") ?: return@mapNotNull null
            val title = o.str("title") ?: o.str("name") ?: return@mapNotNull null
            val date = o.str("release_date") ?: o.str("first_air_date") ?: ""
            MediaResult(
                externalId = id,
                type = type,
                title = title,
                year = date.take(4),
                posterUrl = o.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                overview = o.str("overview") ?: "",
                score = o.str("vote_average")?.toDoubleOrNull()
            )
        }
    }

    private suspend fun jikan(path: String, params: Map<String, String>): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("api.jikan.moe")
            .addPathSegments("v4/$path")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        var res = http.call(Request.Builder().url(url).build())
        if (res.first == 429) {
            delay(1200)
            res = http.call(Request.Builder().url(url).build())
        }
        if (res.first !in 200..299) throw IOException("Anime search failed (${res.first})")
        val arr = JSON.parseToJsonElement(res.second).jsonObject["data"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("mal_id") ?: return@mapNotNull null
            val title = o.str("title_english") ?: o.str("title") ?: return@mapNotNull null
            val jpg = (o["images"] as? JsonObject)?.get("jpg") as? JsonObject
            val year = o.str("year") ?: (o["aired"] as? JsonObject)?.str("from")?.take(4) ?: ""
            MediaResult(
                externalId = id,
                type = MediaType.ANIME,
                title = title,
                year = year,
                posterUrl = jpg?.str("large_image_url") ?: jpg?.str("image_url"),
                overview = o.str("synopsis") ?: "",
                score = o.str("score")?.toDoubleOrNull()
            )
        }
    }
}
