package com.saucyfy.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saucyfy.app.AppViewModel
import com.saucyfy.app.UiState
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.toDetail

@Composable
fun App(vm: AppViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scheme = MaterialTheme.colorScheme

    // Only check for new messages / updates while the app is on screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.setForeground(true)
            else if (event == Lifecycle.Event.ON_STOP) vm.setForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Surface gives every Text the right default color (this is what fixes unreadable headers).
    Surface(color = scheme.background, contentColor = scheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                // soft theme-colored glow at the top of every screen
                .background(
                    Brush.verticalGradient(
                        0f to scheme.primaryContainer.copy(alpha = 0.30f),
                        0.45f to Color.Transparent
                    )
                )
        ) {
            when {
                ui.booting -> Unit
                ui.session == null -> LoginScreen(busy = ui.authBusy, onSubmit = { e, p, up -> vm.authenticate(e, p, up) })
                else -> MainScaffold(vm, ui)
            }

            if (ui.adminOpen && ui.isAdmin) {
                BackHandler { vm.closeAdmin() }
                AdminScreen(
                    onBack = { vm.closeAdmin() },
                    onPublish = { n, notes, force -> vm.publishUpdate(n, notes, force) }
                )
            }

            // Update splash (newer version exists) or one-time "What's new" after updating.
            val prompt = ui.updatePrompt
            val news = ui.whatsNew
            if (prompt != null) {
                BackHandler(enabled = prompt.mandatory) { }
                UpdateOverlay(
                    update = prompt,
                    isNewVersion = true,
                    progress = ui.updateProgress,
                    onUpdate = { vm.startUpdate() },
                    onDismiss = { vm.dismissUpdate() }
                )
            } else if (news != null && !ui.booting) {
                UpdateOverlay(
                    update = news,
                    isNewVersion = false,
                    progress = null,
                    onUpdate = { },
                    onDismiss = { vm.dismissWhatsNew() }
                )
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
            )
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, ui: UiState) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    val totalUnread = ui.unread.values.sum()
    val chatOpen = tab == 3 && ui.chatWith != null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setAvatar(uri)
    }

    LaunchedEffect(tab) {
        if (tab == 2 || tab == 3) vm.loadFriends()
    }
    // Back from a chat / a friend's list returns to the list.
    BackHandler(enabled = chatOpen) { vm.closeChat() }
    BackHandler(enabled = tab == 2 && ui.viewingFriend != null) { vm.closeFriend() }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            if (!chatOpen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    val tabs = listOf(
                        "Library" to Icons.Filled.Home,
                        "Discover" to Icons.Filled.Search,
                        "Friends" to Icons.Filled.Favorite,
                        "Chats" to Icons.Filled.Email,
                        "Profile" to Icons.Filled.Person
                    )
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                if (i == 3 && totalUnread > 0) {
                                    BadgedBox(badge = { Badge { Text("$totalUnread") } }) { Icon(icon, label) }
                                } else {
                                    Icon(icon, label)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        val m = Modifier.padding(pad).fillMaxSize()
        Crossfade(targetState = tab, label = "tab") { t ->
            when (t) {
                0 -> LibraryScreen(
                    ui = ui,
                    onOpen = { detail = it },
                    onBump = { vm.bumpEpisode(it) },
                    onSetStatus = { item, st ->
                        vm.save(item.toDetail(), st, item.rating, item.season, item.episode, item.notes) { }
                    },
                    onRefresh = { vm.refreshLibrary() },
                    onFind = { tab = 1 },
                    modifier = m
                )
                1 -> SearchScreen(
                    ui = ui,
                    onQuery = { vm.onQuery(it) },
                    onFilter = { vm.setFilter(it) },
                    onOpen = { detail = it },
                    onQuickSave = { d, st -> vm.save(d, st, null, null, null, null) { } },
                    modifier = m
                )
                2 -> {
                    val vf = ui.viewingFriend
                    if (vf != null) {
                        FriendLibraryScreen(
                            friend = vf,
                            items = ui.friendItems,
                            loading = ui.friendItemsLoading,
                            onBack = { vm.closeFriend() },
                            onOpen = { detail = it },
                            onUnfriend = {
                                ui.friends.firstOrNull { it.profile.id == vf.id }?.let { vm.removeFriend(it) }
                            },
                            modifier = m
                        )
                    } else {
                        FriendsScreen(
                            ui = ui,
                            onSearch = { vm.searchUsers(it) },
                            onAdd = { vm.addFriend(it) },
                            onAccept = { vm.acceptFriend(it) },
                            onRemove = { vm.removeFriend(it) },
                            onOpen = { vm.openFriend(it) },
                            onRefresh = { vm.loadFriends() },
                            modifier = m
                        )
                    }
                }
                3 -> {
                    val cw = ui.chatWith
                    if (cw != null) {
                        ChatScreen(
                            friend = cw,
                            msgs = ui.chats[cw.id] ?: emptyList(),
                            gifs = ui.gifs,
                            gifsLoading = ui.gifsLoading,
                            gifCategory = ui.gifCategory,
                            onBack = { vm.closeChat() },
                            onSend = { vm.sendMessage(cw, it) },
                            onLoadGifs = { vm.loadGifs(it) },
                            modifier = m
                        )
                    } else {
                        ChatListScreen(ui = ui, onOpen = { vm.openChat(it) }, modifier = m)
                    }
                }
                else -> ProfileScreen(
                    ui = ui,
                    onSignOut = { vm.signOut() },
                    onPickAvatar = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onEditProfile = { n, u -> vm.editProfile(n, u) },
                    onTheme = { vm.setTheme(it) },
                    onOpenAdmin = { vm.openAdmin() },
                    modifier = m
                )
            }
        }
    }

    detail?.let { d ->
        LaunchedEffect(d.type, d.externalId) { vm.loadLimits(d) }
        val existing = ui.library.firstOrNull { it.mediaType == d.type.key && it.externalId == d.externalId }
        DetailSheet(
            d = d,
            existing = existing,
            limits = ui.limits[d.type.key + ":" + d.externalId],
            onDismiss = { detail = null },
            onSave = { st, r, s, e, n -> vm.save(d, st, r, s, e, n) { detail = null } },
            onRemove = { existing?.let { vm.remove(it) { detail = null } } }
        )
    }
}
