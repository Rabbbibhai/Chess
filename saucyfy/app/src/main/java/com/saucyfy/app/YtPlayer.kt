package com.saucyfy.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Plays YouTube with YouTube's official embedded player (IFrame API) inside a WebView.
 * The player stays visible on screen, as YouTube's terms require.
 */
class YtPlayer(ctx: Context) {
    data class State(val ready: Boolean = false, val playing: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** current position and duration, in seconds (kept apart so the UI only redraws the player) */
    private val _progress = MutableStateFlow(0f to 0f)
    val progress: StateFlow<Pair<Float, Float>> = _progress.asStateFlow()

    var onEnded: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportZoom(false)
        setBackgroundColor(0xFF000000.toInt())
        webViewClient = WebViewClient()
        addJavascriptInterface(Bridge(), "Android")
        loadDataWithBaseURL("https://www.youtube.com", HTML, "text/html", "utf-8", null)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            _state.update { it.copy(ready = true) }
        }

        @JavascriptInterface
        fun onState(s: Int) {
            when (s) {
                0 -> {
                    _state.update { it.copy(playing = false) }
                    main.post { onEnded?.invoke() }
                }
                1 -> _state.update { it.copy(playing = true, error = null) }
                2 -> _state.update { it.copy(playing = false) }
            }
        }

        @JavascriptInterface
        fun onTime(cur: Double, dur: Double) {
            _progress.value = cur.toFloat() to dur.toFloat()
        }

        @JavascriptInterface
        fun onError(code: Int) {
            val msg = when (code) {
                100 -> "This video is unavailable or private"
                101, 150 -> "The owner doesn't allow this video to be played outside YouTube"
                152, 153 -> "YouTube blocked the player for this video, try again"
                else -> "Couldn't play this video (error $code)"
            }
            _state.update { it.copy(playing = false, error = msg) }
        }
    }

    private fun js(code: String) {
        main.post { webView.evaluateJavascript(code, null) }
    }

    fun load(videoId: String) {
        if (!Regex("^[0-9A-Za-z_-]{11}$").matches(videoId)) return
        _state.update { it.copy(error = null) }
        _progress.value = 0f to 0f
        js("loadId('$videoId')")
    }

    fun play() = js("play()")
    fun pause() = js("pause()")
    fun seek(sec: Float) = js("seek($sec)")

    companion object {
        private const val HTML = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player, ready = false, pending = null;
function onYouTubeIframeAPIReady() {
  player = new YT.Player('p', {
    height: '100%', width: '100%',
    playerVars: { playsinline: 1, rel: 0, fs: 0, modestbranding: 1, origin: 'https://www.youtube.com' },
    events: {
      onReady: function () { ready = true; Android.onReady(); if (pending) { player.loadVideoById(pending); pending = null; } },
      onStateChange: function (e) { Android.onState(e.data); },
      onError: function (e) { Android.onError(e.data); }
    }
  });
  setInterval(function () {
    if (ready && player.getDuration) { Android.onTime(player.getCurrentTime() || 0, player.getDuration() || 0); }
  }, 500);
}
function loadId(id) { if (ready) { player.loadVideoById(id); } else { pending = id; } }
function play() { if (player) player.playVideo(); }
function pause() { if (player) player.pauseVideo(); }
function seek(s) { if (player) player.seekTo(s, true); }
</script></body></html>
"""
    }
}
