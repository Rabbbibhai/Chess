package com.saucyfy.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saucyfy.app.MusicViewModel
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.Track
import com.saucyfy.app.data.type

private fun clock(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Small drawn player icons: 0 previous, 1 next, 2 play, 3 pause. */
@Composable
private fun Glyph(kind: Int, tint: Color, dp: Dp = 24.dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            2 -> drawPath(
                Path().apply {
                    moveTo(w * 0.28f, h * 0.15f); lineTo(w * 0.86f, h * 0.5f); lineTo(w * 0.28f, h * 0.85f); close()
                },
                tint
            )
            3 -> {
                drawRect(tint, Offset(w * 0.22f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                drawRect(tint, Offset(w * 0.58f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
            }
            0 -> {
                drawRect(tint, Offset(w * 0.14f, h * 0.15f), Size(w * 0.12f, h * 0.7f))
                drawPath(
                    Path().apply {
                        moveTo(w * 0.88f, h * 0.15f); lineTo(w * 0.34f, h * 0.5f); lineTo(w * 0.88f, h * 0.85f); close()
                    },
                    tint
                )
            }
            else -> {
                drawRect(tint, Offset(w * 0.74f, h * 0.15f), Size(w * 0.12f, h * 0.7f))
                drawPath(
                    Path().apply {
                        moveTo(w * 0.12f, h * 0.15f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.12f, h * 0.85f); close()
                    },
                    tint
                )
            }
        }
    }
}

/** The music-only section (opened by tapping the Library tab three times). */
@Composable
fun MusicSection(
    mvm: MusicViewModel,
    library: List<LibraryItem>,
    onSave: (Track) -> Unit,
    onUnsave: (LibraryItem) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val st by mvm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) } // 0 search, 1 saved, 2 uploads
    var settings by remember { mutableStateOf(false) }

    val savedItems = library.filter { it.type == MediaType.MUSIC && it.externalId.startsWith("yt:") }
    val savedTracks = savedItems.map { Track(it.externalId.removePrefix("yt:"), it.title, it.overview ?: "") }
    val savedIds = savedItems.map { it.externalId.removePrefix("yt:") }.toSet()
    val toggleSave: (Track) -> Unit = { t ->
        val item = savedItems.firstOrNull { it.externalId == "yt:" + t.videoId }
        if (item != null) onUnsave(item) else onSave(t)
    }

    LaunchedEffect(st.message) {
        // message is shown until the next action
    }

    if (settings) {
        ChannelDialog(
            connected = st.channelName,
            hasKey = st.hasKey,
            onSave = { ch, key ->
                mvm.setChannel(ch, key)
                settings = false
            },
            onDismiss = { settings = false }
        )
    }

    Column(modifier.systemBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Elog") }
            Column(Modifier.weight(1f)) {
                Text("Music", style = MaterialTheme.typography.headlineMedium)
                Text(
                    st.channelName ?: "No channel connected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "Channel settings") }
        }

        NowPlaying(mvm, st.now, st.playing, st.error)

        st.message?.let { m ->
            Surface(
                onClick = { mvm.clearMessage() },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    m,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Search") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Saved ${savedTracks.size}") })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("My uploads") })
        }

        when (tab) {
            0 -> {
                OutlinedTextField(
                    value = st.query,
                    onValueChange = { mvm.onQuery(it) },
                    singleLine = true,
                    placeholder = { Text("Search your songs, or paste a YouTube link") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (st.query.isNotEmpty()) {
                            IconButton(onClick = { mvm.onQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                if (st.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                when {
                    st.channelId == null && st.query.isBlank() -> EmptyState(
                        "Connect your channel",
                        "Tap the gear and enter your YouTube channel (link or @handle). You can also paste any YouTube link above."
                    )
                    st.query.isBlank() -> EmptyState(
                        "Search your music",
                        if (st.hasKey) "Searches your whole channel." else "Searches your latest uploads. Add a YouTube API key in settings to search everything, or paste a link."
                    )
                    st.results.isEmpty() && !st.searching -> EmptyState(
                        "No songs found",
                        if (st.hasKey) "Try different words." else "Only your latest 15 uploads are searchable without an API key. Paste the song's link to add older ones."
                    )
                    else -> TrackList(st.results, st.now, savedIds, { list, i -> mvm.play(list, i) }, toggleSave)
                }
            }
            1 -> {
                if (savedTracks.isEmpty()) {
                    EmptyState("No saved songs", "Tap the heart on a song to keep it here.")
                } else {
                    TrackList(savedTracks, st.now, savedIds, { list, i -> mvm.play(list, i) }, toggleSave)
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Newest uploads",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { mvm.loadUploads() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                }
                when {
                    st.channelId == null -> EmptyState("No channel yet", "Tap the gear and connect your YouTube channel.")
                    st.uploadsLoading && st.uploads.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                    st.uploads.isEmpty() -> EmptyState("Nothing found", "No public uploads came back for this channel.")
                    else -> TrackList(st.uploads, st.now, savedIds, { list, i -> mvm.play(list, i) }, toggleSave)
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(mvm: MusicViewModel, now: Track?, playing: Boolean, error: String?) {
    val progress by mvm.player.progress.collectAsStateWithLifecycle()
    val (cur, dur) = progress
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val fraction = if (dur > 0f) (cur / dur).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        val wv = mvm.player.webView
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (now == null) {
                    Text(
                        "Pick a song to play",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    now?.title ?: "Nothing playing",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    error ?: (now?.artist ?: ""),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = if (dragging) dragValue else fraction,
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        mvm.seek(dragValue * dur)
                        dragging = false
                    },
                    enabled = dur > 0f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(if (dragging) dragValue * dur else cur), style = MaterialTheme.typography.labelSmall)
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { mvm.prev() }) { Glyph(0, MaterialTheme.colorScheme.onSurface) }
                        FilledIconButton(onClick = { mvm.toggle() }, modifier = Modifier.size(52.dp)) {
                            Glyph(if (playing) 3 else 2, MaterialTheme.colorScheme.onPrimary, 26.dp)
                        }
                        IconButton(onClick = { mvm.next() }) { Glyph(1, MaterialTheme.colorScheme.onSurface) }
                    }
                    Text(clock(dur), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    now: Track?,
    savedIds: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onToggle: (Track) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks.size) { i ->
            val t = tracks[i]
            TrackRow(
                t,
                current = now?.videoId == t.videoId,
                saved = t.videoId in savedIds,
                onPlay = { onPlay(tracks, i) },
                onToggle = { onToggle(t) }
            )
        }
    }
}

@Composable
private fun TrackRow(t: Track, current: Boolean, saved: Boolean, onPlay: () -> Unit, onToggle: () -> Unit) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Poster(t.thumb, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    t.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (saved) "Remove from saved" else "Save",
                    tint = if (saved) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChannelDialog(connected: String?, hasKey: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var channel by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your YouTube channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (connected != null) "Connected: $connected" else "Not connected yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    singleLine = true,
                    label = { Text("Channel link, @handle or id") },
                    supportingText = { Text("Leave empty and Save to disconnect.") }
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(if (hasKey) "YouTube API key (saved)" else "YouTube API key (optional)") },
                    supportingText = { Text("Lets you search every song on the channel. Stays on this phone.") }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(channel, key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
