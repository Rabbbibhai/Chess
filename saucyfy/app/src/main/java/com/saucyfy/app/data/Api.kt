package com.saucyfy.app.data

import android.content.Context
import com.saucyfy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Cfg {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    const val REDIRECT = "saucyfy://login-callback"
    val configured: Boolean get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()
}

class AuthExpired : Exception("Session expired. Please sign in again.")

/** Spaces calls at least [minIntervalMs] apart (shared by everything using the same instance). */
class RateLimiter(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var next = 0L

    suspend fun acquire() = mutex.withLock {
        val now = System.currentTimeMillis()
        val wait = next - now
        if (wait > 0) delay(wait)
        next = maxOf(now, next) + minIntervalMs
    }
}

/** HTTP client with a 20 MB disk cache; public metadata GETs are cached for 15 minutes. */
class Http(cacheDir: File? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .apply {
            if (cacheDir != null) {
                cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
                addNetworkInterceptor { chain ->
                    val req = chain.request()
                    val res = chain.proceed(req)
                    if (req.method == "GET" && !req.url.host.endsWith("supabase.co")) {
                        res.newBuilder()
                            .removeHeader("Pragma")
                            .header("Cache-Control", "public, max-age=900")
                            .build()
                    } else {
                        res
                    }
                }
            }
        }
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

/** Keeps the last-seen library on disk so the app opens instantly, then refreshes in the background. */
class LibraryCache(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("saucyfy_cache", Context.MODE_PRIVATE)
    private val ser = ListSerializer(LibraryItem.serializer())

    fun get(userId: String): List<LibraryItem>? = prefs.getString("lib_$userId", null)?.let {
        runCatching { JSON.decodeFromString(ser, it) }.getOrNull()
    }

    fun put(userId: String, items: List<LibraryItem>) {
        prefs.edit().putString("lib_$userId", JSON.encodeToString(ser, items)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

private val JSON_MT = "application/json".toMediaType()
private fun nowSec() = System.currentTimeMillis() / 1000

private fun errMsg(code: Int, body: String): String {
    val o = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull()
    val raw = o?.str("msg") ?: o?.str("error_description") ?: o?.str("message") ?: ""
    return when {
        raw.contains("profiles_username", ignoreCase = true) -> "That username is already taken"
        raw.contains("friendships", ignoreCase = true) && raw.contains("duplicate", ignoreCase = true) ->
            "You already have a request with this person"
        raw.contains("rate limit", ignoreCase = true) -> "Rate limit reached. Please try again later."
        raw.isNotBlank() -> raw.replaceFirstChar { it.uppercase() }
        code == 429 -> "Too many requests. Please wait a bit and try again."
        else -> "Request failed ($code)"
    }
}

class SupabaseApi(private val http: Http, private val store: SessionStore) {
    private val base = Cfg.supabaseUrl
    private val writeLimiter = RateLimiter(350)
    private val profileCols = "id,username,display_name,avatar_url"

    private fun jsonPost(url: String, json: String): Request =
        Request.Builder().url(url)
            .header("apikey", Cfg.anonKey)
            .post(json.toRequestBody(JSON_MT))
            .build()

    private fun rest(path: String, token: String): Request.Builder =
        Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", Cfg.anonKey)
            .header("Authorization", "Bearer $token")

    private fun credentials(email: String, password: String): String =
        buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()

    private fun toSession(o: JsonObject): Session? {
        val access = o.str("access_token") ?: return null
        val user = o["user"] as? JsonObject ?: return null
        val id = user.str("id") ?: return null
        val meta = user["user_metadata"] as? JsonObject
        val email = user.str("email") ?: ""
        return Session(
            accessToken = access,
            refreshToken = o.str("refresh_token") ?: "",
            expiresAt = nowSec() + (o.str("expires_in")?.toLongOrNull() ?: 3600L),
            userId = id,
            email = email,
            name = meta?.str("full_name") ?: meta?.str("name") ?: email.substringBefore("@"),
            avatar = meta?.str("avatar_url")
        )
    }

    suspend fun signIn(email: String, password: String): Session {
        val (code, body) = http.call(
            jsonPost("$base/auth/v1/token?grant_type=password", credentials(email, password))
        )
        if (code !in 200..299) {
            throw IOException(if (code == 400) "Invalid email or password" else errMsg(code, body))
        }
        val s = toSession(JSON.parseToJsonElement(body).jsonObject) ?: throw IOException("Sign-in failed")
        store.save(s)
        return s
    }

    /** Returns a session, or null when the project requires email confirmation first. */
    suspend fun signUp(email: String, password: String): Session? {
        val redirect = URLEncoder.encode(Cfg.REDIRECT, "UTF-8")
        val (code, body) = http.call(
            jsonPost("$base/auth/v1/signup?redirect_to=$redirect", credentials(email, password))
        )
        if (code !in 200..299) throw IOException(errMsg(code, body))
        val s = toSession(JSON.parseToJsonElement(body).jsonObject)
        if (s != null) store.save(s)
        return s
    }

    /** Called with the fragment of a saucyfy://login-callback link (e.g. email confirmation). */
    suspend fun sessionFromRedirect(raw: String): Session {
        val p = raw.split("&").filter { it.contains("=") }.associate {
            val i = it.indexOf('=')
            URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }
        p["error_description"]?.let { throw IOException(it) }
        val access = p["access_token"] ?: throw IOException("Link did not contain a session")
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
        val payload = buildJsonObject { put("refresh_token", s.refreshToken) }.toString()
        val (code, resp) = http.call(jsonPost("$base/auth/v1/token?grant_type=refresh_token", payload))
        if (code !in 200..299) {
            if (code == 400 || code == 401 || code == 403) {
                store.clear(); throw AuthExpired()
            }
            throw IOException(errMsg(code, resp))
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

    private fun myId(): String = (store.get() ?: throw AuthExpired()).userId

    // ---- profile ----
    suspend fun updateProfile(name: String?, avatarUrl: String?): Session {
        val data = buildJsonObject {
            name?.let { put("full_name", it) }
            avatarUrl?.let { put("avatar_url", it) }
        }
        val payload = buildJsonObject { put("data", data) }.toString()
        val (code, body) = authed { t ->
            Request.Builder().url("$base/auth/v1/user")
                .header("apikey", Cfg.anonKey)
                .header("Authorization", "Bearer $t")
                .put(payload.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        val s = store.get() ?: throw AuthExpired()
        val meta = JSON.parseToJsonElement(body).jsonObject["user_metadata"] as? JsonObject
        val ns = s.copy(
            name = meta?.str("full_name") ?: s.name,
            avatar = meta?.str("avatar_url") ?: s.avatar
        )
        store.save(ns)
        return ns
    }

    /** Keeps the public profile row (what friends see) in sync. */
    suspend fun patchProfile(displayName: String? = null, username: String? = null, avatarUrl: String? = null) {
        val me = myId()
        val payload = buildJsonObject {
            displayName?.let { put("display_name", it) }
            username?.let { put("username", it) }
            avatarUrl?.let { put("avatar_url", it) }
        }.toString()
        val (code, body) = authed { t ->
            rest("profiles?id=eq.$me", t)
                .header("Prefer", "return=minimal")
                .patch(payload.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
    }

    suspend fun myProfile(): Profile? {
        val me = myId()
        val (code, body) = authed { t -> rest("profiles?select=$profileCols&id=eq.$me", t).build() }
        if (code !in 200..299) return null
        return JSON.decodeFromString(ListSerializer(Profile.serializer()), body).firstOrNull()
    }

    /** Uploads a JPEG (already compressed to < 1 MB) and returns its public URL. */
    suspend fun uploadAvatar(bytes: ByteArray): String {
        val uid = myId()
        val (code, body) = authed { t ->
            Request.Builder().url("$base/storage/v1/object/avatars/$uid/avatar.jpg")
                .header("apikey", Cfg.anonKey)
                .header("Authorization", "Bearer $t")
                .header("x-upsert", "true")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        return "$base/storage/v1/object/public/avatars/$uid/avatar.jpg?v=${System.currentTimeMillis()}"
    }

    // ---- library ----
    suspend fun loadLibrary(userId: String): List<LibraryItem> {
        val (code, body) = authed { t ->
            rest("library_items?select=*&user_id=eq.$userId&order=updated_at.desc", t).build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        return JSON.decodeFromString(ListSerializer(LibraryItem.serializer()), body)
    }

    /** A friend's library (allowed by row level security only for accepted friends). */
    suspend fun libraryOf(userId: String): List<LibraryItem> = loadLibrary(userId)

    suspend fun upsert(item: LibraryUpsert): LibraryItem {
        writeLimiter.acquire()
        val json = JSON.encodeToString(LibraryUpsert.serializer(), item)
        val (code, body) = authed { t ->
            rest("library_items?on_conflict=user_id,media_type,external_id", t)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(json.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        return JSON.decodeFromString(ListSerializer(LibraryItem.serializer()), body).first()
    }

    suspend fun delete(id: String) {
        writeLimiter.acquire()
        val (code, body) = authed { t -> rest("library_items?id=eq.$id", t).delete().build() }
        if (code !in 200..299) throw IOException(errMsg(code, body))
    }

    // ---- friends ----
    private suspend fun profilesByIds(ids: List<String>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        val (code, body) = authed { t ->
            rest("profiles?select=$profileCols&id=in.(${ids.joinToString(",")})", t).build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        return JSON.decodeFromString(ListSerializer(Profile.serializer()), body)
    }

    suspend fun loadFriends(): List<FriendEntry> {
        val me = myId()
        val (code, body) = authed { t -> rest("friendships?select=*&order=created_at.desc", t).build() }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        val rows = JSON.decodeFromString(ListSerializer(Friendship.serializer()), body)
        if (rows.isEmpty()) return emptyList()
        val otherId = { f: Friendship -> if (f.requester == me) f.addressee else f.requester }
        val profiles = profilesByIds(rows.map(otherId).distinct()).associateBy { it.id }
        return rows.mapNotNull { f ->
            val p = profiles[otherId(f)] ?: return@mapNotNull null
            FriendEntry(f.id, p, f.status == "accepted", incoming = f.addressee == me)
        }
    }

    suspend fun searchUsers(q: String): List<Profile> {
        val me = myId()
        val s = q.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        if (s.length < 2) return emptyList()
        val (code, body) = authed { t ->
            rest(
                "profiles?select=$profileCols&or=(username.ilike.$s*,display_name.ilike.*$s*)&id=neq.$me&limit=20",
                t
            ).build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        return JSON.decodeFromString(ListSerializer(Profile.serializer()), body)
    }

    suspend fun sendRequest(toId: String) {
        val payload = buildJsonObject { put("addressee", toId) }.toString()
        val (code, body) = authed { t ->
            rest("friendships", t)
                .header("Prefer", "return=minimal")
                .post(payload.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
    }

    suspend fun acceptRequest(friendshipId: String) {
        val payload = buildJsonObject { put("status", "accepted") }.toString()
        val (code, body) = authed { t ->
            rest("friendships?id=eq.$friendshipId", t)
                .header("Prefer", "return=minimal")
                .patch(payload.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
    }

    suspend fun removeFriendship(friendshipId: String) {
        val (code, body) = authed { t -> rest("friendships?id=eq.$friendshipId", t).delete().build() }
        if (code !in 200..299) throw IOException(errMsg(code, body))
    }

    // ---- messaging (server only holds a message until it is downloaded) ----
    suspend fun sendMessage(toId: String, body: String) {
        val payload = buildJsonObject {
            put("recipient", toId)
            put("body", body)
        }.toString()
        val (code, resp) = authed { t ->
            rest("mailbox", t)
                .header("Prefer", "return=minimal")
                .post(payload.toRequestBody(JSON_MT))
                .build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, resp))
    }

    suspend fun fetchMailbox(): List<MailMsg> {
        val (code, body) = authed { t ->
            rest("mailbox?select=id,sender,body,created_at&order=created_at.asc&limit=100", t).build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
        return JSON.decodeFromString(ListSerializer(MailMsg.serializer()), body)
    }

    suspend fun deleteMailbox(ids: List<String>) {
        if (ids.isEmpty()) return
        val (code, body) = authed { t ->
            rest("mailbox?id=in.(${ids.joinToString(",")})", t).delete().build()
        }
        if (code !in 200..299) throw IOException(errMsg(code, body))
    }
}

/**
 * Public, key-free metadata with per-host rate limiting, a result cache and fallbacks:
 *  - Movies: Cinemeta (IMDb)   -> fallback Apple iTunes
 *  - Shows:  TVMaze
 *  - Anime:  Jikan (MyAnimeList) -> fallback Kitsu
 *  - Spelling suggestions: Wikipedia search
 *  - Reaction GIFs: nekos.best
 */
class MetadataApi(private val http: Http) {
    private val sizeRegex = Regex("\\d+x\\d+bb")
    private val tagRegex = Regex("<[^>]*>")
    private val kitsuAccept = "application/vnd.api+json"

    private val itunesLimit = RateLimiter(1200)
    private val cinemetaLimit = RateLimiter(300)
    private val tvmazeLimit = RateLimiter(400)
    private val jikanLimit = RateLimiter(450)
    private val kitsuLimit = RateLimiter(300)
    private val wikiLimit = RateLimiter(400)
    private val gifLimit = RateLimiter(300)
    private val cache = ConcurrentHashMap<String, List<MediaResult>>()
    private val gifCache = ConcurrentHashMap<String, List<String>>()

    private suspend fun cached(key: String, load: suspend () -> List<MediaResult>): List<MediaResult> {
        cache[key]?.let { return it }
        val v = load()
        if (v.isNotEmpty()) {
            if (cache.size > 80) cache.clear()
            cache[key] = v
        }
        return v
    }

    private suspend fun getJson(url: HttpUrl, label: String, limiter: RateLimiter, accept: String? = null): String {
        limiter.acquire()
        val req = Request.Builder().url(url).apply { if (accept != null) header("Accept", accept) }.build()
        val (code, body) = http.call(req)
        if (code == 429) throw IOException("$label is busy (429)")
        if (code !in 200..299) throw IOException("$label HTTP $code")
        return body
    }

    /** Picks the first non-empty result; only fails when every source failed. */
    private suspend fun firstNonEmpty(label: String, vararg sources: suspend () -> List<MediaResult>): List<MediaResult> {
        var firstError: Throwable? = null
        var failures = 0
        for (src in sources) {
            val r = runCatching { src() }
            val v = r.getOrNull()
            if (!v.isNullOrEmpty()) return v
            if (r.isFailure) {
                failures++
                if (firstError == null) firstError = r.exceptionOrNull()
            }
        }
        if (failures == sources.size) throw (firstError ?: IOException("$label unavailable"))
        return emptyList()
    }

    suspend fun searchMovies(q: String): List<MediaResult> = cached("m|${q.lowercase()}") {
        firstNonEmpty("Movies", { cinemetaSearch(q) }, { itunesMovies(q) })
    }

    suspend fun searchShows(q: String): List<MediaResult> = cached("s|${q.lowercase()}") { tvmazeSearch(q) }

    suspend fun searchAnime(q: String): List<MediaResult> = cached("a|${q.lowercase()}") {
        firstNonEmpty(
            "Anime",
            { jikan("anime", mapOf("q" to q, "limit" to "20", "sfw" to "true")) },
            { kitsuSearch(q) }
        )
    }

    /** Discover row: popular movies + shows airing today. */
    suspend fun trending(): List<MediaResult> = coroutineScope {
        val m = async {
            runCatching { cinemetaTop() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { itunesTopMovies() }.getOrDefault(emptyList())
        }
        val s = async { runCatching { tvmazeToday() }.getOrDefault(emptyList()) }
        m.await() + s.await()
    }

    suspend fun topAnime(): List<MediaResult> {
        val a = runCatching { jikan("top/anime", mapOf("filter" to "airing", "limit" to "20", "sfw" to "true")) }.getOrNull()
        if (!a.isNullOrEmpty()) return a
        return runCatching { kitsuTop() }.getOrDefault(emptyList())
    }

    /** "Did you mean ...?" from Wikipedia's spelling correction. */
    suspend fun suggest(q: String): String? {
        val url = HttpUrl.Builder().scheme("https").host("en.wikipedia.org")
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", q)
            .addQueryParameter("srlimit", "1")
            .addQueryParameter("srinfo", "suggestion")
            .addQueryParameter("srprop", "")
            .addQueryParameter("format", "json")
            .build()
        wikiLimit.acquire()
        val (code, body) = http.call(
            Request.Builder().url(url).header("User-Agent", "Elog/1.0 (Android app; spelling suggestions)").build()
        )
        if (code !in 200..299) return null
        val info = (JSON.parseToJsonElement(body).jsonObject["query"] as? JsonObject)?.get("searchinfo") as? JsonObject
        return info?.str("suggestion")?.takeIf { it.isNotBlank() && !it.equals(q, ignoreCase = true) }
    }

    /** Reaction GIF urls (nekos.best, no key). */
    suspend fun gifs(category: String): List<String> {
        gifCache[category]?.let { return it }
        val url = HttpUrl.Builder().scheme("https").host("nekos.best")
            .addPathSegments("api/v2/$category")
            .addQueryParameter("amount", "12")
            .build()
        val arr = JSON.parseToJsonElement(getJson(url, "GIFs", gifLimit)).jsonObject["results"] as? JsonArray
            ?: return emptyList()
        val out = arr.mapNotNull { (it as? JsonObject)?.str("url") }.filter { it.startsWith("https://nekos.best/") }
        if (out.isNotEmpty()) gifCache[category] = out
        return out
    }

    // ---- real season / episode limits ----
    /** season number -> number of episodes, from TVMaze. */
    suspend fun tvmazeSeasons(showId: String): Map<Int, Int> {
        val url = HttpUrl.Builder().scheme("https").host("api.tvmaze.com")
            .addPathSegments("shows/$showId/episodes")
            .build()
        val arr = JSON.parseToJsonElement(getJson(url, "Episodes", tvmazeLimit)) as? JsonArray ?: return emptyMap()
        val counts = mutableMapOf<Int, Int>()
        arr.forEach { el ->
            val s = (el as? JsonObject)?.str("season")?.toIntOrNull() ?: return@forEach
            counts[s] = (counts[s] ?: 0) + 1
        }
        return counts
    }

    /** Total episodes of an anime (null while it is still airing / unknown). */
    suspend fun animeEpisodes(id: String): Int? {
        if (id.startsWith("k")) {
            val url = HttpUrl.Builder().scheme("https").host("kitsu.io")
                .addPathSegments("api/edge/anime/${id.drop(1)}")
                .build()
            val data = JSON.parseToJsonElement(getJson(url, "Anime", kitsuLimit, kitsuAccept)).jsonObject["data"] as? JsonObject
            return (data?.get("attributes") as? JsonObject)?.str("episodeCount")?.toIntOrNull()
        }
        val url = HttpUrl.Builder().scheme("https").host("api.jikan.moe")
            .addPathSegments("v4/anime/$id")
            .build()
        val data = JSON.parseToJsonElement(getJson(url, "Anime", jikanLimit)).jsonObject["data"] as? JsonObject
        return data?.str("episodes")?.toIntOrNull()
    }

    // ---- Cinemeta (movies, IMDb ids) ----
    private fun cinemetaMetas(body: String): List<MediaResult> {
        val arr = JSON.parseToJsonElement(body).jsonObject["metas"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("imdb_id") ?: o.str("id") ?: return@mapNotNull null
            val title = o.str("name") ?: return@mapNotNull null
            MediaResult(
                externalId = id,
                type = MediaType.MOVIE,
                title = title,
                year = (o.str("releaseInfo") ?: o.str("year") ?: "").take(4),
                posterUrl = o.str("poster"),
                overview = o.str("description") ?: "",
                score = o.str("imdbRating")?.toDoubleOrNull()
            )
        }
    }

    private suspend fun cinemetaSearch(q: String): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("v3-cinemeta.strem.io")
            .addPathSegments("catalog/movie/top")
            .addPathSegment("search=$q.json")
            .build()
        return cinemetaMetas(getJson(url, "Movie search", cinemetaLimit)).take(24)
    }

    private suspend fun cinemetaTop(): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("v3-cinemeta.strem.io")
            .addPathSegments("catalog/movie/top.json")
            .build()
        return cinemetaMetas(getJson(url, "Popular movies", cinemetaLimit)).take(20)
    }

    // ---- iTunes (movies fallback) ----
    private suspend fun itunesMovies(q: String): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com").addPathSegment("search")
            .addQueryParameter("term", q)
            .addQueryParameter("media", "movie")
            .addQueryParameter("entity", "movie")
            .addQueryParameter("limit", "20")
            .build()
        val arr = JSON.parseToJsonElement(getJson(url, "Movie search", itunesLimit)).jsonObject["results"] as? JsonArray
            ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("trackId") ?: return@mapNotNull null
            val title = o.str("trackName") ?: return@mapNotNull null
            MediaResult(
                externalId = id,
                type = MediaType.MOVIE,
                title = title,
                year = (o.str("releaseDate") ?: "").take(4),
                posterUrl = o.str("artworkUrl100")?.replace(sizeRegex, "400x600bb"),
                overview = o.str("longDescription") ?: o.str("shortDescription") ?: "",
                score = null
            )
        }
    }

    private suspend fun itunesTopMovies(): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com")
            .addPathSegments("us/rss/topmovies/limit=20/json")
            .build()
        val feed = JSON.parseToJsonElement(getJson(url, "Top movies", itunesLimit)).jsonObject["feed"] as? JsonObject
            ?: return emptyList()
        val arr = feed["entry"] as? JsonArray ?: return emptyList()
        fun lbl(o: JsonObject, k: String): String? = (o[k] as? JsonObject)?.str("label")
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = ((o["id"] as? JsonObject)?.get("attributes") as? JsonObject)?.str("im:id")
                ?: return@mapNotNull null
            val title = lbl(o, "im:name") ?: return@mapNotNull null
            val img = ((o["im:image"] as? JsonArray)?.lastOrNull() as? JsonObject)?.str("label")
            MediaResult(
                externalId = id,
                type = MediaType.MOVIE,
                title = title,
                year = (lbl(o, "im:releaseDate") ?: "").take(4),
                posterUrl = img?.replace(sizeRegex, "400x600bb"),
                overview = lbl(o, "summary") ?: "",
                score = null
            )
        }
    }

    // ---- TVMaze (shows) ----
    private fun tvmazeShow(show: JsonObject): MediaResult? {
        val id = show.str("id") ?: return null
        val title = show.str("name") ?: return null
        val img = show["image"] as? JsonObject
        return MediaResult(
            externalId = id,
            type = MediaType.TV,
            title = title,
            year = (show.str("premiered") ?: "").take(4),
            posterUrl = (img?.str("medium") ?: img?.str("original"))?.replace("http://", "https://"),
            overview = (show.str("summary") ?: "").replace(tagRegex, "").trim(),
            score = (show["rating"] as? JsonObject)?.str("average")?.toDoubleOrNull()
        )
    }

    private suspend fun tvmazeSearch(q: String): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("api.tvmaze.com")
            .addPathSegments("search/shows")
            .addQueryParameter("q", q)
            .build()
        val arr = JSON.parseToJsonElement(getJson(url, "Show search", tvmazeLimit)) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val show = (el as? JsonObject)?.get("show") as? JsonObject ?: return@mapNotNull null
            tvmazeShow(show)
        }
    }

    private suspend fun tvmazeToday(): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("api.tvmaze.com")
            .addPathSegments("schedule/web")
            .build()
        val arr = JSON.parseToJsonElement(getJson(url, "Schedule", tvmazeLimit)) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val embedded = (el as? JsonObject)?.get("_embedded") as? JsonObject ?: return@mapNotNull null
            val show = embedded["show"] as? JsonObject ?: return@mapNotNull null
            tvmazeShow(show)
        }.filter { it.posterUrl != null }.distinctBy { it.externalId }.take(20)
    }

    // ---- Jikan (anime) ----
    private suspend fun jikan(path: String, params: Map<String, String>): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("api.jikan.moe")
            .addPathSegments("v4/$path")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        jikanLimit.acquire()
        var res = http.call(Request.Builder().url(url).build())
        if (res.first == 429) {
            delay(1200)
            jikanLimit.acquire()
            res = http.call(Request.Builder().url(url).build())
        }
        if (res.first !in 200..299) throw IOException("Anime HTTP ${res.first}")
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
                score = o.str("score")?.toDoubleOrNull(),
                episodes = o.str("episodes")?.toIntOrNull()
            )
        }
    }

    // ---- Kitsu (anime fallback, ids are prefixed with "k") ----
    private fun kitsuItems(body: String): List<MediaResult> {
        val arr = JSON.parseToJsonElement(body).jsonObject["data"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val a = o["attributes"] as? JsonObject ?: return@mapNotNull null
            val title = (a["titles"] as? JsonObject)?.str("en") ?: a.str("canonicalTitle") ?: return@mapNotNull null
            val poster = a["posterImage"] as? JsonObject
            MediaResult(
                externalId = "k$id",
                type = MediaType.ANIME,
                title = title,
                year = (a.str("startDate") ?: "").take(4),
                posterUrl = poster?.str("medium") ?: poster?.str("small"),
                overview = a.str("synopsis") ?: "",
                score = a.str("averageRating")?.toDoubleOrNull()?.div(10),
                episodes = a.str("episodeCount")?.toIntOrNull()
            )
        }
    }

    private suspend fun kitsuSearch(q: String): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("kitsu.io")
            .addPathSegments("api/edge/anime")
            .addQueryParameter("filter[text]", q)
            .addQueryParameter("page[limit]", "20")
            .build()
        return kitsuItems(getJson(url, "Anime search", kitsuLimit, kitsuAccept))
    }

    private suspend fun kitsuTop(): List<MediaResult> {
        val url = HttpUrl.Builder().scheme("https").host("kitsu.io")
            .addPathSegments("api/edge/anime")
            .addQueryParameter("filter[status]", "current")
            .addQueryParameter("sort", "-userCount")
            .addQueryParameter("page[limit]", "20")
            .build()
        return kitsuItems(getJson(url, "Top anime", kitsuLimit, kitsuAccept))
    }
}
