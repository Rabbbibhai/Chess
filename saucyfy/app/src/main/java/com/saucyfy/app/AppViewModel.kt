package com.saucyfy.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saucyfy.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val booting: Boolean = true,
    val session: Session? = null,
    val authBusy: Boolean = false,
    val library: List<LibraryItem> = emptyList(),
    val libLoading: Boolean = false,
    val query: String = "",
    val filter: MediaType? = null,
    val results: List<MediaResult> = emptyList(),
    val searching: Boolean = false,
    val trending: List<MediaResult> = emptyList(),
    val topAnime: List<MediaResult> = emptyList(),
    val message: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val http = Http()
    private val api = SupabaseApi(http, store)
    private val meta = MetadataApi(http)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var searchJob: Job? = null
    private val attempts = ArrayDeque<Long>()

    init {
        val s = store.get()
        _ui.update { it.copy(session = s, booting = false) }
        if (s != null) {
            refreshLibrary()
            loadDiscover()
        }
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
                    _ui.update { it.copy(session = s) }
                    refreshLibrary()
                    loadDiscover()
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
            refreshLibrary()
            loadDiscover()
        }
    }

    fun signOut() {
        store.clear()
        searchJob?.cancel()
        _ui.update { UiState(booting = false) }
    }

    fun refreshLibrary() {
        launchSafe {
            _ui.update { it.copy(libLoading = true) }
            try {
                val l = api.loadLibrary()
                _ui.update { it.copy(library = l) }
            } finally {
                _ui.update { it.copy(libLoading = false) }
            }
        }
    }

    private fun loadDiscover() {
        viewModelScope.launch {
            val t: List<MediaResult> =
                if (meta.hasTmdb) runCatching { meta.trending() }.getOrDefault(emptyList()) else emptyList()
            _ui.update { it.copy(trending = t) }
            val a: List<MediaResult> = runCatching { meta.topAnime() }.getOrDefault(emptyList())
            _ui.update { it.copy(topAnime = a) }
        }
    }

    fun onQuery(q: String) {
        _ui.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _ui.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            runSearch(q.trim(), _ui.value.filter)
        }
    }

    fun setFilter(f: MediaType?) {
        _ui.update { it.copy(filter = f) }
        onQuery(_ui.value.query)
    }

    private suspend fun runSearch(q: String, f: MediaType?) {
        _ui.update { it.copy(searching = true) }
        try {
            val out = coroutineScope {
                val tm = async {
                    if (f == MediaType.ANIME || !meta.hasTmdb) emptyList<MediaResult>()
                    else runCatching { meta.searchTmdb(q, f) }.getOrDefault(emptyList())
                }
                val an = async {
                    if (f == MediaType.MOVIE || f == MediaType.TV) emptyList<MediaResult>()
                    else runCatching { meta.searchAnime(q) }.getOrDefault(emptyList())
                }
                tm.await() + an.await()
            }
            _ui.update { it.copy(results = out) }
        } finally {
            _ui.update { it.copy(searching = false) }
        }
    }

    private fun replaceLocal(saved: LibraryItem) {
        _ui.update { st ->
            val rest = st.library.filterNot { it.mediaType == saved.mediaType && it.externalId == saved.externalId }
            st.copy(library = listOf(saved) + rest)
        }
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

    fun bumpEpisode(item: LibraryItem) {
        launchSafe {
            val uid = _ui.value.session?.userId ?: throw AuthExpired()
            val saved = api.upsert(item.toUpsert(uid, episode = (item.episode ?: 0) + 1))
            replaceLocal(saved)
        }
    }

    fun remove(item: LibraryItem, onDone: () -> Unit) {
        launchSafe {
            val id = item.id ?: return@launchSafe
            api.delete(id)
            _ui.update { st -> st.copy(library = st.library.filterNot { it.id == id }) }
            onDone()
            toast("Removed from your library")
        }
    }
}
