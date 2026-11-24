package hu.scorex.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            .border(width = 1.dp, color = borderColor, shape = shape)
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
            .border(
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
    // Speciális, gyakran előforduló bajnokságok (nem mindig "Ország: Liga" formátumúak)
    when {
        "premier league" in lower && ("england" in lower || "en" in lower || ":" !in lower) -> return "Anglia: Premier League"
        "championship" in lower && "england" in lower -> return "Anglia: Championship"
        "la liga" in lower || "primera division" in lower -> return "Spanyolország: La Liga"
        "serie a" in lower && ("italy" in lower || ":" !in lower) -> return "Olaszország: Serie A"
        "bundesliga" in lower && "2." !in lower -> return "Németország: Bundesliga"
        "2. bundesliga" in lower -> return "Németország: 2. Bundesliga"
        "ligue 1" in lower -> return "Franciaország: Ligue 1"
        "nb i" in lower || "otp bank liga" in lower || ("hungary" in lower && "liga" in lower) -> return "Magyarország: NB I"
        "champions league" in lower -> return "Nemzetközi: Bajnokok Ligája"
        "europa league" in lower && "conference" !in lower -> return "Nemzetközi: Európa-liga"
        "conference league" in lower -> return "Nemzetközi: Konferencia-liga"
        "eredivisie" in lower -> return "Hollandia: Eredivisie"
        "primeira liga" in lower || "liga portugal" in lower -> return "Portugália: Liga Portugal"
    }

    // Általános fordítás
    return translateLeagueNameAdvanced(trimmed)
}

internal fun translateLeagueNameAdvanced(original: String): String {

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

internal fun translateCompetitionWords(original: String): String {

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

    var tipSnapshots by remember {
        mutableStateOf<Map<String, TipSnapshot>>(emptyMap())
    }

    fun persistTips(updated: Set<String>) {
        preferences.edit().putStringSet("saved_tips", updated).apply()
        savedTips = updated.toList().sorted()
    }

    fun toggleTip(tip: String) {
        val updated = savedTips.toMutableSet()
        if (tip in updated) updated.remove(tip) else updated.add(tip)
        persistTips(updated)
    }

    fun removeTip(tip: String) {
        val updated = savedTips.toMutableSet()
        updated.remove(tip)
        persistTips(updated)
        tipSnapshots = tipSnapshots - tip
    }

    fun clearTips() {
        persistTips(emptySet())
        tipSnapshots = emptyMap()
    }

    fun removeSettledTips(matches: List<ScorexMatch>) {
        val toRemove = savedTips.filter { tip ->
            val st = evaluateTipStatus(tip, matches).first
            st == "Találat" || st == "Nem jött be" || st == "Lezárult"
        }
        if (toRemove.isNotEmpty()) {
            val updated = savedTips.toMutableSet()
            toRemove.forEach { updated.remove(it) }
            persistTips(updated)
            tipSnapshots = tipSnapshots.filterKeys { it !in toRemove }
        }
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
                ApiSettingsScreen(
                    preferences = preferences,
                    onBack = { showSettings = false }
                )
            }

            else -> {
                HomeScreen(
                    preferences = preferences,
                    savedTips = savedTips,
                    tipSnapshots = tipSnapshots,
                    onUpdateTipSnapshots = { tipSnapshots = it },
                    onRemoveTip = ::removeTip,
                    onClearTips = ::clearTips,
                    onRemoveSettledTips = ::removeSettledTips,
                    onSelectMatch = { match -> selectedMatch = match },
                    onOpenSettings = { showSettings = true },
                    onOpenApiData = { showApiData = true },
                    onOpenLeagueHub = { showLeagueHub = true },
                    onOpenProfile = { kind, id, title ->
                        selectedProfile = Triple(kind, id, title)
                    }
                )
            }
        }
    }
}
