package com.saucyfy.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saucyfy.app.UiState
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.Status
import com.saucyfy.app.data.progressLabel
import com.saucyfy.app.data.statusEnum
import com.saucyfy.app.data.toDetail
import com.saucyfy.app.data.type

@Composable
fun LibraryScreen(
    ui: UiState,
    onOpen: (Detail) -> Unit,
    onBump: (LibraryItem) -> Unit,
    onRefresh: () -> Unit,
    onFind: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = ui.library.filter { filter == null || it.status == filter }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${ui.library.size} titles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("All ${ui.library.size}") }
            )
            Status.entries.forEach { s ->
                val n = ui.library.count { it.status == s.key }
                FilterChip(
                    selected = filter == s.key,
                    onClick = { filter = s.key },
                    label = { Text("${s.label} $n") }
                )
            }
        }

        when {
            ui.libLoading && ui.library.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            shown.isEmpty() -> EmptyState(
                title = if (ui.library.isEmpty()) "Your library is empty" else "Nothing here yet",
                body = "Search for a movie, show or anime and save it with a rating or where you left off.",
                action = { Button(onClick = onFind) { Text("Find something") } }
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id ?: (it.mediaType + it.externalId) }) { item ->
                    LibraryCard(item, onClick = { onOpen(item.toDetail()) }, onBump = { onBump(item) })
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(item: LibraryItem, onClick: () -> Unit, onBump: () -> Unit) {
    val s = item.statusEnum
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Poster(item.posterUrl, Modifier.width(66.dp).height(98.dp))
            Column(
                Modifier
                    .weight(1f)
                    .height(98.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        listOfNotNull(item.type.label, item.year?.takeIf { it.isNotBlank() }).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(s.label, statusColor(s))
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
            if (s == Status.WATCHING && item.type != MediaType.MOVIE) {
                FilledTonalIconButton(onClick = onBump, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(Icons.Filled.Add, "Next episode")
                }
            }
        }
    }
}
