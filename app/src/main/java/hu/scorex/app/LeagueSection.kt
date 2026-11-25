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

// Colors are defined in MainActivity.kt (package-level)
private val LeagueCyan = Color(0xFF38BDF8)
private val LeaguePurple = Color(0xFFA78BFA)
private val LeagueCoral = Color(0xFFFF7A90)
private val LeagueText = Color(0xFF9FB0C7)
private val GlassHighlight = Color(0xFF1E3454).copy(alpha = 0.52f)
private val GlassDark = Color(0xFF0C1526).copy(alpha = 0.78f)

private val AccentPalette = listOf(
    AccentColor,
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
    if (isFavorite) return GoldAccent
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

private fun hasFavoriteTeamInMatch(match: ScorexMatch, favoriteTeams: Set<String>): Boolean {
    if (favoriteTeams.isEmpty()) return false
    val hKey = favoriteTeamKey(match.homeTeam)
    val aKey = favoriteTeamKey(match.awayTeam)
    return hKey in favoriteTeams || aKey in favoriteTeams
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
    selectedTeamKey: String? = null,
    forceLiveOnly: Boolean = false,
    onClearSelectedLeague: () -> Unit = {},
    onClearSelectedTeam: () -> Unit = {},
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

    val favoriteTeams = remember {
        mutableStateListOf<String>().apply {
            addAll(
                preferences.getStringSet("favorite_teams", emptySet())
                    ?: emptySet()
            )
        }
    }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "favorite_leagues" -> {
                    val updated = prefs.getStringSet("favorite_leagues", emptySet()).orEmpty()
                    favorites.clear()
                    favorites.addAll(updated)
                }
                "favorite_teams" -> {
                    val updated = prefs.getStringSet("favorite_teams", emptySet()).orEmpty()
                    favoriteTeams.clear()
                    favoriteTeams.addAll(updated)
                }
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

    val favoriteTeamSet = remember(favoriteTeams.toList()) { favoriteTeams.toSet() }

    val filteredMatches = remember(
        matches,
        searchQuery,
        activeFilter,
        favorites.toList(),
        favoriteTeamSet,
        savedTips.toList(),
        selectedLeagueKey,
        selectedTeamKey,
        forceLiveOnly
    ) {
        val q = searchQuery.trim().lowercase(Locale("hu", "HU"))
        matches.filter { match ->
            val leagueKey = match.league.ifBlank { "Ismeretlen bajnokság" }
            val translatedLeague = translateLeagueName(leagueKey).lowercase(Locale("hu", "HU"))

            val matchesSelectedLeague = selectedLeagueKey == null || leagueKey == selectedLeagueKey
            val matchesSelectedTeam = selectedTeamKey == null ||
                favoriteTeamKey(match.homeTeam) == selectedTeamKey ||
                favoriteTeamKey(match.awayTeam) == selectedTeamKey
            val matchesLiveQuick = !forceLiveOnly || isMatchLive(match)

            val matchesFilter = when (activeFilter) {
                LeagueFilter.ALL -> true
                LeagueFilter.FAVORITES -> leagueKey in favorites || hasFavoriteTeamInMatch(match, favoriteTeamSet)
                LeagueFilter.LIVE -> isMatchLive(match)
                LeagueFilter.TIPS -> hasTipForMatch(match, savedTips)
                LeagueFilter.FINISHED -> isMatchFinished(match)
            }

            val matchesSearch = q.isEmpty() ||
                leagueKey.lowercase(Locale("hu", "HU")).contains(q) ||
                translatedLeague.contains(q) ||
                match.homeTeam.lowercase(Locale("hu", "HU")).contains(q) ||
                match.awayTeam.lowercase(Locale("hu", "HU")).contains(q) ||
                match.matchTime.lowercase(Locale("hu", "HU")).contains(q) ||
                match.rawStatus.lowercase(Locale("hu", "HU")).contains(q) ||
                match.date.lowercase(Locale("hu", "HU")).contains(q)

            matchesSelectedLeague && matchesSelectedTeam && matchesLiveQuick && matchesFilter && matchesSearch
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
                borderColor = GoldAccent.copy(alpha = 0.5f),
                gradientColors = listOf(GoldAccent.copy(alpha = 0.16f), GlassSurface)
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
                        color = GoldAccent,
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

        if (selectedTeamKey != null) {
            LeagueGlassCard(
                borderColor = GoldAccent.copy(alpha = 0.5f),
                gradientColors = listOf(GoldAccent.copy(alpha = 0.16f), GlassSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♥ Szűrt csapat: $selectedTeamKey",
                        color = GoldAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "ÖSSZES CSAPAT",
                        color = LeagueCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(GlassDark)
                            .clickable { onClearSelectedTeam() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LeagueGlassCard(
            borderColor = LeagueCyan.copy(alpha = 0.34f),
            gradientColors = listOf(GlassHighlight, GlassSurface)
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
                            text = "Keresés csapatra, ligára vagy időpontra...",
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
                            LeagueFilter.FAVORITES -> GoldAccent
                            LeagueFilter.LIVE -> AccentColor
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
                            color = GoldAccent,
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
                gradientColors = listOf(GlassHighlight, GlassSurface)
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
                        selectedLeagueKey != null ||
                        selectedTeamKey != null ||
                        forceLiveOnly
                    ) {
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                activeFilter = LeagueFilter.ALL
                                onClearSelectedLeague()
                                onClearSelectedTeam()
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
            gradientColors = listOf(Color(0xFF1F3859).copy(alpha = 0.80f), GlassSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val newState = !allExpanded
                            sortedEntries.forEach { entry ->
                                expandedStates[entry.key] = newState
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AccentColor.copy(alpha = 0.20f)),
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
                        color = AccentColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (favorites.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "★ CSAK KEDV.",
                        color = GoldAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(GlassDark)
                            .clickable {
                                sortedEntries.forEach { entry ->
                                    expandedStates[entry.key] = entry.key in favorites
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
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
                    listOf(GoldAccent.copy(alpha = 0.18f), GlassSurface)
                } else {
                    listOf(accent.copy(alpha = 0.12f), GlassSurface)
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
                            color = if (isFavorite) GoldAccent else Color.White,
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
                                        .background(AccentColor.copy(alpha = 0.22f))
                                        .padding(horizontal = 7.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$liveCount ÉLŐ",
                                        color = AccentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isFavorite) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(GoldAccent.copy(alpha = 0.22f))
                                        .padding(horizontal = 7.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "KEDVENC",
                                        color = GoldAccent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = if (isFavorite) "★" else "☆",
                        color = GoldAccent,
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
                                hasFavoriteTeam = hasFavoriteTeamInMatch(match, favoriteTeamSet),
                                onClick = { onSelectMatch(match) }
                            )
                        }
                    }
                }
            }
        }
    }
}
