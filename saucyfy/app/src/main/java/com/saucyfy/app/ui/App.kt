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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saucyfy.app.AppViewModel
import com.saucyfy.app.UiState
import com.saucyfy.app.data.Detail

@Composable
fun App(vm: AppViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            ui.booting -> Unit
            ui.session == null -> LoginScreen(busy = ui.authBusy, onSubmit = { e, p, up -> vm.authenticate(e, p, up) })
            else -> MainScaffold(vm, ui)
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

@Composable
private fun MainScaffold(vm: AppViewModel, ui: UiState) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setAvatar(uri)
    }

    LaunchedEffect(tab) {
        if (tab == 2) vm.loadFriends()
    }
    // Back from a friend's list returns to the Friends list.
    BackHandler(enabled = tab == 2 && ui.viewingFriend != null) { vm.closeFriend() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val tabs = listOf(
                    "Library" to Icons.Filled.Home,
                    "Discover" to Icons.Filled.Search,
                    "Friends" to Icons.Filled.Favorite,
                    "Profile" to Icons.Filled.Person
                )
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, label) },
                        label = { Text(label) }
                    )
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
                    onRefresh = { vm.refreshLibrary() },
                    onFind = { tab = 1 },
                    modifier = m
                )
                1 -> SearchScreen(
                    ui = ui,
                    onQuery = { vm.onQuery(it) },
                    onFilter = { vm.setFilter(it) },
                    onOpen = { detail = it },
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
                else -> ProfileScreen(
                    ui = ui,
                    onSignOut = { vm.signOut() },
                    onPickAvatar = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onEditProfile = { n, u -> vm.editProfile(n, u) },
                    onTheme = { vm.setTheme(it) },
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
