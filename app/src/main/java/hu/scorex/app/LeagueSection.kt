
package hu.scorex.app

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LeagueBackground = Color(0xFF17243A)
private val LeagueGold = Color(0xFFFFC857)
private val LeagueGreen = Color(0xFF35E0A1)
private val LeagueText = Color(0xFF8B9BB4)

@Composable
fun LeagueSection(
    matches: List<ScorexMatch>,
    preferences: SharedPreferences
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

    val groupedMatches = matches.groupBy {
        it.league.ifBlank { "Ismeretlen bajnokság" }
    }.toSortedMap()

    val allExpanded = groupedMatches.keys.all { league ->
        expandedStates[league] ?: true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Összes bajnokság nyitása és csukása
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            colors = CardDefaults.cardColors(
                containerColor = LeagueBackground
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newState = !allExpanded

                        groupedMatches.keys.forEach { league ->
                            expandedStates[league] = newState
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚽",
                    fontSize = 20.sp
                )

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Összes bajnokság",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (allExpanded) {
                            "Minden bajnokság nyitva"
                        } else {
                            "Bajnokságok összecsukva"
                        },
                        color = LeagueText,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = if (allExpanded) "⌃" else "⌄",
                    color = LeagueGreen,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        groupedMatches.forEach { (league, leagueMatches) ->

            val expanded = expandedStates[league] ?: true
            val isFavorite = league in favorites
            val translatedLeague = translateLeagueName(league)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LeagueBackground
                )
            ) {
                Column {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedStates[league] = !expanded
                            }
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = if (isFavorite) "★" else "⚽",
                            color = if (isFavorite) {
                                LeagueGold
                            } else {
                                LeagueGreen
                            },
                            fontSize = 19.sp
                        )

                        Spacer(Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = translatedLeague,
                                color = if (isFavorite) {
                                    LeagueGold
                                } else {
                                    Color.White
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "${leagueMatches.size} mérkőzés",
                                color = LeagueText,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = if (isFavorite) "★" else "☆",
                            color = LeagueGold,
                            fontSize = 23.sp,
                            modifier = Modifier.clickable {
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
                        )

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = if (expanded) "⌃" else "⌄",
                            color = LeagueGreen,
                            fontSize = 22.sp
                        )
                    }

                    if (expanded) {
                        HorizontalDivider(
                            color = Color(0xFF263750)
                        )

                        leagueMatches.forEach { match ->
                            LiveMatchCard(match)

                            HorizontalDivider(
                                color = Color(0xFF263750)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun translateLeagueName(original: String): String {

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
        "Conference League" to "Konferencia-liga"
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
