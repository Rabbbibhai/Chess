package com.saucyfy.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saucyfy.app.BuildConfig
import com.saucyfy.app.R
import com.saucyfy.app.data.AppUpdate

/**
 * Full-screen splash. [isNewVersion] = true: "Update available" with an Update button.
 * false: "What's new" shown once after you updated.
 */
@Composable
fun UpdateOverlay(
    update: AppUpdate,
    isNewVersion: Boolean,
    progress: Float?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val lines = update.notes.lines()
        .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
        .filter { it.isNotBlank() }

    Surface(color = scheme.background, contentColor = scheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.background)))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                Image(
                    painterResource(R.drawable.logo),
                    contentDescription = "Elog",
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(22.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isNewVersion) "Update available" else "What's new",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(8.dp))
                Pill("Elog ${update.versionName}", scheme.primary)
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceContainer,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        lines.forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(scheme.primary)
                                )
                                Text(line, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Downloading ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Button(
                        onClick = if (isNewVersion) onUpdate else onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { Text(if (isNewVersion) "Update now" else "Got it", fontWeight = FontWeight.SemiBold) }
                    if (isNewVersion && !update.mandatory) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }
            }
        }
    }
}

/** Admin only: publish the notes for the build you are running. Everyone on an older build gets the update splash. */
@Composable
fun AdminScreen(onBack: () -> Unit, onPublish: (name: String, notes: String, mandatory: Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("1.0.${BuildConfig.VERSION_CODE}") }
    var notes by remember { mutableStateOf("") }
    var mandatory by remember { mutableStateOf(false) }

    Surface(color = scheme.background, contentColor = scheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Admin", style = MaterialTheme.typography.headlineMedium)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = scheme.primaryContainer) {
                Text(
                    "You are running build ${BuildConfig.VERSION_CODE}. Publishing sends these notes to everyone on an older build, " +
                        "who then see the Update splash with a download button. Install the new build on your phone first, then publish.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Version name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(2000) },
                label = { Text("What's new (one change per line)") },
                minLines = 7,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = mandatory, onCheckedChange = { mandatory = it })
                Text("Force update (cannot be skipped)", style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { onPublish(name, notes, mandatory) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Publish update to everyone", fontWeight = FontWeight.SemiBold) }
            Text(
                "Publishing the same version again just edits its notes.",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
