package hu.scorex.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun PitchLineupView(
    formation: String,
    players: List<LineupPlayerItem>,
    teamName: String,
    isHome: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isHome) GoldAccent else CyanAccent
    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF2E8B4A), Color(0xFF1A5C2E), Color(0xFF0F3D1F), Color(0xFF1A5C2E))
    )

    Column(modifier = modifier) {
        Text(
            text = "$teamName  •  $formation",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgBrush)
        ) {
            val w = maxWidth
            val h = maxHeight

            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw = size.width
                val sh = size.height
                val stroke = 1.6f
                drawRect(Color.White.copy(alpha = 0.28f), size = size, style = Stroke(stroke))
                drawLine(Color.White.copy(alpha = 0.22f), Offset(sw / 2, 0f), Offset(sw / 2, sh), strokeWidth = stroke)
                drawCircle(Color.White.copy(alpha = 0.25f), radius = min(sw, sh) * 0.13f, center = Offset(sw / 2, sh / 2), style = Stroke(stroke))
                // Center spot
                drawCircle(Color.White.copy(alpha = 0.4f), radius = 3f, center = Offset(sw / 2, sh / 2))
                val penW = sw * 0.52f
                val penH = sh * 0.16f
                drawRect(Color.White.copy(alpha = 0.2f), topLeft = Offset((sw - penW) / 2, 0f), size = Size(penW, penH), style = Stroke(stroke))
                drawRect(Color.White.copy(alpha = 0.2f), topLeft = Offset((sw - penW) / 2, sh - penH), size = Size(penW, penH), style = Stroke(stroke))
            }

            val positions = buildFormationPositions(formation, players.size)
            positions.forEachIndexed { index, (xFrac, yFrac) ->
                val player = players.getOrNull(index) ?: return@forEachIndexed
                PlayerMarker(
                    player = player,
                    accent = accent,
                    modifier = Modifier
                        .offset(
                            x = w * xFrac - 24.dp,
                            y = h * yFrac - 18.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun PlayerMarker(player: LineupPlayerItem, accent: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent, accent.copy(alpha = 0.7f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = player.number.ifBlank { "•" },
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = player.name.split(" ").lastOrNull()?.take(9) ?: player.name.take(9),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun buildFormationPositions(formation: String, count: Int): List<Pair<Float, Float>> {
    val parts = formation.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.isEmpty()) {
        return (0 until minOf(count, 11)).map { i ->
            Pair(0.18f + (i % 4) * 0.21f, 0.18f + (i / 4) * 0.20f)
        }
    }

    val positions = mutableListOf<Pair<Float, Float>>()
    positions.add(Pair(0.5f, 0.90f)) // GK

    val lineCount = parts.size
    parts.forEachIndexed { lineIdx, numInLine ->
        val y = 0.74f - (lineIdx * (0.58f / maxOf(lineCount - 1, 1)))
        for (i in 0 until numInLine) {
            val x = 0.12f + (i + 0.5f) * (0.76f / numInLine)
            positions.add(Pair(x, y))
        }
    }
    return positions.take(count)
}
