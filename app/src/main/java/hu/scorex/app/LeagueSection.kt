package hu.scorex.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Collator
import java.util.Locale

private val GlassBase = Color(0xFF132238).copy(alpha = 0.72f)
private val GlassHighlight = Color(0xFF1E3454).copy(alpha = 0.52f)
private val GlassDark = Color(0xFF0C1526).copy(alpha = 0.78f)
private val LeagueGold = Color(0xFFFFC857)
private val LeagueGreen = Color(0xFF35E0A1)
private val LeagueCyan = Color(0xFF38BDF8)
private val LeaguePurple = Color(0xFFA78BFA)
private val LeagueCoral = Color(0xFFFF7A90)
private val LeagueText = Color(0xFF9FB0C7)

private val AccentPalette = listOf(
    LeagueGreen,
    LeagueCyan,
    LeaguePurple,
    LeagueCoral,
    Color(0xFF60A5FA),
    Color(0xFF4ADE80),
    Color(0xFFF472B6),
    Color(0xFF2DD4BF)
)

private enum class LeagueFilter(val label: String) {
    ALL("Mind"),
    FAVORITES("Kedvenc"),
    LIVE("Élő"),
    TIPS("Tippes"),
    FINISHED("Kész")
}

fun isMatchLive(match: ScorexMatch): Boolean {
    val finished = isMatchFinished(match)
    if (finished) return false
    return match.matchTime.equals("ÉLŐ", ignoreCase = true) ||
        match.matchTime.equals("HT", ignoreCase = true) ||
        match.rawStatus.equals("HT", ignoreCase = true) ||
        match.rawStatus.equals("LIVE", ignoreCase = true) ||
        match.matchTime.contains("'") ||
        match.matchTime.contains("+")
}

fun isMatchFinished(match: ScorexMatch): Boolean {
    return match.matchTime.equals("FT", ignoreCase = true) ||
        match.matchTime.equals("AET", ignoreCase = true) ||
        match.matchTime.equals("PEN", ignoreCase = true) ||
        match.rawStatus.equals("FT", ignoreCase = true) ||
        match.rawStatus.equals("AET", ignoreCase = true) ||
        match.rawStatus.equals("PEN", ignoreCase = true)
}

private fun matchSortRank(match: ScorexMatch): Int = when {
    isMatchLive(match) -> 0
    !isMatchFinished(match) -> 1
    else -> 2
}

private fun matchTimeSortKey(match: ScorexMatch): Int {
    val minuteMatch = Regex("^(\\d+)").find(match.matchTime.trim())
    if (minuteMatch != null) {
        return minuteMatch.groupValues[1].toIntOrNull() ?: 999
    }
    val clockMatch = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(match.matchTime.trim())
    if (clockMatch != null) {
        val h = clockMatch.groupValues[1].toIntOrNull() ?: 99
        val m = clockMatch.groupValues[2].toIntOrNull() ?: 99
        return h * 60 + m
    }
    return 9999
}

private fun accentColorForLeague(
    league: String,
    translatedName: String,
    isFavorite: Boolean
): Color {
    if (isFavorite) return LeagueGold
    val key = translatedName.ifBlank { league }.lowercase(Locale("hu", "HU"))
    if (key.startsWith("magyarország")) return Color(0xFFFF6B6B)
    if (key.startsWith("nemzetközi") || key.startsWith("világ")) return LeaguePurple
    val idx = (key.hashCode() and Int.MAX_VALUE) % AccentPalette.size
    return AccentPalette[idx]
}

private fun hasTipForMatch(match: ScorexMatch, savedTips: List<String>): Boolean {
    val prefix = "${match.homeTeam} – ${match.awayTeam}: "
    return savedTips.any { it.startsWith(prefix, ignoreCase = true) }
}

@Composable
private fun LeagueGlassCard(
    borderColor: Color,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = gradientColors))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = shape
                ),
            content = content
        )
    }
}

