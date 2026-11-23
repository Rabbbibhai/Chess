package com.saucyfy.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saucyfy.app.ui.App
import com.saucyfy.app.ui.SaucyfyTheme
import com.saucyfy.app.ui.themeById

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (savedInstanceState == null) handle(intent)
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val dark = themeById(ui.themeId).dark
            // Keep status / navigation bar icons readable in light and dark themes.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SaucyfyTheme(ui.themeId) {
                App(vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    // Email confirmation links open saucyfy://login-callback#access_token=...
    private fun handle(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme == "saucyfy") {
            vm.handleRedirect(d.fragment ?: d.encodedQuery ?: "")
        }
    }
}
