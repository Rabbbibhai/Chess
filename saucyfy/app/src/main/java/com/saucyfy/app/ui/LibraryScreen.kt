package com.saucyfy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saucyfy.app.UiState
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.Status
import com.saucyfy.app.data.isSingle
import com.saucyfy.app.data.labelFor
import com.saucyfy.app.data.plural
import com.saucyfy.app.data.progressLabel
import com.saucyfy.app.data.statusEnum
import com.saucyfy.app.data.toDetail
import com.saucyfy.app.data.type

private fun typeColor(t: MediaType): Color = when (t) {
    MediaType.MOVIE -> Brand.Amber
    MediaType.TV -> Brand.Sky
    MediaType.ANIME -> Color(0xFFF472B6)
    MediaType.MANGA -> Brand.Grass
    MediaType.MUSIC -> Color(0xFFB39DFF)
}

/** Library home = big tiles (Movies, Shows, Anime, Manga, Music). Tap one to open its own page. */
@Composable
fun LibraryScreen(
    ui: UiState,
    onOpen: (Detail) -> Unit,
    onBump: (LibraryItem) -> Unit,
    onSetStatus: (LibraryItem, Status) -> Unit,
    onRefresh: () -> Unit,
    onFind: () -> Unit,
    modifier: Modifier = Modifier
) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val type: MediaType? = section?.let { MediaType.from(it) }

    BackHandler(enabled = type != null) {
        section = null
        filter = null
    }

    if (type == null) {
        LibraryHome(
            ui = ui,
            onOpen = onOpen,
            onBump = onBump,
            onRefresh = onRefresh,
            onFind = onFind,
            onPick = {
                section = it.key
                filter = null
            },
            modifier = modifier
        )
    } else {
        LibrarySection(
            ui = ui,
            type = type,
            filter = filter,
            onFilter = { filter = it },
            onBack = {
                section = null
                filter = null
            },
            onOpen = onOpen,
            onBump = onBump,
            onSetStatus = onSetStatus,
            onFind = onFind,
            modifier = modifier
        )
    }
}

@Composable
private fun LibraryHome(
    ui: UiState,
    onOpen: (Detail) -> Unit,
    onBump: (LibraryItem) -> Unit,
    onRefresh: () -> Unit,
    onFind: () -> Unit,
    onPick: (MediaType) -> Unit,
    modifier: Modifier
) {
    val inProgress = ui.library.filter { it.status == Status.WATCHING.key && !it.type.isSingle }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${ui.library.size} titles · ${ui.library.count { it.status == Status.WATCHED.key }} finished",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        if (inProgress.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Continue")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(inProgress, key = { it.id ?: (it.mediaType + it.externalId) }) { item ->
                            ContinueCard(item, onClick = { onOpen(item.toDetail()) }, onBump = { onBump(item) })
                        }
                    }
                }
            }
        }
        if (ui.library.isEmpty() && !ui.libLoading) {
            item {
                EmptyState(
                    title = "Your library is empty",
                    body = "Search for a movie, show, anime, manga or song and save it with a rating or where you left off.",
                    action = { Button(onClick = onFind) { Text("Find something") } }
                )
            }
        }
        item { SectionHeader("Collections") }
        MediaType.entries.chunked(2).forEach { pair ->
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { t ->
                        SectionTile(
                            type = t,
                            items = ui.library.filter { it.type == t },
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(t) }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A big tappable tile for one kind of media, with a little stack of your newest posters. */
@Composable
private fun SectionTile(type: MediaType, items: List<LibraryItem>, modifier: Modifier, onClick: () -> Unit) {
    val accent = typeColor(type)
    val done = items.count { it.status == Status.WATCHED.key }
    val square = type == MediaType.MUSIC
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.height(160.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.42f), Color.Transparent)))
        ) {
            Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text(type.plural, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (items.isEmpty()) "Nothing yet" else "${items.size} · $done finished",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy((-20).dp)
            ) {
                items.take(3).reversed().forEach {
                    Poster(it.posterUrl, Modifier.width(if (square) 56.dp else 52.dp).height(if (square) 56.dp else 78.dp))
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(
    ui: UiState,
    type: MediaType,
    filter: String?,
    onFilter: (String?) -> Unit,
    onBack: () -> Unit,
    onOpen: (Detail) -> Unit,
    onBump: (LibraryItem) -> Unit,
    onSetStatus: (LibraryItem, Status) -> Unit,
    onFind: () -> Unit,
    modifier: Modifier
) {
    val base = ui.library.filter { it.type == type }
    val shown = base.filter { filter == null || it.status == filter }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library") }
                Column(Modifier.weight(1f)) {
                    Text(type.plural, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${base.size} titles · ${base.count { it.status == Status.WATCHED.key }} finished",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("All ${base.size}") })
                Status.entries.forEach { s ->
                    val n = base.count { it.status == s.key }
                    FilterChip(
                        selected = filter == s.key,
                        onClick = { onFilter(s.key) },
                        label = { Text("${s.labelFor(type)} $n") }
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here yet",
                    body = "Find something in Discover and it shows up here.",
                    action = { Button(onClick = onFind) { Text("Find something") } }
                )
            }
        } else {
            items(shown, key = { it.id ?: (it.mediaType + it.externalId) }) { item ->
                LibraryCard(
                    item,
                    onClick = { onOpen(item.toDetail()) },
                    onBump = { onBump(item) },
                    onFinish = { onSetStatus(item, Status.WATCHED) },
                    modifier = Modifier.animateItem().padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/** Compact card for things you are in the middle of, with a +1 episode / chapter button. */
@Composable
private fun ContinueCard(item: LibraryItem, onClick: () -> Unit, onBump: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.width(290.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Poster(item.posterUrl, Modifier.width(56.dp).height(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    item.progressLabel() ?: "Not started",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            FilledTonalIconButton(onClick = onBump) {
                Icon(Icons.Filled.Add, if (item.type == MediaType.MANGA) "Next chapter" else "Next episode")
            }
        }
    }
}

@Composable
private fun LibraryCard(
    item: LibraryItem,
    onClick: () -> Unit,
    onBump: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = item.statusEnum
    val square = item.type == MediaType.MUSIC
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // colored edge shows the status at a glance
            Box(Modifier.width(5.dp).fillMaxHeight().background(statusColor(s)))
            Row(
                Modifier
                    .weight(1f)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Poster(item.posterUrl, Modifier.width(if (square) 80.dp else 66.dp).height(if (square) 80.dp else 98.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .height(if (square) 80.dp else 98.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            item.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            listOfNotNull(item.type.label, item.year?.takeIf { it.isNotBlank() }).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(s.labelFor(item.type), statusColor(s))
                        item.rating?.takeIf { it > 0 }?.let {
                            Icon(Icons.Filled.Star, null, tint = Brand.Amber, modifier = Modifier.size(14.dp))
                            Text("$it/10", style = MaterialTheme.typography.labelMedium)
                        }
                        item.progressLabel()?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (s == Status.WATCHING && !item.type.isSingle) {
                    FilledTonalIconButton(onClick = onBump, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Filled.Add, "Next")
                    }
                } else if (item.type.isSingle && s == Status.PLANNED) {
                    FilledTonalIconButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Filled.Check, "Mark done")
                    }
                }
            }
        }
    }
}
