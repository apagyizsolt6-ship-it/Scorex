package hu.scorex.app

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ApiSettingsScreen(
    preferences: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var apiKey by remember {
        mutableStateOf(
            preferences.getString("statpal_api_key", "").orEmpty()
        )
    }

    var selectedInterval by remember {
        mutableIntStateOf(
            preferences.getInt("refresh_interval_sec", 15).coerceIn(15, 120)
        )
    }

    var notifyFavoriteTeams by remember {
        mutableStateOf(preferences.getBoolean("notify_favorite_teams", true))
    }

    var notifySavedTips by remember {
        mutableStateOf(preferences.getBoolean("notify_saved_tips", true))
    }

    var favoriteTeams by remember {
        mutableStateOf(
            preferences.getStringSet("favorite_teams", emptySet()).orEmpty().toList().sorted()
        )
    }

    var statusText by remember {
        mutableStateOf("Add meg a StatPal API-kulcsodat.")
    }

    var isTesting by remember {
        mutableStateOf(false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) {
            "Értesítési engedély megadva."
        } else {
            "Az értesítési engedély elutasítva."
        }
    }

    val scope = rememberCoroutineScope()
    val intervalOptions = listOf(15, 30, 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
            Text(text = "← VISSZA", color = AccentColor)
        }

        Column {
            Text(
                text = "SCOREX",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "BEÁLLÍTÁSOK ÉS ÉRTESÍTÉSEK",
                color = AccentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        GlassPanel(
            borderColor = AccentColor.copy(alpha = 0.30f),
            gradientColors = listOf(GlassTop, GlassSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "StatPal API-kulcs",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API-kulcs") },
                    placeholder = { Text("Írd be a kulcsodat") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GlassWell,
                        unfocusedContainerColor = GlassWell,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = SecondaryColor.copy(alpha = 0.5f),
                        focusedLabelColor = AccentColor,
                        unfocusedLabelColor = SecondaryColor,
                        cursorColor = AccentColor
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Automatikus frissítési időköz",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervalOptions.forEach { sec ->
                        val active = selectedInterval == sec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) AccentColor.copy(alpha = 0.22f)
                                    else GlassWell
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) AccentColor else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedInterval = sec
                                    preferences.edit()
                                        .putInt("refresh_interval_sec", sec)
                                        .apply()
                                    statusText = "Frissítési időköz: $sec mp."
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$sec mp",
                                color = if (active) AccentColor else SecondaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val key = apiKey.trim()
                        if (key.isBlank()) {
                            statusText = "Hiányzik az API-kulcs."
                        } else {
                            apiKey = key
                            preferences.edit()
                                .putString("statpal_api_key", key)
                                .putInt("refresh_interval_sec", selectedInterval)
                                .apply()
                            statusText = "Beállítások sikeresen elmentve."
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
                        preferences.edit().remove("statpal_api_key").apply()
                        statusText = "API-kulcs törölve."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("KULCS TÖRLÉSE", color = Color.White)
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
                                        val resultMatches = StatPalApi.fetchLiveMatches(key)
                                        "Sikeres kapcsolat! Mérkőzések: ${resultMatches.size}."
                                    } catch (exception: Exception) {
                                        exception.message ?: "Kapcsolódási hiba."
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
                        containerColor = GlassWell,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isTesting) "TESZTELÉS..." else "STATPAL KAPCSOLAT TESZTELÉSE"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = statusText,
                    color = if (
                        statusText.contains("hiba", true) ||
                        statusText.contains("hibás", true) ||
                        statusText.contains("elutasítva", true)
                    ) ErrorColor else AccentColor,
                    fontSize = 12.sp
                )
            }
        }

        GlassPanel(
            borderColor = CyanAccent.copy(alpha = 0.30f),
            gradientColors = listOf(GlassTop, GlassSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ÉLŐ GÓL- ÉS EREDMÉNYÉRTESÍTÉSEK",
                    color = CyanAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kedvenc csapatok eseményei",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notifyFavoriteTeams,
                        onCheckedChange = { checked ->
                            notifyFavoriteTeams = checked
                            preferences.edit().putBoolean("notify_favorite_teams", checked).apply()
                            if (checked && !ScorexNotifier.hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tippszelvényen szereplő meccsek",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notifySavedTips,
                        onCheckedChange = { checked ->
                            notifySavedTips = checked
                            preferences.edit().putBoolean("notify_saved_tips", checked).apply()
                            if (checked && !ScorexNotifier.hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (!ScorexNotifier.hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ScorexNotifier.sendNotification(
                                context = context,
                                notificationId = 1001,
                                title = "SCOREX • TESZT ÉRTESÍTÉS",
                                body = "Az élő gól- és eredményjelzés aktív!"
                            )
                            statusText = "Teszt értesítés elküldve."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("TESZT ÉRTESÍTÉS KÜLDÉSE", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (favoriteTeams.isNotEmpty()) {
            GlassPanel(
                borderColor = GoldAccent.copy(alpha = 0.32f),
                gradientColors = listOf(GoldAccent.copy(alpha = 0.14f), GlassSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KEDVENC CSAPATOK (${favoriteTeams.size})",
                            color = GoldAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ÖSSZES TÖRLÉSE",
                            color = ErrorColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(GlassWell)
                                .clickable {
                                    preferences.edit().remove("favorite_teams").apply()
                                    favoriteTeams = emptyList()
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    favoriteTeams.forEach { teamKey ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(GlassWell)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "♥ $teamKey",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Eltávolít",
                                color = ErrorColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    val updated = favoriteTeams.toMutableSet().apply { remove(teamKey) }
                                    preferences.edit().putStringSet("favorite_teams", updated).apply()
                                    favoriteTeams = updated.toList().sorted()
                                }
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Az API-kulcsot az alkalmazás helyben tárolja. Nem szükséges a GitHub-forráskódba beírnod.",
            color = SecondaryColor,
            fontSize = 11.sp
        )

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
