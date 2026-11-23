package com.saucyfy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saucyfy.app.UiState
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.FriendEntry
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.Profile
import com.saucyfy.app.data.Status
import com.saucyfy.app.data.progressLabel
import com.saucyfy.app.data.shownName
import com.saucyfy.app.data.statusEnum
import com.saucyfy.app.data.toDetail
import com.saucyfy.app.data.type

@Composable
fun Avatar(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PersonRow(p: Profile, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(p.avatarUrl, p.shownName, 46.dp)
            Column(Modifier.weight(1f)) {
                Text(p.shownName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "@${p.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
    )
}

@Composable
fun FriendsScreen(
    ui: UiState,
    onSearch: (String) -> Unit,
    onAdd: (Profile) -> Unit,
    onAccept: (FriendEntry) -> Unit,
    onRemove: (FriendEntry) -> Unit,
    onOpen: (Profile) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val byId = ui.friends.associateBy { it.profile.id }
    val requests = ui.friends.filter { !it.accepted && it.incoming }
    val friends = ui.friends.filter { it.accepted }
    val sent = ui.friends.filter { !it.accepted && !it.incoming }
    val searching = ui.userQuery.trim().length >= 2

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Friends", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                ui.myUsername?.let {
                    Text(
                        "Your username: @$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
        }

        OutlinedTextField(
            value = ui.userQuery,
            onValueChange = onSearch,
            singleLine = true,
            placeholder = { Text("Find people by username") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (ui.userQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searching) {
                if (ui.userSearching && ui.userResults.isEmpty()) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                } else if (ui.userResults.isEmpty()) {
                    item { EmptyState("No one found", "Check the username and try again.") }
                }
                items(ui.userResults, key = { it.id }) { p ->
                    val e = byId[p.id]
                    PersonRow(p, onClick = if (e?.accepted == true) ({ onOpen(p) }) else null) {
                        when {
                            e == null -> FilledTonalButton(onClick = { onAdd(p) }) { Text("Add") }
                            e.accepted -> Pill("Friends", Brand.Grass)
                            e.incoming -> FilledTonalButton(onClick = { onAccept(e) }) { Text("Accept") }
                            else -> Pill("Requested", Brand.Amber)
                        }
                    }
                }
            } else {
                if (requests.isNotEmpty()) {
                    item { SectionLabel("Requests") }
                    items(requests, key = { it.friendshipId }) { e ->
                        PersonRow(e.profile, onClick = null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(onClick = { onAccept(e) }) { Icon(Icons.Filled.Check, "Accept") }
                                IconButton(onClick = { onRemove(e) }) { Icon(Icons.Filled.Close, "Decline") }
                            }
                        }
                    }
                }
                item { SectionLabel("Friends ${friends.size}") }
                if (friends.isEmpty()) {
                    item {
                        EmptyState(
                            if (ui.friendsLoading) "Loading..." else "No friends yet",
                            "Search a username above to add someone, then you can see each other's lists."
                        )
                    }
                }
                items(friends, key = { it.friendshipId }) { e ->
                    PersonRow(e.profile, onClick = { onOpen(e.profile) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
                if (sent.isNotEmpty()) {
                    item { SectionLabel("Sent requests") }
                    items(sent, key = { it.friendshipId }) { e ->
                        PersonRow(e.profile, onClick = null) {
                            TextButton(onClick = { onRemove(e) }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendLibraryScreen(
    friend: Profile,
    items: List<LibraryItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onOpen: (Detail) -> Unit,
    onUnfriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val shown = items.filter { filter == null || it.status == filter }
    val rated = items.mapNotNull { it.rating }.filter { it > 0 }
    val avg = if (rated.isEmpty()) "-" else "%.1f".format(rated.average())

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove friend?") },
            text = { Text("You and ${friend.shownName} will no longer see each other's lists.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onUnfriend()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Avatar(friend.avatarUrl, friend.shownName, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(friend.shownName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "@${friend.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { confirm = true }) { Text("Unfriend", color = MaterialTheme.colorScheme.error) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MiniStat("${items.count { it.status == Status.WATCHED.key }}", "Finished", Brand.Grass)
            MiniStat("${items.count { it.status == Status.WATCHING.key }}", "Watching", Brand.Sky)
            MiniStat("${items.count { it.status == Status.PLANNED.key }}", "To watch", Brand.Amber)
            MiniStat(avg, "Avg rating", Brand.Coral)
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All ${items.size}") })
            Status.entries.forEach { s ->
                val n = items.count { it.status == s.key }
                FilterChip(selected = filter == s.key, onClick = { filter = s.key }, label = { Text("${s.label} $n") })
            }
        }

        when {
            loading && items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            shown.isEmpty() -> EmptyState("Nothing here", "${friend.shownName} hasn't added anything in this section.")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id ?: (it.mediaType + it.externalId) }) { item ->
                    FriendItemCard(item, onClick = { onOpen(item.toDetail()) })
                }
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Read-only card; tapping opens the sheet so you can add it to your own library. */
@Composable
private fun FriendItemCard(item: LibraryItem, onClick: () -> Unit) {
    val s = item.statusEnum
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Poster(item.posterUrl, Modifier.width(60.dp).height(90.dp))
            Column(
                Modifier
                    .weight(1f)
                    .height(90.dp),
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
        }
    }
}
