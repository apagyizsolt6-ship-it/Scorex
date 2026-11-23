
package hu.scorex.app

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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

    val groupedMatches = matches.groupBy {
        it.league.ifBlank { "Ismeretlen bajnokság" }
    }.toSortedMap()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groupedMatches.forEach { (league, leagueMatches) ->

            var expanded by remember(league) {
                mutableStateOf(true)
            }

            val isFavorite = league in favorites

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
                                expanded = !expanded
                            }
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFavorite) "★" else "⚽",
                            color = if (isFavorite) LeagueGold else LeagueGreen,
                            fontSize = 19.sp
                        )

                        Spacer(Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = league,
                                color = if (isFavorite) LeagueGold else Color.White,
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
