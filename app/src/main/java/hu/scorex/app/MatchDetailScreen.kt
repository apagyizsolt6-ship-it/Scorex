package hu.scorex.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MatchDetailScreen(
    match: ScorexMatch,
    preferences: SharedPreferences,
    savedTips: List<String>,
    onToggleTip: (String) -> Unit,
    onBack: () -> Unit
) {
    val live = isMatchLive(match)
    val finished = isMatchFinished(match)
    val translatedLeague = translateLeagueName(match.league)
    val leagueKey = match.league.ifBlank { "Ismeretlen bajnokság" }

    val homeKey = favoriteTeamKey(match.homeTeam)
    val awayKey = favoriteTeamKey(match.awayTeam)

    var isLeagueFavorite by remember(leagueKey, preferences) {
        mutableStateOf(
            leagueKey in (preferences.getStringSet("favorite_leagues", emptySet()).orEmpty())
        )
    }

    var favoriteTeams by remember(preferences) {
        mutableStateOf(
            preferences.getStringSet("favorite_teams", emptySet()).orEmpty().toSet()
        )
    }

    fun toggleTeamFavorite(teamName: String) {
        val key = favoriteTeamKey(teamName)
        if (key.isBlank()) return
        val updated = favoriteTeams.toMutableSet()
        if (key in updated) {
            updated.remove(key)
        } else {
            updated.add(key)
        }
        preferences.edit().putStringSet("favorite_teams", updated).apply()
        favoriteTeams = updated
    }

    val homeGoalsInt = match.homeScore.toIntOrNull()
    val awayGoalsInt = match.awayScore.toIntOrNull()

    val currentOutcomePrefix = if (homeGoalsInt != null && awayGoalsInt != null) {
        when {
            homeGoalsInt > awayGoalsInt -> "1 ("
            awayGoalsInt > homeGoalsInt -> "2 ("
            else -> "X ("
        }
    } else {
        null
    }

    val summaryText = when {
        homeGoalsInt != null && awayGoalsInt != null -> when {
            homeGoalsInt > awayGoalsInt -> "${match.homeTeam} vezet / áll ${match.homeScore}–${match.awayScore} arányban."
            awayGoalsInt > homeGoalsInt -> "${match.awayTeam} vezet / áll ${match.homeScore}–${match.awayScore} arányban."
            else -> "Kiegyenlített állás: ${match.homeScore}–${match.awayScore}."
        }
        finished -> "A mérkőzés véget ért (${match.homeScore} – ${match.awayScore})."
        live -> "A mérkőzés folyamatban van (${match.matchTime})."
        else -> "Még nem kezdődött el vagy frissítésre vár (${match.matchTime})."
    }

    val tipOptions = listOf(
        "1 (${match.homeTeam})",
        "X (Döntetlen)",
        "2 (${match.awayTeam})"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                Text(text = "← VISSZA", color = AccentColor, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val current = preferences.getStringSet("favorite_leagues", emptySet())
                        .orEmpty()
                        .toMutableSet()
                    if (isLeagueFavorite) {
                        current.remove(leagueKey)
                    } else {
                        current.add(leagueKey)
                    }
                    preferences.edit().putStringSet("favorite_leagues", current).apply()
                    isLeagueFavorite = !isLeagueFavorite
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isLeagueFavorite) "★ Kedvenc liga" else "☆ Kedvencnek",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        GlassPanel(
            borderColor = if (live) AccentColor.copy(alpha = 0.55f) else CyanAccent.copy(alpha = 0.30f),
            gradientColors = listOf(GlassTop, GlassSurface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = translatedLeague,
                    color = CyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (live) AccentColor.copy(alpha = 0.22f)
                                else CyanAccent.copy(alpha = 0.16f)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (live) "● ÉLŐ • ${match.matchTime}" else match.matchTime,
                            color = if (live) AccentColor else CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (match.date.isNotBlank()) {
                        Text(
                            text = match.date,
                            color = SecondaryColor,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = match.homeTeam,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${match.homeScore}  :  ${match.awayScore}",
                        color = GoldAccent,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    Text(
                        text = match.awayTeam,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isHomeFav = homeKey in favoriteTeams
                    val isAwayFav = awayKey in favoriteTeams

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isHomeFav) GoldAccent.copy(alpha = 0.18f) else GlassWell)
                            .border(
                                width = 1.dp,
                                color = if (isHomeFav) GoldAccent else Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { toggleTeamFavorite(match.homeTeam) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHomeFav) "♥ Hazai kedvenc" else "♡ Hazai mentése",
                            color = if (isHomeFav) GoldAccent else SecondaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isAwayFav) GoldAccent.copy(alpha = 0.18f) else GlassWell)
                            .border(
                                width = 1.dp,
                                color = if (isAwayFav) GoldAccent else Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { toggleTeamFavorite(match.awayTeam) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAwayFav) "♥ Vendég kedvenc" else "♡ Vendég mentése",
                            color = if (isAwayFav) GoldAccent else SecondaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text(
                    text = summaryText,
                    color = SecondaryColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        GlassPanel(
            borderColor = AccentColor.copy(alpha = 0.25f),
            gradientColors = listOf(GlassSurface, GlassWell)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MÉRKŐZÉS ADATAI",
                    color = AccentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                DiagnosticRow(label = "Eredeti bajnokság", value = match.league)
                DiagnosticRow(label = "Állapot / Kezdés (helyi)", value = match.matchTime, valueColor = AccentColor)
                if (match.rawKickoff.isNotBlank()) {
                    DiagnosticRow(label = "API kezdési idő (UTC)", value = match.rawKickoff)
                }
                if (match.rawStatus.isNotBlank()) {
                    DiagnosticRow(label = "API státuszkód", value = match.rawStatus)
                }
                if (match.date.isNotBlank()) {
                    DiagnosticRow(label = "Dátum", value = match.date)
                }
                DiagnosticRow(label = "Azonosító", value = match.id)
            }
        }

        GlassPanel(
            borderColor = GoldAccent.copy(alpha = 0.32f),
            gradientColors = listOf(GoldAccent.copy(alpha = 0.14f), GlassSurface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TIPP HOZZÁADÁSA A SZELVÉNYHEZ",
                    color = GoldAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                tipOptions.forEach { option ->
                    val fullTip = "${match.homeTeam} – ${match.awayTeam}: $option"
                    val isSaved = fullTip in savedTips
                    val isCurrentWinner = currentOutcomePrefix != null && option.startsWith(currentOutcomePrefix)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isSaved) GoldAccent.copy(alpha = 0.20f)
                                else GlassWell
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSaved) GoldAccent else Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(11.dp)
                            )
                            .clickable { onToggleTip(fullTip) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option,
                                color = if (isSaved) GoldAccent else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isCurrentWinner) {
                                Text(
                                    text = if (finished) "Eredmény alapján nyerő" else "Jelenleg nyerésre áll",
                                    color = AccentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = if (isSaved) "✓ Mentve" else "+ Felvétel",
                            color = if (isSaved) GoldAccent else AccentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = BackgroundColor
            )
        ) {
            Text("VISSZA A MÉRKŐZÉSEKHEZ")
        }
    }
}
