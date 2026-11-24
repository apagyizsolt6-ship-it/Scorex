package hu.scorex.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ScorexMatch(
    val id: String,
    val league: String,
    val leagueId: String = "",
    val homeTeam: String,
    val homeTeamId: String = "",
    val awayTeam: String,
    val awayTeamId: String = "",
    val homeScore: String,
    val awayScore: String,
    val matchTime: String,
    val date: String = "",
    val rawStatus: String = "",
    val rawKickoff: String = ""
)

object StatPalApi {

    private const val V2_SOCCER_BASE = "https://statpal.io/api/v2/soccer"
    private const val DAILY_URL = "$V2_SOCCER_BASE/matches/daily"
    private const val LIVE_URL = "$V2_SOCCER_BASE/matches/live"
    private const val USAGE_URL = "https://statpal.io/api/user-request-count"

    private val targetZone: ZoneId = ZoneId.of("Europe/Budapest")
    private val outTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val outDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Volatile var lastRawResponse: String = ""
        private set
    @Volatile var lastLeagueCount: Int = 0
        private set
    @Volatile var lastApiMatchCount: Int = 0
        private set
    @Volatile var lastParsedMatchCount: Int = 0
        private set
    @Volatile var lastSkippedMatchCount: Int = 0
        private set
    @Volatile var lastDuplicateMatchCount: Int = 0
        private set
    @Volatile var lastMissingTeamCount: Int = 0
        private set
    @Volatile var lastInvalidMatchCount: Int = 0
        private set
    @Volatile var lastSkippedMatchDetails: String = ""
        private set
    @Volatile var lastMatchCount: Int = 0
        private set
    @Volatile var lastDailyRequestCount: String = "-"
        private set
    @Volatile var lastWasRateLimited: Boolean = false
        private set

    private val cachedLeagueIdsByName = mutableMapOf<String, String>()
    private val cachedTeamIdsByName = mutableMapOf<String, String>()

    fun resolveLeagueId(leagueName: String, explicitId: String = ""): String {
        if (explicitId.isNotBlank()) return explicitId
        return cachedLeagueIdsByName[leagueName.trim().lowercase()].orEmpty()
    }

    fun resolveTeamId(teamName: String, explicitId: String = ""): String {
        if (explicitId.isNotBlank()) return explicitId
        return cachedTeamIdsByName[normalizeTeamKey(teamName)].orEmpty()
    }

    private data class ParseStats(
        var rawCount: Int = 0,
        var duplicateCount: Int = 0,
        var missingTeamCount: Int = 0,
        var invalidCount: Int = 0,
        val skippedDetails: MutableList<String> = mutableListOf()
    )

    private fun buildUrlWithKey(
        baseUrl: String,
        apiKey: String,
        extraQuery: Map<String, String> = emptyMap()
    ): String {
        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val sb = StringBuilder(baseUrl)
        sb.append(if (baseUrl.contains("?")) "&" else "?")
        sb.append("access_key=").append(encodedKey)
        extraQuery.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                sb.append("&")
                    .append(URLEncoder.encode(k, "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(v, "UTF-8"))
            }
        }
        return sb.toString()
    }

