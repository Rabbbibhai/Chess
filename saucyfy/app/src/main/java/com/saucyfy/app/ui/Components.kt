package com.saucyfy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saucyfy.app.data.MediaType
import com.saucyfy.app.data.Status
import com.saucyfy.app.data.labelFor

fun statusColor(s: Status): Color = when (s) {
    Status.WATCHING -> Brand.Sky
    Status.WATCHED -> Brand.Grass
    Status.PLANNED -> Brand.Amber
    Status.DROPPED -> Brand.Coral
}

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Section heading: small accent bar + the theme's header color. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val header = MaterialTheme.typography.headlineMedium.color
    Row(modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = header, fontWeight = FontWeight.Bold)
    }
}

/** Small "star 7.8" chip that sits on top of a poster. */
@Composable
fun ScoreBadge(score: Double?, modifier: Modifier = Modifier) {
    if (score == null || score <= 0.0) return
    Row(
        modifier
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Star, null, tint = Brand.Amber, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            "%.1f".format(score),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/** Movies and songs: one tap to put it on "To watch / To listen" or mark it done. */
@Composable
fun SingleActions(type: MediaType, current: Status?, onPick: (Status) -> Unit, modifier: Modifier = Modifier) {
    val pad = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Status.PLANNED, Status.WATCHED).forEach { s ->
            val selected = current == s
            if (selected) {
                Button(
                    onClick = { onPick(s) },
                    contentPadding = pad,
                    modifier = Modifier.height(34.dp)
                ) { Text("✓ " + s.labelFor(type), style = MaterialTheme.typography.labelMedium) }
            } else {
                OutlinedButton(
                    onClick = { onPick(s) },
                    contentPadding = pad,
                    modifier = Modifier.height(34.dp)
                ) { Text(s.labelFor(type), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
fun InfoCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        action?.invoke()
    }
}
