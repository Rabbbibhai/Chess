package com.saucyfy.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saucyfy.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

data class UiState(
    val booting: Boolean = true,
    val themeId: String = "midnight",
    val session: Session? = null,
    val myUsername: String? = null,
    val authBusy: Boolean = false,
    val profileBusy: Boolean = false,
    val library: List<LibraryItem> = emptyList(),
    val libLoading: Boolean = false,
    val query: String = "",
    val filter: MediaType? = null,
    val results: List<MediaResult> = emptyList(),
    val searching: Boolean = false,
    val suggestion: String? = null,
    val corrected: String? = null,
    val trending: List<MediaResult> = emptyList(),
    val topAnime: List<MediaResult> = emptyList(),
    val limits: Map<String, Limits> = emptyMap(),
    val friends: List<FriendEntry> = emptyList(),
    val friendsLoading: Boolean = false,
    val userQuery: String = "",
    val userResults: List<Profile> = emptyList(),
    val userSearching: Boolean = false,
    val viewingFriend: Profile? = null,
    val friendItems: List<LibraryItem> = emptyList(),
    val friendItemsLoading: Boolean = false,
    val chats: Map<String, List<ChatMsg>> = emptyMap(),
    val unread: Map<String, Int> = emptyMap(),
    val chatWith: Profile? = null,
    val gifs: List<String> = emptyList(),
    val gifsLoading: Boolean = false,
    val gifCategory: String = "happy",
    val message: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val libCache = LibraryCache(app)
    private val chatStore = ChatStore(app)
    private val themePrefs = app.getSharedPreferences("saucyfy_theme", Context.MODE_PRIVATE)
    private val http = Http(app.cacheDir)
    private val api = SupabaseApi(http, store)
    private val meta = MetadataApi(http)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var searchJob: Job? = null
    private var userJob: Job? = null
    private var pollJob: Job? = null
    private var foreground = true
    private val chatLock = Mutex()
    private val attempts = ArrayDeque<Long>()

    init {
        val s = store.get()
        // Show the last-known library instantly, then refresh in the background.
        val cached: List<LibraryItem> = s?.let { libCache.get(it.userId) } ?: emptyList()
        val unread: Map<String, Int> = s?.let { chatStore.allUnread(it.userId) } ?: emptyMap()
        val theme = themePrefs.getString("id", "midnight") ?: "midnight"
        _ui.update { it.copy(session = s, booting = false, library = cached, unread = unread, themeId = theme) }
        if (s != null) onSignedIn()
    }

    private fun onSignedIn() {
        refreshLibrary()
        loadDiscover()
        loadMe()
        startPolling()
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    private fun toast(msg: String) {
        _ui.update { it.copy(message = msg) }
    }

    private fun launchSafe(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: AuthExpired) {
            signOut()
            toast(e.message ?: "Please sign in again")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast(e.message ?: "Something went wrong")
        }
    }

    private fun persist() {
        val st = _ui.value
        st.session?.let { libCache.put(it.userId, st.library) }
    }

    // ---- theme ----
    fun setTheme(id: String) {
        themePrefs.edit().putString("id", id).apply()
        _ui.update { it.copy(themeId = id) }
    }

    // ---- auth (with attempt lockout: 5 tries per minute) ----
    private fun lockedSeconds(): Long {
        val now = System.currentTimeMillis()
        while (attempts.isNotEmpty() && now - attempts.first() > 60_000) attempts.removeFirst()
        return if (attempts.size >= 5) (60_000 - (now - attempts.first())) / 1000 + 1 else 0L
    }

    fun authenticate(email: String, password: String, signUp: Boolean) {
        val e = email.trim()
        if (!e.contains("@") || e.length < 5) {
            toast("Enter a valid email address"); return
        }
        if (password.length < 8) {
            toast("Password must be at least 8 characters"); return
        }
        val wait = lockedSeconds()
        if (wait > 0) {
            toast("Too many attempts. Try again in ${wait}s"); return
        }
        if (_ui.value.authBusy) return
        attempts.addLast(System.currentTimeMillis())
        _ui.update { it.copy(authBusy = true) }
        launchSafe {
            try {
                val s = if (signUp) api.signUp(e, password) else api.signIn(e, password)
                if (s == null) {
                    toast("Account created. Check your email to confirm it, then sign in.")
                } else {
                    attempts.clear()
                    val cached: List<LibraryItem> = libCache.get(s.userId) ?: emptyList()
                    val unread = chatStore.allUnread(s.userId)
                    _ui.update { it.copy(session = s, library = cached, unread = unread) }
                    onSignedIn()
                }
            } finally {
                _ui.update { it.copy(authBusy = false) }
            }
        }
    }

    /** Handles saucyfy://login-callback links (email confirmation). */
    fun handleRedirect(raw: String) {
        launchSafe {
            val s = api.sessionFromRedirect(raw)
            _ui.update { it.copy(session = s) }
            onSignedIn()
        }
    }

    fun signOut() {
        store.clear()
        libCache.clear()
        searchJob?.cancel()
        userJob?.cancel()
        pollJob?.cancel()
        _ui.update { UiState(booting = false, themeId = it.themeId, trending = it.trending, topAnime = it.topAnime) }
    }

    // ---- profile ----
    private fun loadMe() {
        viewModelScope.launch {
            val p = runCatching { api.myProfile() }.getOrNull()
            if (p != null) _ui.update { it.copy(myUsername = p.username) }
        }
    }

    fun editProfile(name: String, username: String) {
        val n = name.trim().take(30)
        val u = username.trim().lowercase()
        if (n.length < 2) {
            toast("Name is too short"); return
        }
        if (!Regex("^[a-z0-9_]{3,20}$").matches(u)) {
            toast("Username: 3-20 letters, numbers or _"); return
        }
        launchSafe {
            api.patchProfile(displayName = n, username = u) // fails first if the username is taken
            val s = api.updateProfile(n, null)
            _ui.update { it.copy(session = s, myUsername = u) }
            toast("Profile updated")
        }
    }

    fun setAvatar(uri: Uri) {
        if (_ui.value.profileBusy) return
        _ui.update { it.copy(profileBusy = true) }
        launchSafe {
            try {
                val bytes = withContext(Dispatchers.IO) { compressAvatar(uri) }
                val url = api.uploadAvatar(bytes)
                val s = api.updateProfile(null, url)
                runCatching { api.patchProfile(avatarUrl = url) }
                _ui.update { it.copy(session = s) }
                toast("Profile photo updated")
            } finally {
                _ui.update { it.copy(profileBusy = false) }
            }
        }
    }

    /** Center-crops to a square, shrinks to <= 512 px and re-encodes as JPEG so it is always under 1 MB. */
    private fun compressAvatar(uri: Uri): ByteArray {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Could not read that image")
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
        val out = ByteArrayOutputStream()
        var q = 88
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            q -= 10
        } while (out.size() > 1_000_000 && q > 20)
        if (out.size() > 1_000_000) throw IOException("Image is too large (max 1 MB)")
        return out.toByteArray()
    }

    // ---- library ----
    fun refreshLibrary() {
        launchSafe {
            _ui.update { it.copy(libLoading = true) }
            try {
                val uid = _ui.value.session?.userId ?: throw AuthExpired()
                val l = api.loadLibrary(uid)
                _ui.update { it.copy(library = l) }
                persist()
            } finally {
                _ui.update { it.copy(libLoading = false) }
            }
        }
    }

    private fun loadDiscover() {
        viewModelScope.launch {
            val t: List<MediaResult> = runCatching { meta.trending() }.getOrDefault(emptyList())
            _ui.update { it.copy(trending = t) }
            val a: List<MediaResult> = runCatching { meta.topAnime() }.getOrDefault(emptyList())
            _ui.update { it.copy(topAnime = a) }
        }
    }

    /** Fetches real season/episode counts so the counters can't be tapped past what exists. */
    fun loadLimits(d: Detail) {
        if (d.type == MediaType.MOVIE) return
        val key = d.type.key + ":" + d.externalId
        if (_ui.value.limits.containsKey(key)) return
        viewModelScope.launch {
            val lim: Limits = runCatching {
                if (d.type == MediaType.TV) {
                    Limits(seasons = meta.tvmazeSeasons(d.externalId).ifEmpty { null })
                } else {
                    Limits(totalEpisodes = d.episodes ?: meta.animeEpisodes(d.externalId))
                }
            }.getOrNull() ?: return@launch
            _ui.update { it.copy(limits = it.limits + (key to lim)) }
        }
    }

    private fun maxEpisodeFor(item: LibraryItem): Int {
        val lim = _ui.value.limits[item.mediaType + ":" + item.externalId]
        return when (item.type) {
            MediaType.TV -> lim?.seasons?.get(item.season ?: 1) ?: 100
            MediaType.ANIME -> lim?.totalEpisodes ?: 3000
            MediaType.MOVIE -> 0
        }
    }

    // ---- search: parallel, results appear as each source answers, with spelling help ----
    fun onQuery(q: String) {
        _ui.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _ui.update { it.copy(results = emptyList(), searching = false, suggestion = null, corrected = null) }
            return
        }
        if (q.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(250)
            runSearch(q.trim(), _ui.value.filter)
        }
    }

    fun setFilter(f: MediaType?) {
        _ui.update { it.copy(filter = f) }
        onQuery(_ui.value.query)
    }

    private suspend fun searchAll(
        q: String,
        f: MediaType?,
        errors: MutableList<String>,
        onPartial: (List<MediaResult>) -> Unit
    ): List<MediaResult> = coroutineScope {
        val parts = arrayOfNulls<List<MediaResult>>(3)
        fun emit() = onPartial(parts.filterNotNull().flatten())
        val jobs = listOf(
            async {
                if (f == null || f == MediaType.MOVIE) {
                    parts[0] = runCatching { meta.searchMovies(q) }
                        .onFailure { errors += "Movies: ${it.message}" }
                        .getOrDefault(emptyList())
                    emit()
                }
            },
            async {
                if (f == null || f == MediaType.TV) {
                    parts[1] = runCatching { meta.searchShows(q) }
                        .onFailure { errors += "Shows: ${it.message}" }
                        .getOrDefault(emptyList())
                    emit()
                }
            },
            async {
                if (f == null || f == MediaType.ANIME) {
                    parts[2] = runCatching { meta.searchAnime(q) }
                        .onFailure { errors += "Anime: ${it.message}" }
                        .getOrDefault(emptyList())
                    emit()
                }
            }
        )
        jobs.awaitAll()
        parts.filterNotNull().flatten()
    }

    private fun localTitles(): List<String> {
        val st = _ui.value
        return st.library.map { it.title } + st.trending.map { it.title } + st.topAnime.map { it.title }
    }

    private suspend fun runSearch(q: String, f: MediaType?) {
        _ui.update { it.copy(searching = true, suggestion = null, corrected = null) }
        try {
            val errors = mutableListOf<String>()
            val out = searchAll(q, f, errors) { partial -> _ui.update { it.copy(results = partial) } }
            if (errors.isNotEmpty() && out.isEmpty()) {
                toast("Couldn't reach: " + errors.joinToString("; ").take(150))
            }
            if (out.isEmpty()) {
                // Nothing found: try your own titles first (instant), then Wikipedia's spelling correction.
                val alt = closestTitle(q, localTitles()) ?: runCatching { meta.suggest(q) }.getOrNull()
                if (alt != null) {
                    val fixed = searchAll(alt, f, mutableListOf()) { }
                    if (fixed.isNotEmpty()) {
                        _ui.update { it.copy(results = fixed, corrected = alt) }
                    } else {
                        _ui.update { it.copy(results = emptyList(), suggestion = alt) }
                    }
                } else {
                    _ui.update { it.copy(results = emptyList()) }
                }
            }
        } finally {
            _ui.update { it.copy(searching = false) }
        }
    }

    private fun replaceLocal(saved: LibraryItem) {
        _ui.update { st ->
            val rest = st.library.filterNot { it.mediaType == saved.mediaType && it.externalId == saved.externalId }
            st.copy(library = listOf(saved) + rest)
        }
        persist()
    }

    fun save(
        d: Detail,
        status: Status,
        rating: Int?,
        season: Int?,
        episode: Int?,
        notes: String?,
        onDone: () -> Unit
    ) {
        launchSafe {
            val uid = _ui.value.session?.userId ?: throw AuthExpired()
            val saved = api.upsert(
                LibraryUpsert(
                    userId = uid,
                    mediaType = d.type.key,
                    externalId = d.externalId,
                    title = d.title,
                    posterUrl = d.posterUrl,
                    year = d.year.ifBlank { null },
                    overview = d.overview.ifBlank { null },
                    status = status.key,
                    rating = rating,
                    season = season,
                    episode = episode,
                    notes = notes
                )
            )
            replaceLocal(saved)
            onDone()
            toast("Saved to your library")
        }
    }

    /** +1 episode: the list updates instantly, then syncs (and rolls back if it fails). */
    fun bumpEpisode(item: LibraryItem) {
        val next = (item.episode ?: 0) + 1
        if (next > maxEpisodeFor(item)) {
            toast("That's the last episode"); return
        }
        replaceLocal(item.copy(episode = next))
        launchSafe {
            try {
                val uid = _ui.value.session?.userId ?: throw AuthExpired()
                replaceLocal(api.upsert(item.toUpsert(uid, episode = next)))
            } catch (e: Exception) {
                replaceLocal(item)
                throw e
            }
        }
    }

    fun remove(item: LibraryItem, onDone: () -> Unit) {
        launchSafe {
            val id = item.id ?: return@launchSafe
            api.delete(id)
            _ui.update { st -> st.copy(library = st.library.filterNot { it.id == id }) }
            persist()
            onDone()
            toast("Removed from your library")
        }
    }

    // ---- friends ----
    fun loadFriends() {
        launchSafe {
            _ui.update { it.copy(friendsLoading = true) }
            try {
                val f = api.loadFriends()
                val me = _ui.value.session?.userId
                val loaded: Map<String, List<ChatMsg>> = if (me == null) emptyMap() else withContext(Dispatchers.IO) {
                    f.filter { it.accepted }.associate { it.profile.id to chatStore.load(me, it.profile.id) }
                }
                _ui.update { st -> st.copy(friends = f, chats = loaded + st.chats) }
            } finally {
                _ui.update { it.copy(friendsLoading = false) }
            }
        }
    }

    fun searchUsers(q: String) {
        _ui.update { it.copy(userQuery = q) }
        userJob?.cancel()
        if (q.trim().length < 2) {
            _ui.update { it.copy(userResults = emptyList(), userSearching = false) }
            return
        }
        userJob = viewModelScope.launch {
            delay(300)
            _ui.update { it.copy(userSearching = true) }
            try {
                val r = api.searchUsers(q.trim())
                _ui.update { it.copy(userResults = r) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Could not search people")
            } finally {
                _ui.update { it.copy(userSearching = false) }
            }
        }
    }

    fun addFriend(p: Profile) {
        launchSafe {
            val existing = _ui.value.friends.firstOrNull { it.profile.id == p.id }
            if (existing != null && existing.incoming && !existing.accepted) {
                api.acceptRequest(existing.friendshipId)
                toast("You're now friends with ${p.shownName}")
            } else if (existing == null) {
                api.sendRequest(p.id)
                toast("Friend request sent")
            }
            val f = api.loadFriends()
            _ui.update { it.copy(friends = f) }
        }
    }

    fun acceptFriend(e: FriendEntry) {
        launchSafe {
            api.acceptRequest(e.friendshipId)
            val f = api.loadFriends()
            _ui.update { it.copy(friends = f) }
            toast("You're now friends with ${e.profile.shownName}")
        }
    }

    /** Decline / cancel a request, or unfriend. */
    fun removeFriend(e: FriendEntry) {
        launchSafe {
            api.removeFriendship(e.friendshipId)
            val f = api.loadFriends()
            _ui.update { st ->
                st.copy(
                    friends = f,
                    viewingFriend = if (st.viewingFriend?.id == e.profile.id) null else st.viewingFriend,
                    chatWith = if (st.chatWith?.id == e.profile.id) null else st.chatWith
                )
            }
        }
    }

    fun openFriend(p: Profile) {
        _ui.update { it.copy(viewingFriend = p, friendItems = emptyList(), friendItemsLoading = true) }
        launchSafe {
            try {
                val items = api.libraryOf(p.id)
                _ui.update { if (it.viewingFriend?.id == p.id) it.copy(friendItems = items) else it }
            } finally {
                _ui.update { it.copy(friendItemsLoading = false) }
            }
        }
    }

    fun closeFriend() {
        _ui.update { it.copy(viewingFriend = null, friendItems = emptyList()) }
    }

    // ---- chat: history lives on the device; the server only holds a message until it's downloaded ----
    fun setForeground(f: Boolean) {
        foreground = f
        if (f) startPolling() else pollJob?.cancel()
    }

    private fun startPolling() {
        pollJob?.cancel()
        if (!foreground || _ui.value.session == null) return
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    pollInbox()
                } catch (e: AuthExpired) {
                    signOut()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // offline or temporary error: try again next round
                }
                delay(if (_ui.value.chatWith != null) 4000L else 20000L)
            }
        }
    }

    private fun parseTs(s: String): Long =
        runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())

    private suspend fun appendChat(me: String, friend: String, add: List<ChatMsg>) = chatLock.withLock {
        val cur = _ui.value.chats[friend] ?: withContext(Dispatchers.IO) { chatStore.load(me, friend) }
        val known = cur.map { it.id }.toHashSet()
        val merged = (cur + add.filter { it.id !in known }).sortedBy { it.ts }.takeLast(300)
        withContext(Dispatchers.IO) { chatStore.save(me, friend, merged) }
        _ui.update { it.copy(chats = it.chats + (friend to merged)) }
    }

    private suspend fun removeChatMsg(me: String, friend: String, id: String) = chatLock.withLock {
        val cur = _ui.value.chats[friend] ?: return@withLock
        val merged = cur.filter { it.id != id }
        withContext(Dispatchers.IO) { chatStore.save(me, friend, merged) }
        _ui.update { it.copy(chats = it.chats + (friend to merged)) }
    }

    private suspend fun pollInbox() {
        val me = _ui.value.session?.userId ?: return
        val inbox = api.fetchMailbox()
        if (inbox.isEmpty()) return
        for ((sender, msgs) in inbox.groupBy { it.sender }) {
            val known: Set<String> = _ui.value.chats[sender]?.map { it.id }?.toSet()
                ?: withContext(Dispatchers.IO) { chatStore.load(me, sender).map { it.id }.toSet() }
            val fresh = msgs.filter { it.id !in known }
            appendChat(me, sender, msgs.map { ChatMsg(it.id, false, it.body, parseTs(it.createdAt)) })
            if (fresh.isNotEmpty() && _ui.value.chatWith?.id != sender) {
                val n = (_ui.value.unread[sender] ?: 0) + fresh.size
                chatStore.setUnread(me, sender, n)
                _ui.update { it.copy(unread = it.unread + (sender to n)) }
            }
        }
        // Saved on this device first, now free the server space.
        api.deleteMailbox(inbox.map { it.id })
    }

    fun openChat(p: Profile) {
        val me = _ui.value.session?.userId ?: return
        _ui.update { it.copy(chatWith = p, unread = it.unread - p.id) }
        chatStore.setUnread(me, p.id, 0)
        viewModelScope.launch {
            if (_ui.value.chats[p.id] == null) appendChat(me, p.id, emptyList())
            runCatching { pollInbox() }
        }
    }

    fun closeChat() {
        _ui.update { it.copy(chatWith = null) }
    }

    fun sendMessage(to: Profile, body: String) {
        val text = body.trim().take(500)
        if (text.isEmpty()) return
        val me = _ui.value.session?.userId ?: return
        val msg = ChatMsg(UUID.randomUUID().toString(), true, text, System.currentTimeMillis())
        viewModelScope.launch { appendChat(me, to.id, listOf(msg)) }
        launchSafe {
            try {
                api.sendMessage(to.id, text)
            } catch (e: Exception) {
                removeChatMsg(me, to.id, msg.id)
                throw e
            }
        }
    }

    fun loadGifs(category: String) {
        _ui.update { it.copy(gifCategory = category, gifsLoading = true) }
        viewModelScope.launch {
            val g = runCatching { meta.gifs(category) }.getOrDefault(emptyList())
            _ui.update { if (it.gifCategory == category) it.copy(gifs = g, gifsLoading = false) else it }
        }
    }
}
