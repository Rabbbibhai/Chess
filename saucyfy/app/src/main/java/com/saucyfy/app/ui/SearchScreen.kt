package com.saucyfy.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saucyfy.app.UiState
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.MediaResult
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.Status
import com.saucyfy.app.data.isSingle
import com.saucyfy.app.data.labelFor
import com.saucyfy.app.data.plural
import com.saucyfy.app.data.statusEnum
import com.saucyfy.app.data.toDetail

@Composable
fun SearchScreen(
    ui: UiState,
    onQuery: (String) -> Unit,
    onFilter: (MediaType?) -> Unit,
    onOpen: (Detail) -> Unit,
    onQuickSave: (Detail, Status) -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val sugg = ui.suggestion
    val corrected = ui.corrected
    val filters: List<Pair<String, MediaType?>> =
        listOf<Pair<String, MediaType?>>("All" to null) + MediaType.entries.map { it.plural to it }

    Column(modifier) {
        Text(
            "Discover",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp)
        )
        OutlinedTextField(
            value = ui.query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Search movies, shows, anime, manga, music") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (label, t) ->
                FilterChip(selected = ui.filter == t, onClick = { onFilter(t) }, label = { Text(label) })
            }
        }
        if (ui.searching && ui.query.isNotBlank()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        when {
            ui.query.isBlank() -> Discover(ui, onOpen)
            ui.results.isEmpty() && ui.searching ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.results.isEmpty() && sugg != null -> EmptyState(
                title = "No results for “${ui.query.trim()}”",
                body = "Did you mean “$sugg”?",
                action = { Button(onClick = { onQuery(sugg) }) { Text("Search “$sugg”") } }
            )
            ui.results.isEmpty() -> EmptyState("No matches", "Check the spelling or try another filter.")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (corrected != null) {
                    item {
                        Text(
                            "No results for “${ui.query.trim()}” - showing results for “$corrected”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(ui.results) { r ->
                    val inLib = ui.library.firstOrNull { it.mediaType == r.type.key && it.externalId == r.externalId }
                    ResultRow(
                        r,
                        inLib,
                        onClick = { onOpen(r.toDetail()) },
                        onQuickSave = { st -> onQuickSave(r.toDetail(), st) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Discover(ui: UiState, onOpen: (Detail) -> Unit) {
    val f = ui.filter
    val popular = ui.trending.filter { f == null || it.type == f }
    val anime = if (f == null || f == MediaType.ANIME) ui.topAnime else emptyList()
    val manga = if (f == null || f == MediaType.MANGA) ui.topManga else emptyList()
    val tracks = if (f == null || f == MediaType.MUSIC) ui.topTracks else emptyList()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (popular.isNotEmpty()) PosterRow("Popular now", popular, onOpen)
        if (anime.isNotEmpty()) PosterRow("Top airing anime", anime, onOpen)
        if (manga.isNotEmpty()) PosterRow("Top manga", manga, onOpen)
        if (tracks.isNotEmpty()) PosterRow("Top tracks", tracks, onOpen, square = true)
        if (popular.isEmpty() && anime.isEmpty() && manga.isEmpty() && tracks.isEmpty()) {
            EmptyState("Search to get started", "Find a title, then save it to your library.")
        }
    }
}

@Composable
private fun PosterRow(title: String, list: List<MediaResult>, onOpen: (Detail) -> Unit, square: Boolean = false) {
    SectionHeader(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { r ->
            Column(
                Modifier
                    .width(124.dp)
                    .clickable { onOpen(r.toDetail()) },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Poster(r.posterUrl, Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 2f / 3f))
                    ScoreBadge(r.score, Modifier.align(Alignment.BottomStart).padding(6.dp))
                }
                Text(
                    r.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    r: MediaResult,
    inLibrary: LibraryItem?,
    onClick: () -> Unit,
    onQuickSave: (Status) -> Unit
) {
    val single = r.type.isSingle
    val posterHeight = if (r.type == MediaType.MUSIC) 72.dp else 108.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                Poster(r.posterUrl, Modifier.width(72.dp).height(posterHeight))
                ScoreBadge(r.score, Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    r.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    listOf(r.type.label, r.year).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (r.overview.isNotBlank()) {
                    Text(
                        r.overview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (single) {
                    SingleActions(r.type, inLibrary?.statusEnum, onQuickSave, Modifier.padding(top = 4.dp))
                } else if (inLibrary != null) {
                    Pill(inLibrary.statusEnum.labelFor(r.type), statusColor(inLibrary.statusEnum))
                }
            }
            if (!single && inLibrary == null) {
                Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
