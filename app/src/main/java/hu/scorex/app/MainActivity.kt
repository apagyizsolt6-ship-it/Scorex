package hu.scorex.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

internal val BackgroundColor = Color(0xFF060B14)
internal val SurfaceColor = Color(0xFF0E1726)
internal val GlassTop = Color(0xFF17243A)
internal val GlassSurface = Color(0xFF101B2D)
internal val GlassWell = Color(0xFF0A1220)
internal val AccentColor = Color(0xFF00E676)
internal val CyanAccent = Color(0xFF29B6F6)
internal val GoldAccent = Color(0xFFFFD54F)
internal val PurpleAccent = Color(0xFFB388FF)
internal val SecondaryColor = Color(0xFF9FB0C7)
internal val ErrorColor = Color(0xFFFF5252)
internal val WarningColor = Color(0xFFFFB74D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences("scorex_prefs", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundColor
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF091324),
                                        BackgroundColor,
                                        Color(0xFF050911)
                                    )
                                )
                            )
                    ) {
                        ScorexApp(preferences = preferences)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.10f),
    gradientColors: List<Color> = listOf(GlassSurface, GlassWell),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(gradientColors))
            .androidx.compose.foundation.border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        content()
    }
}

@Composable
internal fun ScorexSectionBadge(
    text: String,
    tint: Color = AccentColor,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .androidx.compose.foundation.border(
                width = 1.dp,
                color = tint.copy(alpha = 0.34f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

internal fun isMatchLive(match: ScorexMatch): Boolean {
    val t = match.matchTime.trim().uppercase()
    val s = match.rawStatus.trim().uppercase()
    if (t == "FT" || t == "AET" || t == "PEN" || t == "ELHALASZTVA" || t == "TÖRÖLVE" || t == "FÉLBESZAKADT") {
        return false
    }
    if (s == "FT" || s == "AET" || s == "PEN" || s == "PST" || s == "CANC" || s == "CANCELLED" || s == "ABD" || s == "ABANDONED" || s == "NS" || s == "TBD") {
        return false
    }
    if (t == "HT" || t == "ÉLŐ" || s == "HT" || s == "LIVE" || s == "IN PLAY" || s == "1H" || s == "2H" || s == "ET" || s == "BT" || s == "P") {
        return true
    }
    if (t.endsWith("'") || t.contains("+")) {
        return true
    }
    return s.all { it.isDigit() } && s.isNotBlank()
}

internal fun isMatchFinished(match: ScorexMatch): Boolean {
    val t = match.matchTime.trim().uppercase()
    val s = match.rawStatus.trim().uppercase()
    return t == "FT" || t == "AET" || t == "PEN" || s == "FT" || s == "AET" || s == "PEN"
}

internal fun translateLeagueName(rawLeague: String): String {
    val trimmed = rawLeague.trim()
    if (trimmed.isBlank()) return "Ismeretlen bajnokság"

    val lower = trimmed.lowercase()
    return when {
        "premier league" in lower && ("england" in lower || "en" in lower || ":" !in lower) -> "Anglia: Premier League"
        "championship" in lower && "england" in lower -> "Anglia: Championship"
        "la liga" in lower || "primera division" in lower -> "Spanyolország: La Liga"
        "serie a" in lower && ("italy" in lower || ":" !in lower) -> "Olaszország: Serie A"
        "bundesliga" in lower && "2." !in lower -> "Németország: Bundesliga"
        "2. bundesliga" in lower -> "Németország: 2. Bundesliga"
        "ligue 1" in lower -> "Franciaország: Ligue 1"
        "nb i" in lower || "otp bank liga" in lower || "hungary" in lower && "liga" in lower -> "Magyarország: NB I"
        "champions league" in lower -> "Nemzetközi: Bajnokok Ligája"
        "europa league" in lower && "conference" !in lower -> "Nemzetközi: Európa-liga"
        "conference league" in lower -> "Nemzetközi: Konferencia-liga"
        "eredivisie" in lower -> "Hollandia: Eredivisie"
        "primeira liga" in lower || "liga portugal" in lower -> "Portugália: Liga Portugal"
        else -> trimmed
    }
}

internal fun favoriteTeamKey(teamName: String): String {
    return normalizeTeamKey(teamName)
}

internal fun normalizeTeamKey(teamName: String): String {
    val normalized = Normalizer.normalize(teamName.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return normalized
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\b(fc|cf|sc|ac|as|afc|fk|sk|nk|ud|cd|sd|rc|rcd|ca|ssc|us|sv|vfb|fsv|rb|inter|united|city)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
fun ScorexApp(preferences: SharedPreferences) {
    var apiKey by remember {
        mutableStateOf(preferences.getString("statpal_api_key", "").orEmpty())
    }

    var matches by remember { mutableStateOf<List<ScorexMatch>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastUpdatedText by remember { mutableStateOf("") }

    var showSettings by remember { mutableStateOf(false) }
    var showApiData by remember { mutableStateOf(false) }
    var showLeagueHub by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<ScorexMatch?>(null) }
    var selectedProfile by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var savedTips by remember {
        mutableStateOf(
            preferences.getStringSet("saved_tips", emptySet()).orEmpty().toList().sorted()
        )
    }

    fun toggleTip(tip: String) {
        val updated = savedTips.toMutableSet()
        if (tip in updated) {
            updated.remove(tip)
        } else {
            updated.add(tip)
        }
        preferences.edit().putStringSet("saved_tips", updated).apply()
        savedTips = updated.toList().sorted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        when {
            selectedProfile != null -> {
                val (kind, id, title) = selectedProfile!!
                EntityProfileScreen(
                    initialKind = kind,
                    initialId = id,
                    initialTitle = title,
                    preferences = preferences,
                    onBack = { selectedProfile = null }
                )
            }

            selectedMatch != null -> {
                MatchDetailScreen(
                    match = selectedMatch!!,
                    preferences = preferences,
                    savedTips = savedTips,
                    onToggleTip = ::toggleTip,
                    onOpenProfile = { kind, id, title ->
                        selectedProfile = Triple(kind, id, title)
                    },
                    onBack = { selectedMatch = null }
                )
            }

            showLeagueHub -> {
                LeagueHubScreen(
                    preferences = preferences,
                    onBack = { showLeagueHub = false }
                )
            }

            showApiData -> {
                ApiDataScreen(
                    onBack = { showApiData = false }
                )
            }

            showSettings -> {
                SettingsScreen(
                    preferences = preferences,
                    currentApiKey = apiKey,
                    onSaveApiKey = { newKey ->
                        val cleaned = newKey.trim()
                        preferences.edit().putString("statpal_api_key", cleaned).apply()
                        apiKey = cleaned
                        showSettings = false
                    },
                    onBack = { showSettings = false }
                )
            }

            else -> {
                HomeScreen(
                    preferences = preferences,
                    apiKey = apiKey,
                    matches = matches,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    lastUpdatedText = lastUpdatedText,
                    savedTips = savedTips,
                    onMatchesLoaded = { loaded, timeLabel ->
                        matches = loaded
                        errorMessage = ""
                        lastUpdatedText = timeLabel
                    },
                    onError = { msg ->
                        errorMessage = msg
                    },
                    onLoadingChange = { loading ->
                        isLoading = loading
                    },
                    onOpenSettings = { showSettings = true },
                    onOpenApiData = { showApiData = true },
                    onOpenLeagueHub = { showLeagueHub = true },
                    onOpenMatch = { match -> selectedMatch = match },
                    onToggleTip = ::toggleTip
                )
            }
        }
    }
}
