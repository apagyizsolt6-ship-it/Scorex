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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveMatchCard(
    match: ScorexMatch,
    hasSavedTip: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isFinished = isMatchFinished(match)
    val isLive = isMatchLive(match)

    val statusColor = when {
        isLive -> AccentColor
        isFinished -> SecondaryColor
        else -> CyanAccent
    }

    val scoreColor = when {
        isLive -> GoldAccent
        isFinished -> Color.White
        else -> AccentColor
    }

    GlassPanel(
        borderColor = statusColor.copy(alpha = if (isLive) 0.55f else 0.22f),
        gradientColors = if (isLive) {
            listOf(AccentColor.copy(alpha = 0.14f), GlassSurface)
        } else {
            listOf(GlassTop, GlassSurface)
        },
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translateLeagueName(match.league),
                    color = SecondaryColor,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasSavedTip) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GoldAccent.copy(alpha = 0.20f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TIPP",
                                color = GoldAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusColor.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isLive && !match.matchTime.contains("ÉLŐ", true)) {
                                "● ${match.matchTime}"
                            } else {
                                match.matchTime
                            },
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.homeTeam,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = match.awayTeam,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = match.homeScore,
                        color = scoreColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = match.awayScore,
                        color = scoreColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
