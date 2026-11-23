
package hu.scorex.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences(
            "scorex_settings",
            MODE_PRIVATE
        )

        setContent {
            ScorexApp(preferences)
        }
    }
}

private val BackgroundColor = Color(0xFF080D18)
private val CardColor = Color(0xFF121C2D)
private val AccentColor = Color(0xFF35E0A1)
private val SecondaryColor = Color(0xFF8B9BB4)

@Composable
fun ScorexApp(preferences: SharedPreferences) {

    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            if (showSettings) {
                ApiSettingsScreen(
                    preferences = preferences,
                    onBack = {
                        showSettings = false
                    }
                )
            } else {
                HomeScreen(
                    onOpenSettings = {
                        showSettings = true
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCORE",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = "X",
                        color = AccentColor,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = "ÉLD ÁT A FUTBALLT",
                    color = SecondaryColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = BackgroundColor
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "⚙",
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ÉLŐ MÉRKŐZÉSEK",
            color = AccentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        MatchCard(
            league = "Premier League",
            homeTeam = "Manchester City",
            awayTeam = "Liverpool",
            homeScore = "–",
            awayScore = "–",
            matchTime = "Minta"
        )

        Spacer(modifier = Modifier.height(12.dp))

        MatchCard(
            league = "La Liga",
            homeTeam = "Real Madrid",
            awayTeam = "Barcelona",
            homeScore = "–",
            awayScore = "–",
            matchTime = "Minta"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CardColor
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "SCOREX TIPPEK",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Hamarosan elérhető a saját tippfogadási szelvényed.",
                    color = SecondaryColor,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "A mérkőzések jelenleg bemutatóadatok.",
            color = SecondaryColor,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ApiSettingsScreen(
    preferences: SharedPreferences,
    onBack: () -> Unit
) {
    var apiKey by remember {
        mutableStateOf(
            preferences.getString("statpal_api_key", "") ?: ""
        )
    }

    var statusText by remember {
        mutableStateOf("Add meg a StatPal API-kulcsodat.")
    }

    var isTesting by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "← VISSZA",
            color = AccentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "SCOREX",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = "API BEÁLLÍTÁSOK",
            color = AccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CardColor
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "StatPal API-kulcs",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("API-kulcs")
                    },
                    placeholder = {
                        Text("Írd be a kulcsodat")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = SecondaryColor,
                        focusedLabelColor = AccentColor,
                        unfocusedLabelColor = SecondaryColor,
                        cursorColor = AccentColor
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        preferences.edit()
                            .putString(
                                "statpal_api_key",
                                apiKey.trim()
                            )
                            .apply()

                        statusText = "API-kulcs elmentve."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = BackgroundColor
                    )
                ) {
                    Text("KULCS MENTÉSE")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        apiKey = ""
                        preferences.edit()
                            .remove("statpal_api_key")
                            .apply()

                        statusText = "API-kulcs törölve."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "KULCS TÖRLÉSE",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val key = apiKey.trim()

                        if (key.isBlank()) {
                            statusText = "Először add meg az API-kulcsot."
                        } else {
                            preferences.edit()
                                .putString(
                                    "statpal_api_key",
                                    key
                                )
                                .apply()

                            isTesting = true
                            statusText = "Kapcsolódás a StatPal API-hoz..."

                            testStatPalConnection(
                                key = key,
                                onResult = { result ->
                                    statusText = result
                                    isTesting = false
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF263750),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isTesting) {
                            "TESZTELÉS..."
                        } else {
                            "STATPAL KAPCSOLAT TESZTELÉSE"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusText,
                    color = AccentColor,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Az API-kulcs az alkalmazás saját tárhelyére kerül mentésre.",
            color = SecondaryColor,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = BackgroundColor
            )
        ) {
            Text("VISSZA A SCOREXHEZ")
        }
    }
}

fun testStatPalConnection(
    key: String,
    onResult: (String) -> Unit
) {
    Thread {
        var result: String

        try {
            val encodedKey = URLEncoder.encode(
                key,
                "UTF-8"
            )

            val url = URL(
                "https://statpal.io/api/v2/soccer/matches/live" +
                    "?access_key=$encodedKey"
            )

            val connection = url.openConnection()
                as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode

            result = when (responseCode) {
                200 -> "Sikeres kapcsolat! A StatPal API válaszolt."
                401, 403 -> "Hibás API-kulcs vagy nincs hozzáférés."
                429 -> "Elérted az API lekérési korlátját."
                else -> "API válasz: HTTP $responseCode"
            }

            connection.disconnect()

        } catch (e: Exception) {
            result = "Kapcsolódási hiba: ${e.message ?: "Ismeretlen hiba"}"
        }

        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).post {
            onResult(result)
        }
    }.start()
}

@Composable
fun MatchCard(
    league: String,
    homeTeam: String,
    awayTeam: String,
    homeScore: String,
    awayScore: String,
    matchTime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = league,
                    color = SecondaryColor,
                    fontSize = 12.sp
                )

                Text(
                    text = matchTime,
                    color = AccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = homeTeam,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = awayTeam,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = homeScore,
                        color = AccentColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = awayScore,
                        color = AccentColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