@Composable
fun LeagueSection(
    matches: List<ScorexMatch>,
    preferences: SharedPreferences,
    savedTips: List<String> = emptyList(),
    selectedLeagueKey: String? = null,
    onClearSelectedLeague: () -> Unit = {},
    onSelectMatch: (ScorexMatch) -> Unit = {}
) {
    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(
                preferences.getStringSet("favorite_leagues", emptySet())
                    ?: emptySet()
            )
        }
    }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "favorite_leagues") {
                val updated = prefs.getStringSet("favorite_leagues", emptySet()).orEmpty()
                favorites.clear()
                favorites.addAll(updated)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun persistFavorites() {
        preferences.edit()
            .putStringSet("favorite_leagues", favorites.toSet())
            .apply()
    }

    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(LeagueFilter.ALL) }
    var showFavoritesManager by remember { mutableStateOf(false) }

    val hungarianCollator = remember {
        Collator.getInstance(Locale("hu", "HU")).apply {
            strength = Collator.PRIMARY
        }
    }

    val filteredMatches = remember(
        matches,
        searchQuery,
        activeFilter,
        favorites.toList(),
        savedTips.toList(),
        selectedLeagueKey
    ) {
        val q = searchQuery.trim().lowercase(Locale("hu", "HU"))
        matches.filter { match ->
            val leagueKey = match.league.ifBlank { "Ismeretlen bajnokság" }
            val translatedLeague = translateLeagueName(leagueKey).lowercase(Locale("hu", "HU"))

            val matchesSelectedLeague = selectedLeagueKey == null || leagueKey == selectedLeagueKey

            val matchesFilter = when (activeFilter) {
                LeagueFilter.ALL -> true
                LeagueFilter.FAVORITES -> leagueKey in favorites
                LeagueFilter.LIVE -> isMatchLive(match)
                LeagueFilter.TIPS -> hasTipForMatch(match, savedTips)
                LeagueFilter.FINISHED -> isMatchFinished(match)
            }

            val matchesSearch = q.isEmpty() ||
                leagueKey.lowercase(Locale("hu", "HU")).contains(q) ||
                translatedLeague.contains(q) ||
                match.homeTeam.lowercase(Locale("hu", "HU")).contains(q) ||
                match.awayTeam.lowercase(Locale("hu", "HU")).contains(q)

            matchesSelectedLeague && matchesFilter && matchesSearch
        }
    }

    val groupedMatches = remember(filteredMatches) {
        filteredMatches
            .groupBy { it.league.ifBlank { "Ismeretlen bajnokság" } }
            .mapValues { (_, leagueList) ->
                leagueList.sortedWith(
                    compareBy<ScorexMatch> { matchSortRank(it) }
                        .thenBy { matchTimeSortKey(it) }
                        .thenBy { it.matchTime }
                        .thenBy { it.homeTeam }
                )
            }
    }

    val sortedEntries = remember(groupedMatches, favorites.toList(), hungarianCollator) {
        groupedMatches.entries.sortedWith { a, b ->
            val aFav = a.key in favorites
            val bFav = b.key in favorites
            if (aFav != bFav) {
                if (aFav) -1 else 1
            } else {
                val nameA = translateLeagueName(a.key)
                val nameB = translateLeagueName(b.key)
                hungarianCollator.compare(nameA, nameB)
            }
        }
    }

    val allExpanded = sortedEntries.isNotEmpty() && sortedEntries.all { entry ->
        expandedStates[entry.key] ?: true
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        if (selectedLeagueKey != null) {
            LeagueGlassCard(
                borderColor = LeagueGold.copy(alpha = 0.5f),
                gradientColors = listOf(LeagueGold.copy(alpha = 0.16f), GlassBase)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "★ Szűrt liga: ${translateLeagueName(selectedLeagueKey)}",
                        color = LeagueGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "ÖSSZES LIGA",
                        color = LeagueCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(GlassDark)
                            .clickable { onClearSelectedLeague() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LeagueGlassCard(
            borderColor = LeagueCyan.copy(alpha = 0.34f),
            gradientColors = listOf(GlassHighlight, GlassBase)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Keresés csapatra vagy bajnokságra...",
                            color = LeagueText,
                            fontSize = 12.sp
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                color = LeagueCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GlassDark,
                        unfocusedContainerColor = GlassDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = LeagueCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        cursorColor = LeagueCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LeagueFilter.entries.forEach { filter ->
                        val selected = activeFilter == filter
                        val chipColor = when (filter) {
                            LeagueFilter.ALL -> LeagueCyan
                            LeagueFilter.FAVORITES -> LeagueGold
                            LeagueFilter.LIVE -> LeagueGreen
                            LeagueFilter.TIPS -> LeaguePurple
                            LeagueFilter.FINISHED -> LeagueCoral
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) chipColor.copy(alpha = 0.24f)
                                    else GlassDark
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) chipColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .clickable { activeFilter = filter }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                color = if (selected) chipColor else LeagueText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (favorites.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★ ${favorites.size} mentett kedvenc liga",
                            color = LeagueGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (showFavoritesManager) "Bezárás" else "Kedvencek kezelése",
                            color = LeagueCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable { showFavoritesManager = !showFavoritesManager }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (showFavoritesManager) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GlassDark)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            favorites.sortedWith { a, b ->
                                hungarianCollator.compare(translateLeagueName(a), translateLeagueName(b))
                            }.forEach { favLeague ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "★ ${translateLeagueName(favLeague)}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Eltávolít",
                                        color = LeagueCoral,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                favorites.remove(favLeague)
                                                persistFavorites()
                                                if (selectedLeagueKey == favLeague) {
                                                    onClearSelectedLeague()
                                                }
                                            }
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sortedEntries.isEmpty()) {
            LeagueGlassCard(
                borderColor = LeagueCoral.copy(alpha = 0.3f),
                gradientColors = listOf(GlassHighlight, GlassBase)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Nincs találat a megadott szűrőkre vagy keresésre.",
                        color = LeagueText,
                        fontSize = 12.sp
                    )
                    if (
                        searchQuery.isNotBlank() ||
                        activeFilter != LeagueFilter.ALL ||
                        selectedLeagueKey != null
                    ) {
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                activeFilter = LeagueFilter.ALL
                                onClearSelectedLeague()
                            },
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(
                                text = "SZŰRŐK TÖRLÉSE",
                                color = LeagueCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            return@Column
        }

        LeagueGlassCard(
            borderColor = LeagueCyan.copy(alpha = 0.35f),
            gradientColors = listOf(Color(0xFF1F3859).copy(alpha = 0.80f), GlassBase)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newState = !allExpanded
                        sortedEntries.forEach { entry ->
                            expandedStates[entry.key] = newState
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LeagueGreen.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚽", fontSize = 16.sp)
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Összes bajnokság (${sortedEntries.size})",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (allExpanded) {
                            "Minden bajnokság nyitva • ${favorites.size} kedvenc"
                        } else {
                            "Bajnokságok összecsukva • ${favorites.size} kedvenc"
                        },
                        color = LeagueText,
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = if (allExpanded) "⌃" else "⌄",
                    color = LeagueGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        sortedEntries.forEach { (league, leagueMatches) ->

            val expanded = expandedStates[league] ?: true
            val isFavorite = league in favorites
            val translatedLeague = translateLeagueName(league)
            val accent = accentColorForLeague(league, translatedLeague, isFavorite)
            val liveCount = leagueMatches.count { isMatchLive(it) }

            LeagueGlassCard(
                borderColor = accent.copy(alpha = if (isFavorite) 0.65f else 0.30f),
                gradientColors = if (isFavorite) {
                    listOf(LeagueGold.copy(alpha = 0.18f), GlassBase)
                } else {
                    listOf(accent.copy(alpha = 0.12f), GlassBase)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStates[league] = !expanded }
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(56.dp)
                            .background(accent)
                    )

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFavorite) "★" else "⚽",
                            color = accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(9.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translatedLeague,
                            color = if (isFavorite) LeagueGold else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        Spacer(Modifier.height(3.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.20f))
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${leagueMatches.size} meccs",
                                    color = accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (liveCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(LeagueGreen.copy(alpha = 0.22f))
                                        .padding(horizontal = 7.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$liveCount ÉLŐ",
                                        color = LeagueGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isFavorite) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(LeagueGold.copy(alpha = 0.22f))
                                        .padding(horizontal = 7.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "KEDVENC",
                                        color = LeagueGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = if (isFavorite) "★" else "☆",
                        color = LeagueGold,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (isFavorite) {
                                    favorites.remove(league)
                                    if (selectedLeagueKey == league) {
                                        onClearSelectedLeague()
                                    }
                                } else {
                                    favorites.add(league)
                                }
                                persistFavorites()
                            }
                            .padding(4.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = if (expanded) "⌃" else "⌄",
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (expanded) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        leagueMatches.forEach { match ->
                            LiveMatchCard(
                                match = match,
                                hasSavedTip = hasTipForMatch(match, savedTips),
                                onClick = { onSelectMatch(match) }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun translateLeagueName(original: String): String {

    val countryTranslations = mapOf(
        "africa" to "Afrika",
        "albania" to "Albánia",
        "algeria" to "Algéria",
        "andorra" to "Andorra",
        "angola" to "Angola",
        "argentina" to "Argentína",
        "armenia" to "Örményország",
        "australia" to "Ausztrália",
        "austria" to "Ausztria",
        "azerbaijan" to "Azerbajdzsán",
        "bahrain" to "Bahrein",
        "belarus" to "Fehéroroszország",
        "belgium" to "Belgium",
        "bolivia" to "Bolívia",
        "bosnia and herzegovina" to "Bosznia-Hercegovina",
        "brazil" to "Brazília",
        "bulgaria" to "Bulgária",
        "cameroon" to "Kamerun",
        "canada" to "Kanada",
        "chile" to "Chile",
        "china" to "Kína",
        "colombia" to "Kolumbia",
        "croatia" to "Horvátország",
        "cyprus" to "Ciprus",
        "czech republic" to "Csehország",
        "czechia" to "Csehország",
        "denmark" to "Dánia",
        "ecuador" to "Ecuador",
        "egypt" to "Egyiptom",
        "england" to "Anglia",
        "estonia" to "Észtország",
        "finland" to "Finnország",
        "france" to "Franciaország",
        "germany" to "Németország",
        "ghana" to "Ghána",
        "greece" to "Görögország",
        "hungary" to "Magyarország",
        "iceland" to "Izland",
        "india" to "India",
        "indonesia" to "Indonézia",
        "international" to "Nemzetközi",
        "iran" to "Irán",
        "iraq" to "Irak",
        "ireland" to "Írország",
        "israel" to "Izrael",
        "italy" to "Olaszország",
        "ivory coast" to "Elefántcsontpart",
        "japan" to "Japán",
        "kazakhstan" to "Kazahsztán",
        "kenya" to "Kenya",
        "kosovo" to "Koszovó",
        "latvia" to "Lettország",
        "lebanon" to "Libanon",
        "lithuania" to "Litvánia",
        "luxembourg" to "Luxemburg",
        "malaysia" to "Malajzia",
        "malta" to "Málta",
        "mexico" to "Mexikó",
        "moldova" to "Moldova",
        "montenegro" to "Montenegró",
        "morocco" to "Marokkó",
        "netherlands" to "Hollandia",
        "new zealand" to "Új-Zéland",
        "nigeria" to "Nigéria",
        "north macedonia" to "Észak-Macedónia",
        "northern ireland" to "Észak-Írország",
        "norway" to "Norvégia",
        "paraguay" to "Paraguay",
        "peru" to "Peru",
        "poland" to "Lengyelország",
        "portugal" to "Portugália",
        "qatar" to "Katar",
        "romania" to "Románia",
        "russia" to "Oroszország",
        "saudi arabia" to "Szaúd-Arábia",
        "scotland" to "Skócia",
        "serbia" to "Szerbia",
        "singapore" to "Szingapúr",
        "slovakia" to "Szlovákia",
        "slovenia" to "Szlovénia",
        "south africa" to "Dél-Afrika",
        "south korea" to "Dél-Korea",
        "spain" to "Spanyolország",
        "sweden" to "Svédország",
        "switzerland" to "Svájc",
        "thailand" to "Thaiföld",
        "tunisia" to "Tunézia",
        "turkey" to "Törökország",
        "türkiye" to "Törökország",
        "ukraine" to "Ukrajna",
        "united arab emirates" to "Egyesült Arab Emírségek",
        "united states" to "Egyesült Államok",
        "uruguay" to "Uruguay",
        "uzbekistan" to "Üzbegisztán",
        "venezuela" to "Venezuela",
        "vietnam" to "Vietnám",
        "wales" to "Wales",
        "world" to "Világ"
    )

    val parts = original.split(":", limit = 2)

    if (parts.size < 2) {
        return translateCompetitionWords(original.trim())
    }

    val country = parts[0].trim()
    val competition = parts[1].trim()

    val translatedCountry =
        countryTranslations[country.lowercase()]
            ?: country

    return "$translatedCountry: ${
        translateCompetitionWords(competition)
    }"
}

private fun translateCompetitionWords(original: String): String {

    val replacements = listOf(
        "Champions League Qualification" to "BL-selejtező",
        "Europa League Qualification" to "EL-selejtező",
        "Conference League Qualification" to "KL-selejtező",
        "Reserve League" to "Tartalékbajnokság",
        "Premier League 2" to "Premier League 2",
        "Community Shield" to "Community Shield",
        "FA Trophy" to "FA Trophy",
        "EFL Cup" to "EFL-kupa",
        "Supercoppa" to "Szuperkupa",
        "Friendlies" to "Barátságos mérkőzések",
        "Friendly" to "Barátságos mérkőzés",
        "Qualification" to "Selejtező",
        "Qualifying" to "Selejtező",
        "Play Offs" to "Rájátszás",
        "Play-Offs" to "Rájátszás",
        "Playoffs" to "Rájátszás",
        "Super Cup" to "Szuperkupa",
        "League Cup" to "Ligakupa",
        "National Cup" to "Nemzeti kupa",
        "FA Cup" to "FA-kupa",
        "Copa del Rey" to "Copa del Rey",
        "Coppa Italia" to "Olasz Kupa",
        "DFB-Pokal" to "Német Kupa",
        "Coupe de France" to "Francia Kupa",
        "Cup" to "Kupa",
        "World Cup" to "Világbajnokság",
        "Copa America" to "Copa América",
        "Women" to "Női",
        "Woman" to "Női",
        "Youth" to "Utánpótlás",
        "Reserves" to "Tartalék",
        "Reserve" to "Tartalék",
        "U23" to "U23",
        "U21" to "U21",
        "U19" to "U19",
        "U18" to "U18",
        "U17" to "U17",
        "U16" to "U16",
        "U15" to "U15",
        "First Division" to "Első osztály",
        "Second Division" to "Másodosztály",
        "Third Division" to "Harmadosztály",
        "Segunda División" to "Másodosztály",
        "Premier League" to "Premier League",
        "Scottish Premiership" to "Skót Premiership",
        "Belgian Pro League" to "Belga Pro League",
        "Champions League" to "Bajnokok Ligája",
        "Europa League" to "Európa-liga",
        "Conference League" to "Konferencia-liga",
        "Group Stage" to "Csoportkör",
        "Round of 16" to "Nyolcaddöntő",
        "Quarter-finals" to "Negyeddöntő",
        "Semi-finals" to "Elődöntő",
        "Final" to "Döntő",
        "Championship" to "Bajnokság",
        "Serie A" to "Serie A",
        "Serie B" to "Serie B",
        "Serie C" to "Serie C",
        "La Liga" to "La Liga",
        "Liga MX" to "Liga MX",
        "Bundesliga" to "Bundesliga",
        "Ligue 1" to "Ligue 1",
        "Ligue 2" to "Ligue 2",
        "Eredivisie" to "Eredivisie",
        "Primeira Liga" to "Primeira Liga",
        "Allsvenskan" to "Allsvenskan",
        "Eliteserien" to "Eliteserien",
        "Super Lig" to "Süper Lig",
        "J1 League" to "J1 League",
        "K League 1" to "K League 1",
        "A-League" to "A-League",
        "MLS" to "MLS",
        "Apertura" to "Apertura",
        "Clausura" to "Clausura"
    )

    var result = original

    replacements.forEach { (english, hungarian) ->
        result = result.replace(
            oldValue = english,
            newValue = hungarian,
            ignoreCase = true
        )
    }

    return result
}
