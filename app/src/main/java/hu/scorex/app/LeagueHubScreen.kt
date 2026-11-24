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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LeagueHubScreen(
    preferences: SharedPreferences,
    onBack: () -> Unit
) {
    val apiKey = remember(preferences) {
        preferences.getString("statpal_api_key", "").orEmpty()
    }

    var leagues by remember { mutableStateOf<List<LeagueSummaryItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingLeagues by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var selectedLeague by remember { mutableStateOf<LeagueSummaryItem?>(null) }
    var seasons by remember { mutableStateOf<List<String>>(emptyList()) }
    var statsSummary by remember { mutableStateOf<LeagueStatsSummary?>(null) }
    var prematchOdds by remember { mutableStateOf<List<MatchOddsItem>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            errorMessage = "Állítsd be a StatPal API-kulcsodat a Beállításokban."
            return@LaunchedEffect
        }
        isLoadingLeagues = true
        errorMessage = ""
        try {
            leagues = withContext(Dispatchers.IO) {
                StatPalApi.fetchAllLeagues(apiKey)
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Nem sikerült betölteni a bajnokságokat."
        } finally {
            isLoadingLeagues = false
        }
    }

    LaunchedEffect(selectedLeague?.id) {
        val target = selectedLeague ?: return@LaunchedEffect
        if (apiKey.isBlank()) return@LaunchedEffect
        isLoadingDetails = true
        withContext(Dispatchers.IO) {
            seasons = runCatching { StatPalApi.fetchLeagueSeasons(apiKey, target.id) }.getOrDefault(emptyList())
            statsSummary = runCatching {
                StatPalApi.fetchLeagueStatsBundle(apiKey, target.id, translateLeagueName(target.name))
            }.getOrNull()
            prematchOdds = runCatching { StatPalApi.fetchLeaguePrematchOdds(apiKey, target.id) }.getOrDefault(emptyList())
        }
        isLoadingDetails = false
    }

    val filteredLeagues = remember(leagues, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) leagues
        else leagues.filter {
            it.name.lowercase().contains(q) ||
                translateLeagueName(it.name).lowercase().contains(q) ||
                it.country.lowercase().contains(q)
        }
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
            OutlinedButton(
                onClick = {
                    if (selectedLeague != null) selectedLeague = null else onBack()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (selectedLeague != null) "← ÖSSZES LIGA" else "← FŐOLDAL",
                    color = AccentColor,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "BAJNOKSÁGOK & STATISZTIKÁK",
                color = GoldAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }

        if (errorMessage.isNotBlank()) {
            GlassPanel(
                borderColor = ErrorColor.copy(alpha = 0.4f),
                gradientColors = listOf(GlassSurface, GlassWell)
            ) {
                Text(text = errorMessage, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
            }
        }

        if (selectedLeague != null) {
            val league = selectedLeague!!
            GlassPanel(
                borderColor = GoldAccent.copy(alpha = 0.45f),
                gradientColors = listOf(GoldAccent.copy(alpha = 0.16f), GlassSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = translateLeagueName(league.name),
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (seasons.isNotEmpty()) {
                        Text(
                            text = "Szezonok: ${seasons.take(6).joinToString(", ")}",
                            color = CyanAccent,
                            fontSize = 11.sp
                        )
                    }
                    if (isLoadingDetails) {
                        Text("Liga-statisztikák és szorzók betöltése...", color = SecondaryColor, fontSize = 12.sp)
                    }
                }
            }

            statsSummary?.let { summary ->
                GlassPanel(
                    borderColor = AccentColor.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LIGA STATISZTIKÁK", color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (summary.highlights.isEmpty()) {
                            Text("Nincs elérhető összesített mutató ehhez a ligához.", color = SecondaryColor, fontSize = 12.sp)
                        } else {
                            summary.highlights.forEach { (k, v) ->
                                DiagnosticRow(label = k, value = v, valueColor = AccentColor)
                            }
                        }
                    }
                }

                if (summary.recentMatches.isNotEmpty()) {
                    GlassPanel(
                        borderColor = CyanAccent.copy(alpha = 0.30f),
                        gradientColors = listOf(GlassSurface, GlassWell)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("LIGA MÉRKŐZÉS-STATISZTIKÁK", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            summary.recentMatches.forEach { line ->
                                Text("• $line", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (prematchOdds.isNotEmpty()) {
                GlassPanel(
                    borderColor = PurpleAccent.copy(alpha = 0.30f),
                    gradientColors = listOf(GlassSurface, GlassWell)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MECCS ELŐTTI SZORZÓK", color = PurpleAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        prematchOdds.forEach { odd ->
                            DiagnosticRow(
                                label = odd.market,
                                value = "1: ${odd.homeOdd}  |  X: ${odd.drawOdd}  |  2: ${odd.awayOdd}",
                                valueColor = GoldAccent
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Keresés bajnokságra vagy országra...", color = SecondaryColor, fontSize = 12.sp) },
                shape = RoundedCornerShape(11.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GlassWell,
                    unfocusedContainerColor = GlassWell,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                )
            )

            if (isLoadingLeagues) {
                Text("Bajnokságok betöltése...", color = CyanAccent, fontSize = 12.sp)
            } else {
                Text(
                    text = "Elérhető bajnokságok (${filteredLeagues.size})",
                    color = SecondaryColor,
                    fontSize = 11.sp
                )
                filteredLeagues.take(80).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(GlassSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
                            .clickable { selectedLeague = item }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = translateLeagueName(item.name),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.season.isNotBlank()) {
                                Text(text = "Szezon: ${item.season}", color = SecondaryColor, fontSize = 10.sp)
                            }
                        }
                        Text(text = "Megnyit ›", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
