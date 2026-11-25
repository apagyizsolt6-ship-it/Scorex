package hu.scorex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveMatchCard(
    match: ScorexMatch,
    hasSavedTip: Boolean = false,
    hasFavoriteTeam: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isFinished = isMatchFinished(match)
    val isLive = isMatchLive(match)

    val statusText = when {
        isLive -> match.matchTime.ifBlank { "ÉLŐ" }
        isFinished -> "FT"
        else -> match.matchTime.ifBlank { match.date.takeLast(5) }
    }

    val statusColor = when {
        isLive -> AccentColor
        isFinished -> SecondaryColor
        else -> CyanAccent
    }

    val scoreColor = if (isLive || isFinished) ScoreWhite else SecondaryColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GlassSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status / Time
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (hasFavoriteTeam) {
                Text("♥", color = GoldAccent, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.width(10.dp))

        // Teams + Score
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.homeTeam,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = match.homeScore.ifBlank { "-" },
                    color = scoreColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.awayTeam,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = match.awayScore.ifBlank { "-" },
                    color = scoreColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Tip indicator
        if (hasSavedTip) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyanAccent.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text("TIPP", color = CyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
