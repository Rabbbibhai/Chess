package com.saucyfy.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saucyfy.app.UiState
import com.saucyfy.app.data.ChatMsg
import com.saucyfy.app.data.GifCategories
import com.saucyfy.app.data.Profile
import com.saucyfy.app.data.gifUrlOrNull
import com.saucyfy.app.data.shownName

@Composable
fun ChatListScreen(ui: UiState, onOpen: (Profile) -> Unit, modifier: Modifier = Modifier) {
    val friends = ui.friends.filter { it.accepted }.map { it.profile }
    val sorted = friends.sortedByDescending { ui.chats[it.id]?.lastOrNull()?.ts ?: 0L }

    Column(modifier) {
        Text(
            "Chats",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp)
        )
        Text(
            "Messages are saved on your phone, not on the server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )
        if (sorted.isEmpty()) {
            EmptyState(
                if (ui.friendsLoading) "Loading..." else "No chats yet",
                "Add friends first, then message them here."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sorted, key = { it.id }) { p ->
                    ChatRow(p, ui.chats[p.id]?.lastOrNull(), ui.unread[p.id] ?: 0) { onOpen(p) }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(p: Profile, last: ChatMsg?, unread: Int, onClick: () -> Unit) {
    val preview = when {
        last == null -> "Say hi!"
        gifUrlOrNull(last.body) != null -> (if (last.mine) "You: " else "") + "GIF"
        else -> (if (last.mine) "You: " else "") + last.body
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(p.avatarUrl, p.shownName, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(p.shownName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (unread > 0) Badge { Text("$unread") }
        }
    }
}

@Composable
fun ChatScreen(
    friend: Profile,
    msgs: List<ChatMsg>,
    gifs: List<String>,
    gifsLoading: Boolean,
    gifCategory: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onLoadGifs: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var showGifs by remember { mutableStateOf(false) }

    Column(modifier.imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Avatar(friend.avatarUrl, friend.shownName, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(friend.shownName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "@${friend.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(msgs.asReversed(), key = { it.id }) { m -> Bubble(m) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(onClick = { showGifs = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("GIF", fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                placeholder = { Text("Message") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                enabled = text.isNotBlank()
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }

    if (showGifs) {
        GifSheet(
            gifs = gifs,
            loading = gifsLoading,
            category = gifCategory,
            onLoad = onLoadGifs,
            onPick = {
                onSend(it)
                showGifs = false
            },
            onDismiss = { showGifs = false }
        )
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val gif = gifUrlOrNull(m.body)
    val mine = m.mine
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = shape,
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (gif != null) {
                AsyncImage(
                    model = gif,
                    contentDescription = "GIF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(220.dp)
                        .height(170.dp)
                        .clip(shape)
                )
            } else {
                Text(
                    m.body,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GifSheet(
    gifs: List<String>,
    loading: Boolean,
    category: String,
    onLoad: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { if (gifs.isEmpty()) onLoad(category) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GifCategories) { c ->
                    FilterChip(selected = c == category, onClick = { onLoad(c) }, label = { Text(c) })
                }
            }
            Box(Modifier.fillMaxWidth().height(380.dp)) {
                if (loading && gifs.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (gifs.isEmpty()) {
                    Text(
                        "Couldn't load GIFs. Check your connection.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(gifs) { g ->
                            AsyncImage(
                                model = g,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick("gif:$g") }
                            )
                        }
                    }
                }
            }
        }
    }
}
