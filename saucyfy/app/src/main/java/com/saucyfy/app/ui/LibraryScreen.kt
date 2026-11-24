package com.saucyfy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val tObj: MediaType? = typeFilter?.let { MediaType.from(it) }
    val base = ui.library.filter { typeFilter == null || it.mediaType == typeFilter }
    val shown = base.filter { filter == null || it.status == filter }
    val inProgress = base.filter { it.status == Status.WATCHING.key && !it.type.isSingle }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
        // what kind of media
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("Everything") })
                MediaType.entries.forEach { t ->
                    FilterChip(
                        selected = typeFilter == t.key,
                        onClick = { typeFilter = t.key },
                        label = { Text(t.plural) }
                    )
                }
            }
        }
        // which status
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All ${base.size}") })
                Status.entries.forEach { s ->
                    val n = base.count { it.status == s.key }
                    val name = if (tObj != null) s.labelFor(tObj) else s.label
                    FilterChip(selected = filter == s.key, onClick = { filter = s.key }, label = { Text("$name $n") })
                }
            }
        }
        if (filter == null && inProgress.isNotEmpty()) {
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
        if (ui.libLoading && ui.library.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = if (ui.library.isEmpty()) "Your library is empty" else "Nothing here yet",
                    body = "Search for a movie, show, anime, manga or song and save it with a rating or where you left off.",
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
