package hu.scorex.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

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

internal val BackgroundColor = Color(0xFF070D19)
internal val GlassSurface = Color(0xFF13233A).copy(alpha = 0.74f)
internal val GlassTop = Color(0xFF1F385C).copy(alpha = 0.58f)
internal val GlassWell = Color(0xFF0B1424).copy(alpha = 0.80f)
internal val AccentColor = Color(0xFF35E0A1)
internal val SecondaryColor = Color(0xFF9FB0C7)
internal val ErrorColor = Color(0xFFFF7777)
internal val WarningColor = Color(0xFFFFB74D)
internal val CyanAccent = Color(0xFF38BDF8)
internal val PurpleAccent = Color(0xFFA78BFA)
internal val GoldAccent = Color(0xFFFFC857)

internal enum class TipFilter(val label: String) {
    ALL("Mind"),
    WINNING("Jó állás"),
    SETTLED("Lezárt")
}

internal data class TipSnapshot(
    val statusLabel: String,
    val scorePreview: String?
)

@Composable
internal fun GlassPanel(
    borderColor: Color,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
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
                    color = Color.White.copy(alpha = 0.14f),
                    shape = shape
                ),
            content = content
        )
    }
}

internal fun normalizeTeamKey(name: String): String {
    return name
        .lowercase(Locale("hu", "HU"))
        .replace(Regex("[^a-z0-9áéíóöőúüű ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun loadTipSnapshots(preferences: SharedPreferences): MutableMap<String, TipSnapshot> {
    val raw = preferences.getString("scorex_tip_snapshots", "{}").orEmpty()
    val result = mutableMapOf<String, TipSnapshot>()
    runCatching {
        val obj = JSONObject(raw)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val item = obj.optJSONObject(k)
            if (item != null) {
                val label = item.optString("status", "")
                val preview = item.optString("preview", "").ifBlank { null }
                if (label.isNotBlank()) {
                    result[k] = TipSnapshot(statusLabel = label, scorePreview = preview)
                }
            }
        }
    }
    return result
}

internal fun saveTipSnapshots(
    preferences: SharedPreferences,
    snapshots: Map<String, TipSnapshot>
) {
    val root = JSONObject()
    snapshots.forEach { (tip, snap) ->
        root.put(
            tip,
            JSONObject().apply {
                put("status", snap.statusLabel)
                put("preview", snap.scorePreview.orEmpty())
            }
        )
    }
    preferences.edit().putString("scorex_tip_snapshots", root.toString()).apply()
}

@Composable
fun ScorexApp(preferences: SharedPreferences) {

    var showSettings by remember { mutableStateOf(false) }
    var showApiData by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<ScorexMatch?>(null) }

    val savedTips = remember {
        mutableStateListOf<String>().apply {
            addAll(
                preferences.getStringSet("scorex_tips", emptySet())
                    .orEmpty()
                    .sorted()
            )
        }
    }

    val tipSnapshots = remember {
        mutableStateMapOf<String, TipSnapshot>().apply {
            putAll(loadTipSnapshots(preferences))
        }
    }

    fun persistTips() {
        preferences.edit()
            .putStringSet("scorex_tips", savedTips.toSet())
            .apply()
        val activeKeys = savedTips.toSet()
        tipSnapshots.keys.retainAll(activeKeys)
        saveTipSnapshots(preferences, tipSnapshots)
    }

    fun toggleTip(tipLabel: String) {
        if (tipLabel in savedTips) {
            savedTips.remove(tipLabel)
            tipSnapshots.remove(tipLabel)
        } else {
            savedTips.add(tipLabel)
        }
        persistTips()
    }

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
                                Color(0xFF0F2038),
                                BackgroundColor,
                                Color(0xFF091221)
                            )
                        )
                    )
            ) {
                when {
                    selectedMatch != null -> {
                        MatchDetailScreen(
                            match = selectedMatch!!,
                            preferences = preferences,
                            savedTips = savedTips,
                            onToggleTip = ::toggleTip,
                            onBack = { selectedMatch = null }
                        )
                    }

                    showApiData -> {
                        ApiDataScreen(onBack = { showApiData = false })
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
                            onUpdateTipSnapshots = { updated ->
                                tipSnapshots.clear()
                                tipSnapshots.putAll(updated)
                                saveTipSnapshots(preferences, tipSnapshots)
                            },
                            onRemoveTip = { tip ->
                                savedTips.remove(tip)
                                tipSnapshots.remove(tip)
                                persistTips()
                            },
                            onClearTips = {
                                savedTips.clear()
                                tipSnapshots.clear()
                                persistTips()
                            },
                            onRemoveSettledTips = { activeMatches ->
                                val settledStatuses = setOf("Találat", "Nem jött be", "Lezárult")
                                savedTips.removeAll { tip ->
                                    val liveEval = evaluateTipStatus(tip, activeMatches).first
                                    val cachedEval = tipSnapshots[tip]?.statusLabel
                                    (liveEval in settledStatuses) || (cachedEval in settledStatuses)
                                }
                                persistTips()
                            },
                            onSelectMatch = { match -> selectedMatch = match },
                            onOpenSettings = { showSettings = true },
                            onOpenApiData = { showApiData = true }
                        )
                    }
                }
            }
        }
    }
}

