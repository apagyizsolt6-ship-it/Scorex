package hu.scorex.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TipSnapshot(
    val statusLabel: String = "",
    val scorePreview: String = ""
)

enum class TipFilter(val label: String) {
    ALL("Mind"),
    WINNING("Nyerő"),
    PENDING("Várakozó"),
    SETTLED("Lezárt")
}

internal fun formatOffsetLabel(offset: Int): String {
    val date = LocalDate.now(ZoneId.of("Europe/Budapest")).plusDays(offset.toLong())
    val formattedDate = date.format(DateTimeFormatter.ofPattern("MM.dd."))
    return when (offset) {
        0 -> "Ma ($formattedDate)"
        -1 -> "Tegnap ($formattedDate)"
        1 -> "Holnap ($formattedDate)"
        else -> if (offset > 0) "+$offset nap ($formattedDate)" else "$offset nap ($formattedDate)"
    }
}

internal fun findMatchingMatch(tip: String, matches: List<ScorexMatch>): ScorexMatch? {
    val pairPart = tip.substringBefore(": ", "").trim()
    if (pairPart.isBlank()) return null
    val parts = pairPart.split(Regex("\\s+[–-]\\s+"))
    if (parts.size < 2) return null

    val homeKey = normalizeTeamKey(parts[0])
    val awayKey = normalizeTeamKey(parts[1])
    if (homeKey.isBlank() || awayKey.isBlank()) return null

    return matches.firstOrNull { m ->
        normalizeTeamKey(m.homeTeam) == homeKey && normalizeTeamKey(m.awayTeam) == awayKey
    } ?: matches.firstOrNull { m ->
        val mh = normalizeTeamKey(m.homeTeam)
        val ma = normalizeTeamKey(m.awayTeam)
        (mh.contains(homeKey) || homeKey.contains(mh)) &&
            (ma.contains(awayKey) || awayKey.contains(ma))
    }
}

internal fun evaluateTipStatus(
    tip: String,
    matches: List<ScorexMatch>,
    snapshot: TipSnapshot? = null
): Pair<String, Color> {
    val matched = findMatchingMatch(tip, matches)
    if (matched == null) {
        val fallback = snapshot?.statusLabel.orEmpty()
        if (fallback.isNotBlank()) {
            return fallback to when (fallback) {
                "Nyerésre áll", "Találat" -> AccentColor
                "Nem jött be" -> ErrorColor
                "Jelenleg nem jó" -> WarningColor
                else -> SecondaryColor
            }
        }
        return "Várakozó" to SecondaryColor
    }

    val pickSide = when {
        tip.endsWith(")") && ": 1 (" in tip -> "1"
        tip.endsWith(")") && ": 2 (" in tip -> "2"
        ": X " in tip -> "X"
        else -> {
            val afterColon = tip.substringAfter(": ", "").trim()
            when {
                afterColon.startsWith("1") -> "1"
                afterColon.startsWith("2") -> "2"
                afterColon.startsWith("X", ignoreCase = true) -> "X"
                else -> ""
            }
        }
    }

    val h = matched.homeScore.toIntOrNull()
    val a = matched.awayScore.toIntOrNull()
    val finished = isMatchFinished(matched)
    val live = isMatchLive(matched)

    if (h == null || a == null || pickSide.isBlank()) {
        return if (live) "Élő" to CyanAccent else "Várakozó" to SecondaryColor
    }

    val pickWinsNow = when (pickSide) {
        "1" -> h > a
        "2" -> a > h
        "X" -> h == a
        else -> false
    }

    return when {
        finished && pickWinsNow -> "Találat" to AccentColor
        finished && !pickWinsNow -> "Nem jött be" to ErrorColor
        live && pickWinsNow -> "Nyerésre áll" to AccentColor
        live && !pickWinsNow -> "Jelenleg nem jó" to WarningColor
        else -> "Várakozó" to SecondaryColor
    }
}

internal object ScorexNotifier {
    fun detectAndNotifyChanges(
        context: Context,
        preferences: SharedPreferences,
        previousMatches: List<ScorexMatch>,
        currentMatches: List<ScorexMatch>,
        savedTips: List<String>
    ) {
        val _ = context
        val _ = preferences
        val _ = previousMatches
        val _ = currentMatches
        val _ = savedTips
    }
}
