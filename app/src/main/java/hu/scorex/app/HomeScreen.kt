package hu.scorex.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    preferences: SharedPreferences,
    savedTips: List<String>,
    onRemoveTip: (String) -> Unit,
    onClearTips: () -> Unit,
    onRemoveSettledTips: (List<ScorexMatch>) -> Unit,
    onSelectMatch: (ScorexMatch) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApiData: () -> Unit
) {

    var apiKey by remember {
        mutableStateOf(
            preferences.getString(
                "statpal_api_key",
                ""
            ).orEmpty()
        )
    }

    var refreshIntervalSec by remember {
        mutableIntStateOf(
            preferences.getInt("refresh_interval_sec", 15).coerceIn(15, 120)
        )
    }

    LaunchedEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "statpal_api_key" -> apiKey = prefs.getString("statpal_api_key", "").orEmpty()
                "refresh_interval_sec" -> refreshIntervalSec =
                    prefs.getInt("refresh_interval_sec", 15).coerceIn(15, 120)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    var dayOffset by remember {
        mutableIntStateOf(0)
    }

    var autoRefresh by remember {
        mutableStateOf(true)
    }

    var tipFilter by remember {
        mutableStateOf(TipFilter.ALL)
    }

    var matches by remember {
        mutableStateOf<List<ScorexMatch>>(emptyList())
    }

    var statusMessage by remember {
        mutableStateOf("Mérkőzések betöltése...")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var refreshTrigger by remember {
        mutableIntStateOf(0)
    }

    var lastUpdated by remember {
        mutableStateOf("")
    }

    val favoriteCount = remember(preferences, matches) {
        preferences.getStringSet("favorite_leagues", emptySet()).orEmpty().size
    }

    val liveMatchesCount = remember(matches) {
        matches.count { isMatchLive(it) }
    }

    val tipStatuses = remember(savedTips.toList(), matches) {
        savedTips.map { tip ->
            val matched = findMatchingMatch(tip, matches)
            val statusPair = evaluateTipStatus(tip, matches)
            Triple(tip, statusPair, matched)
        }
    }

    val filteredTipStatuses = remember(tipStatuses, tipFilter) {
        when (tipFilter) {
            TipFilter.ALL -> tipStatuses
            TipFilter.WINNING -> tipStatuses.filter { (_, st, _) ->
                st.first == "Nyerésre áll" || st.first == "Találat"
            }
            TipFilter.SETTLED -> tipStatuses.filter { (_, st, _) ->
                st.first == "Találat" || st.first == "Nem jött be" || st.first == "Lezárult"
            }
        }
    }

    val hitCount = remember(tipStatuses) {
        tipStatuses.count { (_, st, _) -> st.first == "Találat" }
    }

    val winningLiveCount = remember(tipStatuses) {
        tipStatuses.count { (_, st, _) -> st.first == "Nyerésre áll" }
    }

    val missedCount = remember(tipStatuses) {
        tipStatuses.count { (_, st, _) -> st.first == "Nem jött be" }
    }

    val settledCount = remember(tipStatuses) {
        tipStatuses.count { (_, st, _) ->
            st.first == "Találat" || st.first == "Nem jött be" || st.first == "Lezárult"
        }
    }

    LaunchedEffect(apiKey, dayOffset, refreshTrigger, autoRefresh, refreshIntervalSec) {

        if (apiKey.isBlank()) {
            matches = emptyList()
            statusMessage = "Állítsd be a StatPal API-kulcsodat."
            return@LaunchedEffect
        }

        do {
            isLoading = true
            statusMessage = "Kapcsolódás a StatPalhoz (${formatOffsetLabel(dayOffset)})..."

            try {
                val result = withContext(Dispatchers.IO) {
                    StatPalApi.fetchLiveMatches(apiKey, dayOffset)
                }

                matches = result

                statusMessage = if (result.isEmpty()) {
                    "Jelenleg nincs megjeleníthető mérkőzés (${formatOffsetLabel(dayOffset)})."
                } else {
                    "${result.size} mérkőzés betöltve (${formatOffsetLabel(dayOffset)})."
                }

                lastUpdated = SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

            } catch (exception: Exception) {
                statusMessage = exception.message
                    ?: "Ismeretlen kapcsolódási hiba."
            } finally {
                isLoading = false
            }

            if (autoRefresh) {
                delay(refreshIntervalSec * 1_000L)
            }
        } while (autoRefresh)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {

        GlassPanel(
            borderColor = AccentColor.copy(alpha = 0.36f),
            gradientColors = listOf(GlassTop, GlassSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SCORE",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "X",
                            color = AccentColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "ÉLD ÁT A FUTBALLT",
                        color = CyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor.copy(alpha = 0.22f),
                        contentColor = AccentColor
                    ),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = AccentColor.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("⚙", fontSize = 19.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SummaryStatPill(
                label = "MECCSEK",
                value = matches.size.toString(),
                accent = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            SummaryStatPill(
                label = "ÉLŐ",
                value = liveMatchesCount.toString(),
                accent = AccentColor,
                modifier = Modifier.weight(1f)
            )
            SummaryStatPill(
                label = "KEDVENCEK",
                value = favoriteCount.toString(),
                accent = GoldAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GlassPanel(
            borderColor = CyanAccent.copy(alpha = 0.26f),
            gradientColors = listOf(GlassSurface, GlassWell)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { dayOffset-- },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text("‹ Előző", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatOffsetLabel(dayOffset),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (dayOffset != 0) {
                        Text(
                            text = "Vissza mára",
                            color = AccentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { dayOffset = 0 }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { dayOffset++ },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text("Következő ›", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MÉRKŐZÉSEK",
                color = AccentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (autoRefresh) AccentColor.copy(alpha = 0.18f)
                            else GlassWell
                        )
                        .border(
                            width = 1.dp,
                            color = if (autoRefresh) AccentColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable { autoRefresh = !autoRefresh }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (autoRefresh) "AUTO (${refreshIntervalSec}s)" else "AUTO: KI",
                        color = if (autoRefresh) AccentColor else SecondaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { refreshTrigger++ },
                    enabled = !isLoading && apiKey.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "FRISSÍTÉS",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isLoading) {
                    "Adatok frissítése..."
                } else {
                    statusMessage
                },
                color = if (
                    statusMessage.contains("hiba", true) ||
                    statusMessage.contains("hibás", true)
                ) ErrorColor else SecondaryColor,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )

            if (lastUpdated.isNotBlank()) {
                Text(
                    text = lastUpdated,
                    color = CyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (matches.isEmpty()) {
            GlassPanel(
                borderColor = CyanAccent.copy(alpha = 0.30f),
                gradientColors = listOf(GlassTop, GlassSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLoading) "⚽" else "📡",
                        fontSize = 32.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (apiKey.isBlank()) {
                            "STATPAL API-KULCS SZÜKSÉGES"
                        } else if (isLoading) {
                            "MÉRKŐZÉSEK BETÖLTÉSE"
                        } else {
                            "NINCS MEGJELENÍTHETŐ MÉRKŐZÉS"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = statusMessage,
                        color = SecondaryColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    if (apiKey.isBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentColor,
                                contentColor = BackgroundColor
                            )
                        ) {
                            Text("API-KULCS BEÁLLÍTÁSA")
                        }
                    }
                }
            }
        } else {
            ScorexFavoritesQuickBar(
                preferences = preferences,
                matches = matches
            )

            Spacer(modifier = Modifier.height(10.dp))

            LeagueSection(
                matches = matches,
                preferences = preferences,
                savedTips = savedTips,
                onSelectMatch = onSelectMatch
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenApiData,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "API ADATOK MEGTEKINTÉSE",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassPanel(
            borderColor = PurpleAccent.copy(alpha = 0.42f),
            gradientColors = listOf(PurpleAccent.copy(alpha = 0.18f), GlassSurface)
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
                        text = "SCOREX TIPPEK (${savedTips.size})",
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settledCount > 0) {
                            Text(
                                text = "LEZÁRTAK TÖRLÉSE",
                                color = WarningColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(GlassWell)
                                    .clickable { onRemoveSettledTips(matches) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        if (savedTips.isNotEmpty()) {
                            Text(
                                text = "ÖSSZES TÖRLÉSE",
                                color = ErrorColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(GlassWell)
                                    .clickable { onClearTips() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (savedTips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TipStatChip(
                            text = "Találat: $hitCount",
                            color = AccentColor,
                            modifier = Modifier.weight(1f)
                        )
                        TipStatChip(
                            text = "Nyerésre áll: $winningLiveCount",
                            color = CyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TipStatChip(
                            text = "Nem jött be: $missedCount",
                            color = ErrorColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        TipFilter.entries.forEach { filter ->
                            val selected = tipFilter == filter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) GoldAccent.copy(alpha = 0.22f)
                                        else GlassWell
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) GoldAccent else Color.White.copy(alpha = 0.10f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tipFilter = filter }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    color = if (selected) GoldAccent else SecondaryColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (savedTips.isEmpty()) {
                        "Koppints bármelyik mérkőzésre a részletekért, és adj hozzá tippeket (1 / X / 2) a szelvényedhez."
                    } else {
                        "Elmentett tippjeid és aktuális állásuk:"
                    },
                    color = SecondaryColor,
                    fontSize = 12.sp
                )

                filteredTipStatuses.forEach { (tip, statusPair, matched) ->
                    val (statusLabel, statusBadgeColor) = statusPair
                    val scorePreview = matched?.let {
                        "${it.homeScore}:${it.awayScore} • ${it.matchTime}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(GlassWell)
                            .border(
                                width = 0.5.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(11.dp)
                            )
                            .clickable(enabled = matched != null) {
                                if (matched != null) onSelectMatch(matched)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tip,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (scorePreview != null) {
                                Text(
                                    text = "Állás: $scorePreview",
                                    color = SecondaryColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(statusBadgeColor.copy(alpha = 0.20f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusBadgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "✕",
                            color = ErrorColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onRemoveTip(tip) }
                                .padding(start = 3.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SCOREX • FOOTBALL CENTER",
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScorexFavoritesQuickBar(
    preferences: SharedPreferences,
    matches: List<ScorexMatch>
) {
    val favoriteLeagues = remember(preferences, matches) {
        preferences.getStringSet("favorite_leagues", emptySet())
            .orEmpty()
            .toList()
            .sortedBy { translateLeagueName(it) }
    }

    if (favoriteLeagues.isEmpty()) return

    var selectedLeague by remember(favoriteLeagues) {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "KEDVENC LIGÁK",
            color = GoldAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            favoriteLeagues.forEach { league ->
                val leagueKey = league.ifBlank { "Ismeretlen bajnokság" }
                val count = matches.count {
                    (it.league.ifBlank { "Ismeretlen bajnokság" }) == leagueKey
                }
                val isSelected = selectedLeague == leagueKey
                val label = translateLeagueName(leagueKey)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) GoldAccent.copy(alpha = 0.22f)
                            else GlassWell
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) GoldAccent else GoldAccent.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            selectedLeague = if (isSelected) null else leagueKey
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "★ $label ($count)",
                        color = if (isSelected) GoldAccent else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TipStatChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.13f))
            .border(
                width = 0.5.dp,
                color = color.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 4.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), GlassSurface)))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.36f),
                shape = RoundedCornerShape(13.dp)
            )
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = SecondaryColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