internal fun formatOffsetLabel(offset: Int): String = when (offset) {
    0 -> "Ma (0)"
    -1 -> "Tegnap (-1)"
    1 -> "Holnap (+1)"
    else -> if (offset > 0) "+$offset nap" else "$offset nap"
}

internal fun findMatchingMatch(
    tip: String,
    matches: List<ScorexMatch>
): ScorexMatch? {
    val colonIdx = tip.lastIndexOf(": ")
    if (colonIdx <= 0) return null
    val teamsPart = tip.substring(0, colonIdx)
    val dashIdx = teamsPart.indexOf(" – ")
    if (dashIdx <= 0) return null

    val home = normalizeTeamKey(teamsPart.substring(0, dashIdx))
    val away = normalizeTeamKey(teamsPart.substring(dashIdx + 3))
    if (home.isBlank() || away.isBlank()) return null

    return matches.firstOrNull {
        val mHome = normalizeTeamKey(it.homeTeam)
        val mAway = normalizeTeamKey(it.awayTeam)
        (mHome == home && mAway == away) ||
            (mHome.contains(home) && mAway.contains(away)) ||
            (home.contains(mHome) && away.contains(mAway))
    }
}

internal fun statusColorForLabel(label: String): Color = when (label) {
    "Találat", "Nyerésre áll" -> AccentColor
    "Nem jött be" -> ErrorColor
    "Jelenleg nem jó" -> WarningColor
    "Lezárult", "Várakozik", "Mentve" -> SecondaryColor
    else -> CyanAccent
}

internal fun evaluateTipStatus(
    tip: String,
    matches: List<ScorexMatch>,
    fallbackSnapshot: TipSnapshot? = null
): Pair<String, Color> {
    val colonIdx = tip.lastIndexOf(": ")
    if (colonIdx <= 0) return "Mentve" to SecondaryColor

    val pickPart = tip.substring(colonIdx + 2).trim()
    val match = findMatchingMatch(tip, matches)
        ?: fallbackSnapshot?.let { return it.statusLabel to statusColorForLabel(it.statusLabel) }
        ?: return "Várakozik" to SecondaryColor

    val hScore = match.homeScore.toIntOrNull()
    val aScore = match.awayScore.toIntOrNull()
    val finished = isMatchFinished(match)

    if (hScore == null || aScore == null) {
        val label = if (finished) "Lezárult" else match.matchTime
        return label to statusColorForLabel(label)
    }

    val outcome = when {
        hScore > aScore -> "1 ("
        aScore > hScore -> "2 ("
        else -> "X ("
    }

    val isWinning = pickPart.startsWith(outcome)
    val label = when {
        finished && isWinning -> "Találat"
        finished && !isWinning -> "Nem jött be"
        isWinning -> "Nyerésre áll"
        else -> "Jelenleg nem jó"
    }
    return label to statusColorForLabel(label)
}
