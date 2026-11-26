package com.saucyfy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saucyfy.app.data.Detail
import com.saucyfy.app.data.LibraryItem
import com.saucyfy.app.data.Limits
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.Status
import com.saucyfy.app.data.isSingle
import com.saucyfy.app.data.labelFor
import com.saucyfy.app.data.statusEnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    d: Detail,
    existing: LibraryItem?,
    limits: Limits?,
    onDismiss: () -> Unit,
    onSave: (Status, Int?, Int?, Int?, String?) -> Unit,
    onRemove: () -> Unit
) {
    val type = d.type
    val single = type.isSingle
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by remember { mutableStateOf(existing?.statusEnum ?: Status.WATCHED) }
    var rating by remember { mutableIntStateOf(existing?.rating ?: 0) }
    var season by remember { mutableIntStateOf(if (type == MediaType.TV) existing?.season ?: 1 else 1) }
    var volume by remember { mutableIntStateOf(if (type == MediaType.MANGA) existing?.season ?: 0 else 0) }
    var episode by remember { mutableIntStateOf(existing?.episode ?: 0) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    // Real limits when we know them, sane ceilings while they load.
    val maxSeason = limits?.seasons?.keys?.maxOrNull() ?: 40
    fun epMax(s: Int): Int = when (type) {
        MediaType.TV -> limits?.seasons?.get(s) ?: 100
        MediaType.ANIME -> limits?.totalEpisodes ?: 3000
        MediaType.MANGA -> limits?.totalEpisodes ?: 5000
        MediaType.MOVIE, MediaType.MUSIC -> 0
    }
    val knownEp = (type == MediaType.TV && limits?.seasons != null) ||
        ((type == MediaType.ANIME || type == MediaType.MANGA) && limits?.totalEpisodes != null)
    val knownSeason = type == MediaType.TV && limits?.seasons != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box {
                    Poster(
                        d.posterUrl,
                        Modifier.width(if (type == MediaType.MUSIC) 120.dp else 104.dp)
                            .height(if (type == MediaType.MUSIC) 120.dp else 156.dp)
                    )
                    ScoreBadge(d.score, Modifier.align(Alignment.BottomStart).padding(5.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf(type.label, d.year).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (d.overview.isNotBlank()) {
                        Text(
                            d.overview,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!single) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Status.entries.forEach { s ->
                            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.labelFor(type)) })
                        }
                    }
                }
            }

            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Rating",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (rating == 0) "Not rated" else "★ $rating/10",
                        color = if (rating == 0) MaterialTheme.colorScheme.onSurfaceVariant else Brand.Amber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.roundToInt() },
                    valueRange = 0f..10f,
                    steps = 9
                )
            }

            if (single) {
                // Movies and songs: one tap, no status picker.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add to your list",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(Status.PLANNED, Status.WATCHED).forEach { s ->
                            val selected = existing?.statusEnum == s
                            val go = {
                                onSave(s, rating.takeIf { it > 0 }, null, null, notes.trim().ifBlank { null })
                            }
                            if (selected) {
                                Button(
                                    onClick = go,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f).height(52.dp)
                                ) { Text("✓ " + s.labelFor(type), fontWeight = FontWeight.SemiBold) }
                            } else {
                                OutlinedButton(
                                    onClick = go,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f).height(52.dp)
                                ) { Text(s.labelFor(type), fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Left off at",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (type == MediaType.TV) {
                        Stepper("Season", season.coerceAtMost(maxSeason), 1, maxSeason, knownSeason) {
                            season = it
                            episode = minOf(episode, epMax(it))
                        }
                    }
                    if (type == MediaType.MANGA) {
                        Stepper("Volume", volume, 0, 300, false) { volume = it }
                    }
                    Stepper(
                        if (type == MediaType.MANGA) "Chapter" else "Episode",
                        episode.coerceAtMost(epMax(season)),
                        0,
                        epMax(season),
                        knownEp
                    ) { episode = it }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            if (existing != null || !single) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (existing != null) {
                        OutlinedButton(
                            onClick = onRemove,
                            shape = RoundedCornerShape(16.dp),
                            modifier = if (single) Modifier.weight(1f).height(52.dp) else Modifier.height(52.dp)
                        ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    if (!single) {
                        Button(
                            onClick = {
                                val s = season.coerceIn(1, maxSeason)
                                val e = episode.coerceIn(0, epMax(s))
                                val seasonOrVolume: Int? = when (type) {
                                    MediaType.TV -> s
                                    MediaType.MANGA -> volume.takeIf { it > 0 }
                                    else -> null
                                }
                                onSave(
                                    status,
                                    rating.takeIf { it > 0 },
                                    seasonOrVolume,
                                    if (e > 0) e else null,
                                    notes.trim().ifBlank { null }
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) { Text(if (existing != null) "Update" else "Add to library", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

/** A round button that steps once on tap and keeps stepping (faster and faster) while you hold it. */
@Composable
private fun RepeatButton(enabled: Boolean, onStep: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    val bg = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    step()
                    val job = scope.launch {
                        delay(400)
                        var gap = 140L
                        while (true) {
                            step()
                            delay(gap)
                            if (gap > 35) gap -= 10
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Set a number fast: type it, tap + / - (hold to run), or jump with +5 / +10 / +50.
 */
@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, showMax: Boolean, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatButton(enabled = value > min, onStep = { onChange((value - 1).coerceAtLeast(min)) }) {
                Text("−", fontSize = 24.sp, color = tint)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { s ->
                    val digits = s.filter { it.isDigit() }.take(5)
                    text = digits
                    digits.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(92.dp)
            )
            RepeatButton(enabled = value < max, onStep = { onChange((value + 1).coerceAtMost(max)) }) {
                Icon(Icons.Filled.Add, "Increase", tint = tint)
            }
            if (showMax) {
                Text("/ $max", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (max >= 30) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 50).filter { it < max }.forEach { n ->
                    AssistChip(
                        onClick = { onChange((value + n).coerceAtMost(max)) },
                        label = { Text("+$n") }
                    )
                }
            }
        }
    }
}
