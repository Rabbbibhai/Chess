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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.saucyfy.app.data.Cfg
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.MediaResult
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.statusEnum
import com.saucyfy.app.data.toDetail

@Composable
fun SearchScreen(
    ui: UiState,
    onQuery: (String) -> Unit,
    onFilter: (MediaType?) -> Unit,
    onOpen: (Detail) -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val filters = listOf<Pair<String, MediaType?>>(
        "All" to null,
        "Movies" to MediaType.MOVIE,
        "Shows" to MediaType.TV,
        "Anime" to MediaType.ANIME
    )

    Column(modifier) {
        Text(
            "Discover",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp)
        )
        OutlinedTextField(
            value = ui.query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Search movies, shows, anime") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            shape = RoundedCornerShape(16.dp),
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

        when {
            ui.query.isBlank() -> Discover(ui, onOpen)
            ui.searching && ui.results.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.results.isEmpty() -> EmptyState("No matches", "Try a different spelling or another filter.")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ui.results) { r ->
                    val inLib = ui.library.firstOrNull { it.mediaType == r.type.key && it.externalId == r.externalId }
                    ResultRow(r, inLib, onClick = { onOpen(r.toDetail()) })
                }
            }
        }
    }
}

@Composable
private fun Discover(ui: UiState, onOpen: (Detail) -> Unit) {
    val trending = ui.trending.filter { ui.filter == null || it.type == ui.filter }
    val anime = if (ui.filter == null || ui.filter == MediaType.ANIME) ui.topAnime else emptyList()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (!Cfg.tmdbKey.isNotBlank()) {
            InfoCard("Movie and show search needs a free TMDB API key (see README). Anime search already works.")
        }
        if (trending.isNotEmpty()) PosterRow("Trending this week", trending, onOpen)
        if (anime.isNotEmpty()) PosterRow("Top airing anime", anime, onOpen)
        if (trending.isEmpty() && anime.isEmpty()) {
            EmptyState("Search to get started", "Find a title, then save it to your library.")
        }
    }
}

@Composable
private fun PosterRow(title: String, list: List<MediaResult>, onOpen: (Detail) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { r ->
            Column(
                Modifier
                    .width(120.dp)
                    .clickable { onOpen(r.toDetail()) },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Poster(r.posterUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
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
private fun ResultRow(r: MediaResult, inLibrary: LibraryItem?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Poster(r.posterUrl, Modifier.width(56.dp).height(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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
            }
            if (inLibrary != null) {
                Pill(inLibrary.statusEnum.label, statusColor(inLibrary.statusEnum))
            } else {
                Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
