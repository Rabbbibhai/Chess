package com.saucyfy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
fun App(vm: AppViewModel, onSignIn: () -> Unit) {
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
            ui.session == null -> LoginScreen(onSignIn)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val tabs = listOf(
                    "Library" to Icons.Filled.Home,
                    "Discover" to Icons.Filled.Search,
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
        when (tab) {
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
            else -> ProfileScreen(ui = ui, onSignOut = { vm.signOut() }, modifier = m)
        }
    }

    detail?.let { d ->
        val existing = ui.library.firstOrNull { it.mediaType == d.type.key && it.externalId == d.externalId }
        DetailSheet(
            d = d,
            existing = existing,
            onDismiss = { detail = null },
            onSave = { st, r, s, e, n -> vm.save(d, st, r, s, e, n) { detail = null } },
            onRemove = { existing?.let { vm.remove(it) { detail = null } } }
        )
    }
}