    private fun getRaw(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 25000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                if (code == 429) lastWasRateLimited = true
                throw Exception("StatPal HTTP $code")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    fun fetchDailyRequestCount(apiKey: String): String {
        if (apiKey.isBlank()) return "-"
        return try {
            val body = getRaw(buildUrlWithKey(USAGE_URL, apiKey)).trim()
            val parsed = runCatching {
                val tok = JSONTokener(body).nextValue()
                when (tok) {
                    is JSONObject -> tok.opt("count")?.toString()
                        ?: tok.opt("request_count")?.toString()
                        ?: tok.opt("requests")?.toString()
                        ?: body
                    else -> body.removeSurrounding("\"")
                }
            }.getOrDefault(body)
            lastDailyRequestCount = parsed.ifBlank { "-" }
            lastDailyRequestCount
        } catch (_: Exception) {
            lastDailyRequestCount
        }
    }

    fun fetchAllLeagues(apiKey: String): List<LeagueSummaryItem> {
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/leagues", apiKey))
        val list = StatPalParsers.parseLeagues(raw)
        list.forEach {
            cachedLeagueIdsByName[it.name.trim().lowercase()] = it.id
        }
        return list
    }

    fun fetchLeagueSeasons(apiKey: String, leagueId: String): List<String> {
        val query = if (leagueId.isNotBlank()) mapOf("league_id" to leagueId) else emptyMap()
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/leagues/seasons", apiKey, query))
        return StatPalParsers.parseSeasons(raw)
    }

    fun fetchLeagueStatsBundle(apiKey: String, leagueId: String, leagueTitle: String): LeagueStatsSummary {
        val cleanId = URLEncoder.encode(leagueId.trim(), "UTF-8")
        val statsRaw = runCatching { getRaw(buildUrlWithKey("$V2_SOCCER_BASE/leagues/$cleanId/stats", apiKey)) }.getOrDefault("{}")
        val matchesStatsRaw = runCatching { getRaw(buildUrlWithKey("$V2_SOCCER_BASE/leagues/$cleanId/matches-stats", apiKey)) }.getOrDefault("{}")
        return StatPalParsers.parseLeagueStats(leagueId, leagueTitle, statsRaw, matchesStatsRaw)
    }

    fun fetchLeaguePrematchOdds(apiKey: String, leagueId: String): List<MatchOddsItem> {
        val cleanId = URLEncoder.encode(leagueId.trim(), "UTF-8")
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/leagues/$cleanId/odds-prematch", apiKey))
        return StatPalParsers.parseOdds(raw)
    }

    fun fetchMatchPrediction(apiKey: String, matchId: String): MatchPredictionInfo? {
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/predictions", apiKey, mapOf("match_id" to matchId)))
        return StatPalParsers.parsePrediction(raw)
    }

    fun fetchMatchH2H(apiKey: String, match: ScorexMatch): List<String> {
        val query = buildMap {
            put("match_id", match.id)
            if (match.homeTeamId.isNotBlank() && match.awayTeamId.isNotBlank()) {
                put("h2h", "${match.homeTeamId}-${match.awayTeamId}")
            }
        }
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/head-to-head", apiKey, query))
        return StatPalParsers.parseH2H(raw)
    }

    fun fetchMatchLineups(apiKey: String, matchId: String): MatchLineupsInfo? {
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/team-lineups", apiKey, mapOf("match_id" to matchId)))
        return StatPalParsers.parseLineups(raw)
    }

    fun fetchMatchInjuries(apiKey: String, match: ScorexMatch): List<InjuryItem> {
        val query = buildMap {
            put("match_id", match.id)
            if (match.leagueId.isNotBlank()) put("league_id", match.leagueId)
        }
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/injuries-suspensions", apiKey, query))
        return StatPalParsers.parseInjuries(raw)
    }

    fun fetchMatchWeather(apiKey: String, matchId: String): MatchWeatherInfo? {
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/weather-forecast", apiKey, mapOf("match_id" to matchId)))
        return StatPalParsers.parseWeather(raw)
    }

    fun fetchMatchLiveOdds(apiKey: String, match: ScorexMatch): List<MatchOddsItem> {
        val liveRaw = runCatching {
            getRaw(buildUrlWithKey("$V2_SOCCER_BASE/odds/live", apiKey, mapOf("match_id" to match.id)))
        }.getOrDefault("")
        val parsedLive = StatPalParsers.parseOdds(liveRaw)
        if (parsedLive.isNotEmpty()) return parsedLive

        val resolvedLeague = resolveLeagueId(match.league, match.leagueId)
        if (resolvedLeague.isNotBlank()) {
            return runCatching { fetchLeaguePrematchOdds(apiKey, resolvedLeague) }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    fun fetchEntityProfile(apiKey: String, kind: String, id: String): EntityProfileData {
        val cleanId = URLEncoder.encode(id.trim(), "UTF-8")
        val path = when (kind) {
            "team" -> "teams/$cleanId"
            "player" -> "players/$cleanId"
            "coach" -> "coaches/$cleanId"
            else -> "teams/$cleanId"
        }
        val raw = getRaw(buildUrlWithKey("$V2_SOCCER_BASE/$path", apiKey))
        return StatPalParsers.parseEntityProfile(kind, id, raw)
    }

    private fun convertUtcKickoffToLocal(
        rawDate: String,
        rawTime: String
    ): Pair<String, String> {
        val cleanedTime = rawTime.trim()
        val matchResult = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").matchEntire(cleanedTime)
            ?: return cleanedTime to rawDate.trim()

        val hour = matchResult.groupValues[1].toIntOrNull() ?: return cleanedTime to rawDate.trim()
        val minute = matchResult.groupValues[2].toIntOrNull() ?: return cleanedTime to rawDate.trim()
        val second = matchResult.groupValues[3].toIntOrNull() ?: 0

        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return cleanedTime to rawDate.trim()
        }

        val parsedDate = runCatching {
            if (rawDate.isNotBlank()) LocalDate.parse(rawDate.trim())
            else LocalDate.now(ZoneOffset.UTC)
        }.getOrElse {
            LocalDate.now(ZoneOffset.UTC)
        }

        val utcDateTime = LocalDateTime.of(parsedDate, LocalTime.of(hour, minute, second))
        val localDateTime = utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(targetZone)

        return localDateTime.format(outTimeFormatter) to localDateTime.toLocalDate().format(outDateFormatter)
    }

    fun fetchLiveMatches(
        apiKey: String,
        offset: Int = 0,
        useDedicatedLiveEndpoint: Boolean = false
    ): List<ScorexMatch> {

        require(apiKey.isNotBlank()) { "Hiányzik a StatPal API-kulcs!" }
        lastWasRateLimited = false

        val targetUrl = if (useDedicatedLiveEndpoint && offset == 0) {
            buildUrlWithKey(LIVE_URL, apiKey)
        } else {
            buildUrlWithKey(DAILY_URL, apiKey, mapOf("offset" to offset.toString()))
        }

        val connection = URL(targetUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            val response = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            lastRawResponse = response

            if (code !in 200..299) {
                if (code == 429) lastWasRateLimited = true
                throw Exception(
                    when (code) {
                        401, 403 -> "Hibás API-kulcs vagy hozzáférés."
                        400 -> "Hibás StatPal lekérdezés."
                        404 -> "A StatPal végpont nem található."
                        429 -> "API lekérési korlát elérve (429)."
                        else -> "StatPal HTTP hiba: $code"
                    }
                )
            }

            val root = JSONTokener(response).nextValue()
            if (root is JSONObject && !root.optBoolean("success", true)) {
                throw Exception(root.optString("message", "A StatPal hibát jelzett."))
            }

            val matches = mutableListOf<ScorexMatch>()
            val leagueIds = mutableSetOf<String>()
            val seenMatchIds = mutableSetOf<String>()
            val stats = ParseStats()

            collectAllLeagues(
                value = root,
                output = matches,
                leagueIds = leagueIds,
                seenMatchIds = seenMatchIds,
                stats = stats
            )

            lastLeagueCount = leagueIds.size
            lastApiMatchCount = stats.rawCount
            lastParsedMatchCount = matches.size
            lastMatchCount = matches.size

            lastDuplicateMatchCount = stats.duplicateCount
            lastMissingTeamCount = stats.missingTeamCount
            lastInvalidMatchCount = stats.invalidCount

            lastSkippedMatchCount = (stats.rawCount - matches.size).coerceAtLeast(0)
            lastSkippedMatchDetails = if (stats.skippedDetails.isEmpty()) {
                "Nem találtunk kihagyott mérkőzést."
            } else {
                stats.skippedDetails.joinToString(separator = "\n\n")
            }

            return matches
        } finally {
            connection.disconnect()
        }
    }

    private fun collectAllLeagues(
        value: Any?,
        output: MutableList<ScorexMatch>,
        leagueIds: MutableSet<String>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {
        when (value) {
            is JSONObject -> {
                val leagueArray = value.optJSONArray("league")
                if (leagueArray != null) {
                    parseLeagueArray(leagueArray, output, leagueIds, seenMatchIds, stats)
                    return
                }
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val child = value.opt(iterator.next())
                    if (child is JSONObject || child is JSONArray) {
                        collectAllLeagues(child, output, leagueIds, seenMatchIds, stats)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectAllLeagues(value.opt(i), output, leagueIds, seenMatchIds, stats)
                }
            }
        }
    }

    private fun parseLeagueArray(
        leagues: JSONArray,
        output: MutableList<ScorexMatch>,
        leagueIds: MutableSet<String>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {
        for (i in 0 until leagues.length()) {
            val leagueObject = leagues.optJSONObject(i) ?: continue
            val leagueName = leagueObject.optString("name").trim().ifBlank { "Ismeretlen bajnokság" }
            val rawLeagueId = leagueObject.optString("id").trim()
            val leagueId = rawLeagueId.ifBlank { leagueName }
            leagueIds.add(leagueId)

            if (rawLeagueId.isNotBlank()) {
                cachedLeagueIdsByName[leagueName.lowercase()] = rawLeagueId
            }

            when (val matchValue = leagueObject.opt("match")) {
                is JSONObject -> parseSingleMatch(matchValue, leagueName, rawLeagueId, 0, output, seenMatchIds, stats)
                is JSONArray -> parseMatches(matchValue, leagueName, rawLeagueId, output, seenMatchIds, stats)
            }
        }
    }

    private fun parseMatches(
        matchArray: JSONArray,
        leagueName: String,
        leagueId: String,
        output: MutableList<ScorexMatch>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {
        for (i in 0 until matchArray.length()) {
            val matchObject = matchArray.optJSONObject(i)
            if (matchObject == null) {
                stats.rawCount++
                stats.invalidCount++
                stats.skippedDetails.add("Bajnokság: $leagueName\nMérkőzés sorszáma: ${i + 1}\nOk: Nem JSON-objektum.")
                continue
            }
            parseSingleMatch(matchObject, leagueName, leagueId, i, output, seenMatchIds, stats)
        }
    }

    private fun parseSingleMatch(
        matchObject: JSONObject,
        leagueName: String,
        leagueId: String,
        index: Int,
        output: MutableList<ScorexMatch>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {
        stats.rawCount++

        val homeObject = matchObject.optJSONObject("home")
        val awayObject = matchObject.optJSONObject("away")

        val homeTeam = homeObject?.optString("name").orEmpty().trim()
        val awayTeam = awayObject?.optString("name").orEmpty().trim()
        val homeTeamId = homeObject?.optString("id").orEmpty().trim()
        val awayTeamId = awayObject?.optString("id").orEmpty().trim()

        if (homeTeam.isNotBlank() && homeTeamId.isNotBlank()) {
            cachedTeamIdsByName[normalizeTeamKey(homeTeam)] = homeTeamId
        }
        if (awayTeam.isNotBlank() && awayTeamId.isNotBlank()) {
            cachedTeamIdsByName[normalizeTeamKey(awayTeam)] = awayTeamId
        }

        val rawDate = matchObject.optString("date").trim()
        val kickoff = matchObject.optString("time").trim()

        val matchId = matchObject.optString("main_id").trim()
            .ifBlank { matchObject.optString("fallback_id_1").trim() }
            .ifBlank { matchObject.optString("fallback_id_2").trim() }
            .ifBlank { matchObject.optString("fallback_id_3").trim() }
            .ifBlank { "$leagueName-$homeTeam-$awayTeam-$rawDate-$kickoff-$index" }

        val description = """
            Bajnokság: $leagueName
            Hazai: ${homeTeam.ifBlank { "NINCS ADAT" }}
            Vendég: ${awayTeam.ifBlank { "NINCS ADAT" }}
            Dátum: ${rawDate.ifBlank { "NINCS ADAT" }}
            Kezdés: ${kickoff.ifBlank { "NINCS ADAT" }}
            Azonosító: $matchId
        """.trimIndent()

        if (homeTeam.isBlank() || awayTeam.isBlank()) {
            stats.missingTeamCount++
            stats.skippedDetails.add("$description\nOk: Hiányzó csapatnév.")
            return
        }

        if (!seenMatchIds.add(matchId)) {
            stats.duplicateCount++
            stats.skippedDetails.add("$description\nOk: Duplikált mérkőzésazonosító.")
            return
        }

        val homeScore = homeObject?.optString("goals").orEmpty().trim().ifBlank { "-" }.let { if (it == "?") "-" else it }
        val awayScore = awayObject?.optString("goals").orEmpty().trim().ifBlank { "-" }.let { if (it == "?") "-" else it }

        val status = matchObject.optString("status").trim()
        val injuryMinute = matchObject.optString("inj_minute").trim()
        val (localKickoff, localDate) = convertUtcKickoffToLocal(rawDate, kickoff)

        val matchTime = when {
            status.equals("FT", true) -> "FT"
            status.equals("AET", true) -> "AET"
            status.equals("PEN", true) -> "PEN"
            status.equals("HT", true) -> "HT"
            status.equals("PST", true) -> "Elhalasztva"
            status.equals("CANC", true) || status.equals("CANCELLED", true) -> "Törölve"
            status.equals("ABD", true) || status.equals("ABANDONED", true) -> "Félbeszakadt"
            injuryMinute.isNotBlank() && status.isNotBlank() && status.all { it.isDigit() } -> "$status+$injuryMinute"
            injuryMinute.isNotBlank() -> injuryMinute
            status.equals("NS", true) || status.equals("TBD", true) -> localKickoff.ifBlank { "Hamarosan" }
            status.matches(Regex("\\d{1,2}:\\d{2}")) -> convertUtcKickoffToLocal(rawDate, status).first
            status.isNotBlank() && status.all { it.isDigit() } -> "$status'"
            status.isNotBlank() -> status
            localKickoff.isNotBlank() -> localKickoff
            else -> "ÉLŐ"
        }

        output.add(
            ScorexMatch(
                id = matchId,
                league = leagueName,
                leagueId = leagueId,
                homeTeam = homeTeam,
                homeTeamId = homeTeamId,
                awayTeam = awayTeam,
                awayTeamId = awayTeamId,
                homeScore = homeScore,
                awayScore = awayScore,
                matchTime = matchTime,
                date = localDate,
                rawStatus = status,
                rawKickoff = kickoff
            )
        )
    }
}
