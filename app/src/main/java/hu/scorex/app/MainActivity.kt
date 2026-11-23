
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
private val ErrorColor = Color(0xFFFF7777)

@Composable
fun ScorexApp(preferences: SharedPreferences) {

    var showSettings by remember {
        mutableStateOf(false)
    }

    var showApiData by remember {
        mutableStateOf(false)
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            when {
                showApiData -> {
                    ApiDataScreen(
                        onBack = {
                            showApiData = false
                        }
                    )
                }

                showSettings -> {
                    ApiSettingsScreen(
                        preferences = preferences,
                        onBack = {
                            showSettings = false
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        preferences = preferences,
                        onOpenSettings = {
                            showSettings = true
                        },
                        onOpenApiData = {
                            showApiData = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    preferences: SharedPreferences,
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

    var matches by remember {
        mutableStateOf<List<ScorexMatch>>(emptyList())
    }

    var statusMessage by remember {
        mutableStateOf("Élő mérkőzések betöltése...")
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

    LaunchedEffect(apiKey, refreshTrigger) {

        if (apiKey.isBlank()) {
            matches = emptyList()
            statusMessage = "Állítsd be a StatPal API-kulcsodat."
            return@LaunchedEffect
        }

        while (true) {

            isLoading = true
            statusMessage = "Kapcsolódás a StatPalhoz..."

            try {

                val result = withContext(Dispatchers.IO) {
                    StatPalApi.fetchLiveMatches(apiKey)
                }

                matches = result

                statusMessage = if (result.isEmpty()) {
                    "Jelenleg nincs élő mérkőzés."
                } else {
                    "${result.size} élő mérkőzés betöltve."
                }

                lastUpdated = java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())

            } catch (exception: Exception) {

                statusMessage = exception.message
                    ?: "Ismeretlen kapcsolódási hiba."

            } finally {
                isLoading = false
            }

            delay(15_000L)
        }
    }

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
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("⚙", fontSize = 23.sp)
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "ÉLŐ MÉRKŐZÉSEK",
                color = AccentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                onClick = {
                    refreshTrigger++
                },
                enabled = !isLoading && apiKey.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "FRISSÍTÉS",
                    color = AccentColor,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    color = SecondaryColor,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (matches.isEmpty()) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CardColor
                ),
                shape = RoundedCornerShape(20.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = if (isLoading) "⚽" else "📡",
                        fontSize = 36.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (apiKey.isBlank()) {
                            "STATPAL API-KULCS SZÜKSÉGES"
                        } else if (isLoading) {
                            "MÉRKŐZÉSEK BETÖLTÉSE"
                        } else {
                            "NINCS ÉLŐ MÉRKŐZÉS"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = statusMessage,
                        color = SecondaryColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    if (apiKey.isBlank()) {

                        Spacer(modifier = Modifier.height(15.dp))

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

            LeagueSection(
                matches = matches,
                preferences = preferences
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenApiData,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "API ADATOK MEGTEKINTÉSE",
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        }

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
                    text = "Saját tippjeid, mérkőzés-előrejelzések és fogadási szelvény.",
                    color = SecondaryColor,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SCOREX • FOOTBALL CENTER",
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryColor,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ApiDataScreen(
    onBack: () -> Unit
) {

    val rawResponse = StatPalApi.lastRawResponse

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        OutlinedButton(onClick = onBack) {
            Text(
                text = "← VISSZA",
                color = AccentColor
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "STATPAL API ADATOK",
            color = AccentColor,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Itt látható a legutóbbi API-válasz. A bajnokság mezőjét ebből fogjuk azonosítani.",
            color = SecondaryColor,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CardColor
            ),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                Text(
                    text = if (rawResponse.isBlank()) {
                        "Még nincs eltárolt API-válasz."
                    } else {
                        rawResponse
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onBack()
            },
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

@Composable
fun LiveMatchCard(match: ScorexMatch) {

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = match.league,
                    color = SecondaryColor,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = match.matchTime,
                    color = AccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = match.homeTeam,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = match.awayTeam,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {

                    Text(
                        text = match.homeScore,
                        color = AccentColor,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = match.awayScore,
                        color = AccentColor,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ApiSettingsScreen(
    preferences: SharedPreferences,
    onBack: () -> Unit
) {

    var apiKey by remember {
        mutableStateOf(
            preferences.getString(
                "statpal_api_key",
                ""
            ).orEmpty()
        )
    }

    var statusText by remember {
        mutableStateOf("Add meg a StatPal API-kulcsodat.")
    }

    var isTesting by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        OutlinedButton(onClick = onBack) {
            Text(
                text = "← VISSZA",
                color = AccentColor
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {

                        val key = apiKey.trim()

                        if (key.isBlank()) {

                            statusText = "Hiányzik az API-kulcs."

                        } else {

                            apiKey = key

                            preferences.edit()
                                .putString(
                                    "statpal_api_key",
                                    key
                                )
                                .apply()

                            statusText = "API-kulcs sikeresen elmentve."
                        }
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
                        "KULCS TÖRLÉSE",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {

                        val key = apiKey.trim()

                        if (key.isBlank()) {

                            statusText = "Először add meg az API-kulcsot."

                        } else if (!isTesting) {

                            isTesting = true
                            statusText = "StatPal kapcsolat tesztelése..."

                            scope.launch {

                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val resultMatches =
                                            StatPalApi.fetchLiveMatches(key)

                                        "Sikeres kapcsolat! Élő meccsek: ${resultMatches.size}."

                                    } catch (exception: Exception) {
                                        exception.message
                                            ?: "Kapcsolódási hiba."
                                    }
                                }

                                statusText = result
                                isTesting = false
                            }
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
                    color = if (
                        statusText.contains("hiba", true) ||
                        statusText.contains("hibás", true)
                    ) ErrorColor else AccentColor,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Az API-kulcsot az alkalmazás helyben tárolja. Nem szükséges a GitHub-forráskódba beírnod.",
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
