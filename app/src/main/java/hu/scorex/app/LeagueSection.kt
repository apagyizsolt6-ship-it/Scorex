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

private val LeagueBackground = Color(0xFF152238)
private val LeagueGold = Color(0xFFFFC857)
private val LeagueGreen = Color(0xFF35E0A1)
private val LeagueCyan = Color(0xFF38BDF8)
private val LeaguePurple = Color(0xFFA78BFA)
private val LeagueCoral = Color(0xFFFF7A90)
private val LeagueText = Color(0xFF94A3B8)

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
    FAVORITES("Kedvencek"),
    LIVE("Élő"),
    FINISHED("Befejezett")
}

fun isMatchLive(match: ScorexMatch): Boolean {
    val finished = match.matchTime.equals("FT", ignoreCase = true) ||
        match.rawStatus.equals("FT", ignoreCase = true)
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
        match.rawStatus.equals("FT", ignoreCase = true)
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

@Composable
fun LeagueSection(
    matches: List<ScorexMatch>,
    preferences: SharedPreferences,
    onSelectMatch: (ScorexMatch) -> Unit = {}
) {
    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(
                preferences.getStringSet(
                    "favorite_leagues",
                    emptySet()
                ) ?: emptySet()
            )
        }
    }

    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>()
    }

    var searchQuery by remember {
        mutableStateOf("")
    }

    var activeFilter by remember {
        mutableStateOf(LeagueFilter.ALL)
    }

    val hungarianCollator = remember {
        Collator.getInstance(Locale("hu", "HU")).apply {
            strength = Collator.PRIMARY
        }
    }

    val filteredMatches = remember(matches, searchQuery, activeFilter, favorites.toList()) {
        val q = searchQuery.trim().lowercase(Locale("hu", "HU"))
        matches.filter { match ->
            val leagueKey = match.league.ifBlank { "Ismeretlen bajnokság" }
            val translatedLeague = translateLeagueName(leagueKey).lowercase(Locale("hu", "HU"))

            val matchesFilter = when (activeFilter) {
                LeagueFilter.ALL -> true
                LeagueFilter.FAVORITES -> leagueKey in favorites
                LeagueFilter.LIVE -> isMatchLive(match)
                LeagueFilter.FINISHED -> isMatchFinished(match)
            }

            val matchesSearch = q.isEmpty() ||
                leagueKey.lowercase(Locale("hu", "HU")).contains(q) ||
                translatedLeague.contains(q) ||
                match.homeTeam.lowercase(Locale("hu", "HU")).contains(q) ||
                match.awayTeam.lowercase(Locale("hu", "HU")).contains(q)

            matchesFilter && matchesSearch
        }
    }

    val groupedMatches = remember(filteredMatches) {
        filteredMatches
            .groupBy { it.league.ifBlank { "Ismeretlen bajnokság" } }
            .mapValues { (_, leagueList) ->
                leagueList.sortedByDescending { isMatchLive(it) }
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = LeagueCyan.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(15.dp)
                ),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(
                containerColor = LeagueBackground
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = LeagueCyan,
                        unfocusedBorderColor = Color(0xFF283B57),
                        cursorColor = LeagueCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LeagueFilter.entries.forEach { filter ->
                        val selected = activeFilter == filter
                        val chipColor = when (filter) {
                            LeagueFilter.ALL -> LeagueCyan
                            LeagueFilter.FAVORITES -> LeagueGold
                            LeagueFilter.LIVE -> LeagueGreen
                            LeagueFilter.FINISHED -> LeaguePurple
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) chipColor.copy(alpha = 0.22f)
                                    else Color(0xFF101A2B)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) chipColor else Color(0xFF253752),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { activeFilter = filter }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                color = if (selected) chipColor else LeagueText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (sortedEntries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LeagueBackground
                )
            ) {
                Text(
                    text = "Nincs találat a megadott szűrőkre vagy keresésre.",
                    color = LeagueText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(18.dp)
                )
            }
            return@Column
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = LeagueCyan.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(15.dp)
                ),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1A2D4A),
                                Color(0xFF15243B)
                            )
                        )
                    )
                    .clickable {
                        val newState = !allExpanded
                        sortedEntries.forEach { entry ->
                            expandedStates[entry.key] = newState
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LeagueGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚽",
                        fontSize = 19.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Összes bajnokság (${sortedEntries.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (allExpanded) {
                            "Minden bajnokság nyitva • ${favorites.size} kedvenc"
                        } else {
                            "Bajnokságok összecsukva • ${favorites.size} kedvenc"
                        },
                        color = LeagueText,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = if (allExpanded) "⌃" else "⌄",
                    color = LeagueGreen,
                    fontSize = 28.sp,
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isFavorite) 1.5.dp else 1.dp,
                        color = accent.copy(alpha = if (isFavorite) 0.55f else 0.22f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LeagueBackground
                )
            ) {
                Column {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isFavorite) {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            LeagueGold.copy(alpha = 0.14f),
                                            LeagueBackground
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            accent.copy(alpha = 0.08f),
                                            LeagueBackground
                                        )
                                    )
                                }
                            )
                            .clickable {
                                expandedStates[league] = !expanded
                            }
                            .padding(end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(64.dp)
                                .background(accent)
                        )

                        Spacer(Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isFavorite) "★" else "⚽",
                                color = accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = translatedLeague,
                                color = if (isFavorite) LeagueGold else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${leagueMatches.size} mérkőzés",
                                        color = accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (liveCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(LeagueGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
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
                                            .background(LeagueGold.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
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
                            fontSize = 23.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    if (isFavorite) {
                                        favorites.remove(league)
                                    } else {
                                        favorites.add(league)
                                    }

                                    preferences.edit()
                                        .putStringSet(
                                            "favorite_leagues",
                                            favorites.toSet()
                                        )
                                        .apply()
                                }
                                .padding(4.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = if (expanded) "⌃" else "⌄",
                            color = accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (expanded) {
                        HorizontalDivider(
                            color = Color(0xFF263750)
                        )

                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            leagueMatches.forEach { match ->
                                LiveMatchCard(
                                    match = match,
                                    onClick = { onSelectMatch(match) }
                                )
                            }
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
        "Cup" to "Kupa",
        "Women" to "Női",
        "Woman" to "Női",
        "Youth" to "Utánpótlás",
        "Reserves" to "Tartalék",
        "Reserve" to "Tartalék",
        "U21" to "U21",
        "U19" to "U19",
        "U18" to "U18",
        "U17" to "U17",
        "U16" to "U16",
        "U15" to "U15",
        "First Division" to "Első osztály",
        "Second Division" to "Másodosztály",
        "Third Division" to "Harmadosztály",
        "Premier League" to "Premier League",
        "Champions League" to "Bajnokok Ligája",
        "Europa League" to "Európa-liga",
        "Conference League" to "Konferencia-liga",
        "Group Stage" to "Csoportkör",
        "Round of 16" to "Nyolcaddöntő",
        "Quarter-finals" to "Negyeddöntő",
        "Semi-finals" to "Elődöntő",
        "Final" to "Döntő"
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
