package hu.scorex.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MatchDetailTab(val label: String) {
    OVERVIEW("Áttekintés"),
    H2H_PRED("H2H & Tipp"),
    LINEUPS("Felállás & Hiányzók"),
    ODDS_WEATHER("Szorzók & Időjárás")
}

@Composable
fun MatchDetailScreen(
    match: ScorexMatch,
    preferences: SharedPreferences,
    savedTips: List<String>,
    onToggleTip: (String) -> Unit,
    onOpenProfile: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val apiKey = remember(preferences) {
        preferences.getString("statpal_api_key", "").orEmpty()
    }

    var activeTab by remember { mutableStateOf(MatchDetailTab.OVERVIEW) }

    var prediction by remember(match.id) { mutableStateOf<MatchPredictionInfo?>(null) }
    var h2hList by remember(match.id) { mutableStateOf<List<String>>(emptyList()) }
    var lineups by remember(match.id) { mutableStateOf<MatchLineupsInfo?>(null) }
    var injuries by remember(match.id) { mutableStateOf<List<InjuryItem>>(emptyList()) }
    var odds by remember(match.id) { mutableStateOf<List<MatchOddsItem>>(emptyList()) }
    var weather by remember(match.id) { mutableStateOf<MatchWeatherInfo?>(null) }
    var isLoadingTab by remember(match.id, activeTab) { mutableStateOf(false) }

    LaunchedEffect(match.id, activeTab, apiKey) {
        if (apiKey.isBlank()) return@LaunchedEffect
        when (activeTab) {
            MatchDetailTab.H2H_PRED -> {
                if (prediction == null && h2hList.isEmpty()) {
                    isLoadingTab = true
                    withContext(Dispatchers.IO) {
                        prediction = runCatching { StatPalApi.fetchMatchPrediction(apiKey, match.id) }.getOrNull()
                        h2hList = runCatching { StatPalApi.fetchMatchH2H(apiKey, match) }.getOrDefault(emptyList())
                    }
                    isLoadingTab = false
                }
            }
            MatchDetailTab.LINEUPS -> {
                if (lineups == null && injuries.isEmpty()) {
                    isLoadingTab = true
                    withContext(Dispatchers.IO) {
                        lineups = runCatching { StatPalApi.fetchMatchLineups(apiKey, match.id) }.getOrNull()
                        injuries = runCatching { StatPalApi.fetchMatchInjuries(apiKey, match) }.getOrDefault(emptyList())
                    }
                    isLoadingTab = false
                }
            }
            MatchDetailTab.ODDS_WEATHER -> {
                if (odds.isEmpty() && weather == null) {
                    isLoadingTab = true
                    withContext(Dispatchers.IO) {
                        odds = runCatching { StatPalApi.fetchMatchLiveOdds(apiKey, match) }.getOrDefault(emptyList())
                        weather = runCatching { StatPalApi.fetchMatchWeather(apiKey, match.id) }.getOrNull()
                    }
                    isLoadingTab = false
                }
            }
            MatchDetailTab.OVERVIEW -> Unit
        }
    }

    val live = isMatchLive(match)
    val finished = isMatchFinished(match)
    val translatedLeague = translateLeagueName(match.league)
    val leagueKey = match.league.ifBlank { "Ismeretlen bajnokság" }

    val homeKey = favoriteTeamKey(match.homeTeam)
    val awayKey = favoriteTeamKey(match.awayTeam)
    val resolvedHomeId = StatPalApi.resolveTeamId(match.homeTeam, match.homeTeamId)
    val resolvedAwayId = StatPalApi.resolveTeamId(match.awayTeam, match.awayTeamId)

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
        if (key in updated) updated.remove(key) else updated.add(key)
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
    } else null

    val extraNote = when {
        match.matchTime.equals("AET", true) || match.rawStatus.equals("AET", true) -> " (hosszabbítás után)"
        match.matchTime.equals("PEN", true) || match.rawStatus.equals("PEN", true) -> " (tizenegyesekkel)"
        else -> ""
    }

    val summaryText = when {
        homeGoalsInt != null && awayGoalsInt != null -> when {
            homeGoalsInt > awayGoalsInt -> "${match.homeTeam} vezet / áll ${match.homeScore}–${match.awayScore} arányban$extraNote."
            awayGoalsInt > homeGoalsInt -> "${match.awayTeam} vezet / áll ${match.homeScore}–${match.awayScore} arányban$extraNote."
            else -> "Kiegyenlített állás: ${match.homeScore}–${match.awayScore}$extraNote."
        }
        finished -> "A mérkőzés véget ért (${match.homeScore} – ${match.awayScore})$extraNote."
        live -> "A mérkőzés folyamatban van (${match.matchTime})."
        else -> "Állapot / kezdés: ${match.matchTime}."
    }

    val tipOptions = listOf(
        "1 (${match.homeTeam})",
        "X (Döntetlen)",
        "2 (${match.awayTeam})"
    )

    val matchSavedTips = remember(savedTips.toList(), match.homeTeam, match.awayTeam) {
        val prefix = "${match.homeTeam} – ${match.awayTeam}: "
        savedTips.filter { it.startsWith(prefix, ignoreCase = true) }
    }

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
                    val current = preferences.getStringSet("favorite_leagues", emptySet()).orEmpty().toMutableSet()
                    if (isLeagueFavorite) current.remove(leagueKey) else current.add(leagueKey)
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
                            .background(if (live) AccentColor.copy(alpha = 0.22f) else CyanAccent.copy(alpha = 0.16f))
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
                        Text(text = match.date, color = SecondaryColor, fontSize = 11.sp)
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
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = resolvedHomeId.isNotBlank()) {
                                onOpenProfile("team", resolvedHomeId, match.homeTeam)
                            }
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
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = resolvedAwayId.isNotBlank()) {
                                onOpenProfile("team", resolvedAwayId, match.awayTeam)
                            }
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
                            .border(1.dp, if (isHomeFav) GoldAccent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
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
                            .border(1.dp, if (isAwayFav) GoldAccent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
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

                if (resolvedHomeId.isNotBlank() || resolvedAwayId.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (resolvedHomeId.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onOpenProfile("team", resolvedHomeId, match.homeTeam) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Hazai csapatprofil →", color = CyanAccent, fontSize = 10.sp)
                            }
                        }
                        if (resolvedAwayId.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onOpenProfile("team", resolvedAwayId, match.awayTeam) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Vendég csapatprofil →", color = CyanAccent, fontSize = 10.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text(text = summaryText, color = SecondaryColor, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MatchDetailTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) AccentColor.copy(alpha = 0.22f) else GlassWell)
                        .border(1.dp, if (selected) AccentColor else Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
                        .clickable { activeTab = tab }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = if (selected) AccentColor else SecondaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (isLoadingTab) {
            GlassPanel(
                borderColor = CyanAccent.copy(alpha = 0.25f),
                gradientColors = listOf(GlassSurface, GlassWell)
            ) {
                Text(
                    text = "Adatok betöltése a StatPal v2-ből...",
                    color = CyanAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        when (activeTab) {
            MatchDetailTab.OVERVIEW -> {
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
            }

            MatchDetailTab.H2H_PRED -> {
                GlassPanel(
                    borderColor = PurpleAccent.copy(alpha = 0.35f),
                    gradientColors = listOf(PurpleAccent.copy(alpha = 0.14f), GlassSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ELŐREJELZÉS & ESÉLYEK", color = PurpleAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val pred = prediction
                        if (pred == null) {
                            Text("Ehhez a mérkőzéshez jelenleg nincs előrejelzési adat.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            DiagnosticRow("Esélyesebb kimenetel", pred.favoriteSide, AccentColor)
                            DiagnosticRow("Hazai (1) / Döntetlen (X) / Vendég (2)", "${pred.homeWinProb} • ${pred.drawProb} • ${pred.awayWinProb}")
                            Text("Javaslat: ${pred.advice}", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                GlassPanel(
                    borderColor = CyanAccent.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("EGMÁS ELLENI (H2H)", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (h2hList.isEmpty()) {
                            Text("Nincs elérhető H2H előzmény ehhez a párosításhoz.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            h2hList.forEach { row ->
                                Text("• $row", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            MatchDetailTab.LINEUPS -> {
                GlassPanel(
                    borderColor = AccentColor.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CSAPAT FELÁLLÁSOK", color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val lu = lineups
                        if (lu == null || (lu.homeStarting.isEmpty() && lu.awayStarting.isEmpty())) {
                            Text("A kezdőcsapatok még nem érhetők el.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            if (lu.status.isNotBlank()) {
                                Text("Státusz: ${lu.status}", color = SecondaryColor, fontSize = 10.sp)
                            }
                            if (lu.homeStarting.isNotEmpty()) {
                                PitchLineupView(
                                    formation = lu.homeFormation,
                                    players = lu.homeStarting,
                                    teamName = match.homeTeam,
                                    isHome = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (lu.homeCoach.isNotBlank()) {
                                    Text("Edző: ${lu.homeCoach}", color = SecondaryColor, fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            if (lu.awayStarting.isNotEmpty()) {
                                PitchLineupView(
                                    formation = lu.awayFormation,
                                    players = lu.awayStarting,
                                    teamName = match.awayTeam,
                                    isHome = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (lu.awayCoach.isNotBlank()) {
                                    Text("Edző: ${lu.awayCoach}", color = SecondaryColor, fontSize = 10.sp)
                                }
                            }
                            // Bench lists
                            if (lu.homeBench.isNotEmpty() || lu.awayBench.isNotEmpty()) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 8.dp))
                                Text("CSERÉK", color = SecondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                (lu.homeBench + lu.awayBench).take(12).forEach { p ->
                                    Text(
                                        text = "${p.number}. ${p.name}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable(enabled = p.playerId.isNotBlank()) {
                                            onOpenProfile("player", p.playerId, p.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                GlassPanel(
                    borderColor = WarningColor.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SÉRÜLÉSEK ÉS ELTILTÁSOK", color = WarningColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (injuries.isEmpty()) {
                            Text("Nincs jelentett sérült vagy eltiltott játékos.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            injuries.forEach { inj ->
                                Text(
                                    text = "• ${inj.playerName}${if (inj.team.isNotBlank()) " (${inj.team})" else ""}: ${inj.reason} [${inj.status}]",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable(enabled = inj.playerId.isNotBlank()) {
                                        onOpenProfile("player", inj.playerId, inj.playerName)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            MatchDetailTab.ODDS_WEATHER -> {
                GlassPanel(
                    borderColor = GoldAccent.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SZORZÓK (ÉLŐ / MECCS ELŐTTI)", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (odds.isEmpty()) {
                            Text("Ehhez a mérkőzéshez jelenleg nincs elérhető szorzó.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            odds.forEach { item ->
                                DiagnosticRow(
                                    label = item.market,
                                    value = "1: ${item.homeOdd}  |  X: ${item.drawOdd}  |  2: ${item.awayOdd}",
                                    valueColor = GoldAccent
                                )
                            }
                        }
                    }
                }

                GlassPanel(
                    borderColor = CyanAccent.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("HELYSZÍNI IDŐJÁRÁS", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val w = weather
                        if (w == null) {
                            Text("Nincs elérhető időjárás-előrejelzés ehhez a meccshez.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            DiagnosticRow("Időjárás", w.condition, Color.White)
                            DiagnosticRow("Hőmérséklet", w.temperature, AccentColor)
                            DiagnosticRow("Szél", w.wind)
                            DiagnosticRow("Páratartalom", w.humidity)
                        }
                    }
                }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TIPP HOZZÁADÁSA A SZELVÉNYHEZ",
                        color = GoldAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    if (matchSavedTips.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "MÁSOL",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(GlassWell)
                                    .clickable {
                                        val txt = matchSavedTips.joinToString("\n") { "• $it" }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Scorex tipp", txt))
                                        Toast.makeText(context, "Tipp másolva!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                            Text(
                                text = "MEGOSZT",
                                color = AccentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(GlassWell)
                                    .clickable {
                                        val txt = matchSavedTips.joinToString("\n") { "• $it" }
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, txt)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Tipp megosztása"))
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                tipOptions.forEach { option ->
                    val fullTip = "${match.homeTeam} – ${match.awayTeam}: $option"
                    val isSaved = fullTip in savedTips
                    val isCurrentWinner = currentOutcomePrefix != null && option.startsWith(currentOutcomePrefix)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSaved) GoldAccent.copy(alpha = 0.20f) else GlassWell)
                            .border(1.dp, if (isSaved) GoldAccent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
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
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = BackgroundColor)
        ) {
            Text("VISSZA A MÉRKŐZÉSEKHEZ")
        }
    }
}
