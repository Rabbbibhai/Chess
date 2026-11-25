package com.saucyfy.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saucyfy.app.data.Http
import com.saucyfy.app.data.Track
import com.saucyfy.app.data.YtApi
import com.saucyfy.app.data.extractVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicState(
    val channelId: String? = null,
    val channelName: String? = null,
    val hasKey: Boolean = false,
    val uploads: List<Track> = emptyList(),
    val uploadsLoading: Boolean = false,
    val query: String = "",
    val results: List<Track> = emptyList(),
    val searching: Boolean = false,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val now: Track? get() = queue.getOrNull(index)
}

/** The music section: own player + search, kept alive while you switch screens. */
class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("elog_music", Context.MODE_PRIVATE)
    private val api = YtApi(Http(null))
    val player = YtPlayer(app)

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var channelId: String? = prefs.getString("channel", null)
    private var apiKey: String = prefs.getString("key", "") ?: ""
    private var searchJob: Job? = null

    init {
        _state.update { it.copy(channelId = channelId, hasKey = apiKey.isNotBlank()) }
        player.onEnded = { next() }
        viewModelScope.launch {
            player.state.collect { s -> _state.update { it.copy(playing = s.playing, error = s.error) } }
        }
        if (channelId != null) loadUploads()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun msg(text: String) {
        _state.update { it.copy(message = text) }
    }

    // ---- channel ----
    fun loadUploads() {
        val cid = channelId ?: return
        viewModelScope.launch {
            _state.update { it.copy(uploadsLoading = true) }
            try {
                val (name, tracks) = api.feed(cid)
                _state.update { it.copy(channelName = name, uploads = tracks) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msg(e.message ?: "Couldn't load uploads")
            } finally {
                _state.update { it.copy(uploadsLoading = false) }
            }
        }
    }

    /** [input] = channel link, @handle or UC... id (empty removes the channel). [key] = optional YouTube Data API key. */
    fun setChannel(input: String, key: String) {
        viewModelScope.launch {
            try {
                val k = key.trim()
                val cid = if (input.isBlank()) null else api.resolve(input, k.ifBlank { null })
                prefs.edit().putString("channel", cid).putString("key", k).apply()
                channelId = cid
                apiKey = k
                _state.update {
                    it.copy(
                        channelId = cid,
                        hasKey = k.isNotBlank(),
                        uploads = if (cid == null) emptyList() else it.uploads,
                        channelName = if (cid == null) null else it.channelName
                    )
                }
                if (cid != null) loadUploads()
                msg(if (cid == null) "Channel removed" else "Channel connected")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msg(e.message ?: "Couldn't connect that channel")
            }
        }
    }

    // ---- search: your uploads instantly, plus API search / pasted links ----
    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        val s = q.trim()
        if (s.isEmpty()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        val words = s.split(" ").filter { it.isNotBlank() }
        val local = _state.value.uploads.filter { t -> words.all { w -> t.title.contains(w, ignoreCase = true) } }
        _state.update { it.copy(results = local) }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            try {
                val out = LinkedHashMap<String, Track>()
                local.forEach { out[it.videoId] = it }
                extractVideoId(s)?.let { id ->
                    runCatching { api.oembed(id) }
                        .onFailure { msg(it.message ?: "Couldn't read that link") }
                        .getOrNull()?.let { out[it.videoId] = it }
                }
                val cid = channelId
                if (cid != null && apiKey.isNotBlank()) {
                    runCatching { api.apiSearch(cid, apiKey, s) }
                        .onFailure { msg("YouTube search: ${it.message}") }
                        .getOrNull()?.forEach { out.putIfAbsent(it.videoId, it) }
                }
                _state.update { it.copy(results = out.values.toList()) }
            } finally {
                _state.update { it.copy(searching = false) }
            }
        }
    }

    // ---- playback ----
    fun play(list: List<Track>, i: Int) {
        _state.update { it.copy(queue = list) }
        startAt(i)
    }

    private fun startAt(i: Int) {
        val t = _state.value.queue.getOrNull(i) ?: return
        _state.update { it.copy(index = i, error = null) }
        player.load(t.videoId)
    }

    fun next() {
        val st = _state.value
        if (st.index + 1 < st.queue.size) startAt(st.index + 1)
    }

    fun prev() {
        val st = _state.value
        if (st.index > 0) startAt(st.index - 1)
    }

    fun toggle() {
        if (_state.value.index < 0) return
        if (_state.value.playing) player.pause() else player.play()
    }

    fun seek(sec: Float) = player.seek(sec)

    override fun onCleared() {
        player.pause()
        super.onCleared()
    }
}
