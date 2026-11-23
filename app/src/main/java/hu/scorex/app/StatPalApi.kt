package hu.scorex.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ScorexMatch(
    val id: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val matchTime: String,
    val date: String = "",
    val rawStatus: String = ""
)

object StatPalApi {

    private const val BASE_URL =
        "https://statpal.io/api/v2/soccer/matches/daily"

    @Volatile
    var lastRawResponse: String = ""
        private set

    @Volatile
    var lastLeagueCount: Int = 0
        private set

    @Volatile
    var lastApiMatchCount: Int = 0
        private set

    @Volatile
    var lastParsedMatchCount: Int = 0
        private set

    @Volatile
    var lastSkippedMatchCount: Int = 0
        private set

    @Volatile
    var lastDuplicateMatchCount: Int = 0
        private set

    @Volatile
    var lastMissingTeamCount: Int = 0
        private set

    @Volatile
    var lastInvalidMatchCount: Int = 0
        private set

    @Volatile
    var lastSkippedMatchDetails: String = ""
        private set

    @Volatile
    var lastMatchCount: Int = 0
        private set

    private data class ParseStats(
        var rawCount: Int = 0,
        var duplicateCount: Int = 0,
        var missingTeamCount: Int = 0,
        var invalidCount: Int = 0,
        val skippedDetails: MutableList<String> = mutableListOf()
    )

    fun fetchLiveMatches(apiKey: String): List<ScorexMatch> {

        require(apiKey.isNotBlank()) {
            "Hiányzik a StatPal API-kulcs!"
        }

        val encodedKey = URLEncoder.encode(
            apiKey.trim(),
            "UTF-8"
        )

        val url = URL(
            "$BASE_URL?offset=0&access_key=$encodedKey"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val code = connection.responseCode

            val response = if (code in 200..299) {
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }
            } else {
                connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
            }

            lastRawResponse = response

            if (code !in 200..299) {
                throw Exception(
                    when (code) {
                        401, 403 ->
                            "Hibás API-kulcs vagy hozzáférés."

                        400 ->
                            "Hibás StatPal lekérdezés."

                        404 ->
                            "A StatPal napi végpont nem található."

                        429 ->
                            "API lekérési korlát elérve."

                        else ->
                            "StatPal HTTP hiba: $code"
                    }
                )
            }

            val root = JSONTokener(response).nextValue()

            if (root is JSONObject) {
                if (!root.optBoolean("success", true)) {
                    throw Exception(
                        root.optString(
                            "message",
                            "A StatPal hibát jelzett."
                        )
                    )
                }
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

            lastDuplicateMatchCount =
                stats.duplicateCount

            lastMissingTeamCount =
                stats.missingTeamCount

            lastInvalidMatchCount =
                stats.invalidCount

            lastSkippedMatchCount =
                (stats.rawCount - matches.size)
                    .coerceAtLeast(0)

            lastSkippedMatchDetails =
                if (stats.skippedDetails.isEmpty()) {
                    "Nem találtunk kihagyott mérkőzést."
                } else {
                    stats.skippedDetails.joinToString(
                        separator = "\n\n"
                    )
                }

            return matches

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Végigjárja a teljes JSON-választ.
     */
    private fun collectAllLeagues(
        value: Any?,
        output: MutableList<ScorexMatch>,
        leagueIds: MutableSet<String>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {

        when (value) {

            is JSONObject -> {

                val leagueArray =
                    value.optJSONArray("league")

                if (leagueArray != null) {

                    parseLeagueArray(
                        leagues = leagueArray,
                        output = output,
                        leagueIds = leagueIds,
                        seenMatchIds = seenMatchIds,
                        stats = stats
                    )

                    return
                }

                val iterator = value.keys()

                while (iterator.hasNext()) {

                    val child = value.opt(iterator.next())

                    if (
                        child is JSONObject ||
                        child is JSONArray
                    ) {
                        collectAllLeagues(
                            value = child,
                            output = output,
                            leagueIds = leagueIds,
                            seenMatchIds = seenMatchIds,
                            stats = stats
                        )
                    }
                }
            }

            is JSONArray -> {

                for (i in 0 until value.length()) {

                    collectAllLeagues(
                        value = value.opt(i),
                        output = output,
                        leagueIds = leagueIds,
                        seenMatchIds = seenMatchIds,
                        stats = stats
                    )
                }
            }
        }
    }

    /**
     * Bajnokságok feldolgozása.
     *
     * A match mező lehet:
     * - JSONObject: egyetlen mérkőzés
     * - JSONArray: több mérkőzés
     */
    private fun parseLeagueArray(
        leagues: JSONArray,
        output: MutableList<ScorexMatch>,
        leagueIds: MutableSet<String>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {

        for (i in 0 until leagues.length()) {

            val leagueObject =
                leagues.optJSONObject(i) ?: continue

            val leagueName = leagueObject
                .optString("name")
                .trim()
                .ifBlank {
                    "Ismeretlen bajnokság"
                }

            val leagueId = leagueObject
                .optString("id")
                .ifBlank {
                    leagueName
                }

            leagueIds.add(leagueId)

            val matchValue =
                leagueObject.opt("match")

            when (matchValue) {

                is JSONObject -> {

                    parseSingleMatch(
                        matchObject = matchValue,
                        leagueName = leagueName,
                        index = 0,
                        output = output,
                        seenMatchIds = seenMatchIds,
                        stats = stats
                    )
                }

                is JSONArray -> {

                    parseMatches(
                        matchArray = matchValue,
                        leagueName = leagueName,
                        output = output,
                        seenMatchIds = seenMatchIds,
                        stats = stats
                    )
                }
            }
        }
    }

    /**
     * Tömbként érkező mérkőzések feldolgozása.
     */
    private fun parseMatches(
        matchArray: JSONArray,
        leagueName: String,
        output: MutableList<ScorexMatch>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {

        for (i in 0 until matchArray.length()) {

            val matchObject =
                matchArray.optJSONObject(i)

            if (matchObject == null) {

                stats.rawCount++
                stats.invalidCount++

                stats.skippedDetails.add(
                    """
                    Bajnokság: $leagueName
                    Mérkőzés sorszáma: ${i + 1}
                    Ok: A mérkőzés adata nem JSON-objektum.
                    """.trimIndent()
                )

                continue
            }

            parseSingleMatch(
                matchObject = matchObject,
                leagueName = leagueName,
                index = i,
                output = output,
                seenMatchIds = seenMatchIds,
                stats = stats
            )
        }
    }

    /**
     * Egyetlen mérkőzés feldolgozása.
     */
    private fun parseSingleMatch(
        matchObject: JSONObject,
        leagueName: String,
        index: Int,
        output: MutableList<ScorexMatch>,
        seenMatchIds: MutableSet<String>,
        stats: ParseStats
    ) {

        stats.rawCount++

        val homeObject =
            matchObject.optJSONObject("home")

        val awayObject =
            matchObject.optJSONObject("away")

        val homeTeam = homeObject
            ?.optString("name")
            .orEmpty()
            .trim()

        val awayTeam = awayObject
            ?.optString("name")
            .orEmpty()
            .trim()

        val date = matchObject
            .optString("date")
            .trim()

        val kickoff = matchObject
            .optString("time")
            .trim()

        val mainId = matchObject
            .optString("main_id")
            .trim()

        val fallbackId1 = matchObject
            .optString("fallback_id_1")
            .trim()

        val fallbackId2 = matchObject
            .optString("fallback_id_2")
            .trim()

        val fallbackId3 = matchObject
            .optString("fallback_id_3")
            .trim()

        val matchId = mainId
            .ifBlank { fallbackId1 }
            .ifBlank { fallbackId2 }
            .ifBlank { fallbackId3 }
            .ifBlank {
                "$leagueName-$homeTeam-$awayTeam-$date-$kickoff"
            }

        val description =
            """
            Bajnokság: $leagueName
            Hazai: ${homeTeam.ifBlank { "NINCS ADAT" }}
            Vendég: ${awayTeam.ifBlank { "NINCS ADAT" }}
            Dátum: ${date.ifBlank { "NINCS ADAT" }}
            Kezdés: ${kickoff.ifBlank { "NINCS ADAT" }}
            Azonosító: $matchId
            """.trimIndent()

        if (
            homeTeam.isBlank() ||
            awayTeam.isBlank()
        ) {

            stats.missingTeamCount++

            val reason = when {
                homeTeam.isBlank() && awayTeam.isBlank() ->
                    "Mindkét csapat neve hiányzik."

                homeTeam.isBlank() ->
                    "A hazai csapat neve hiányzik."

                else ->
                    "A vendégcsapat neve hiányzik."
            }

            stats.skippedDetails.add(
                "$description\nOk: $reason"
            )

            return
        }

        if (!seenMatchIds.add(matchId)) {

            stats.duplicateCount++

            stats.skippedDetails.add(
                "$description\nOk: Duplikált mérkőzésazonosító."
            )

            return
        }

        val homeScore = homeObject
            ?.optString("goals")
            .orEmpty()
            .trim()
            .ifBlank { "-" }
            .let {
                if (it == "?") "-" else it
            }

        val awayScore = awayObject
            ?.optString("goals")
            .orEmpty()
            .trim()
            .ifBlank { "-" }
            .let {
                if (it == "?") "-" else it
            }

        val status = matchObject
            .optString("status")
            .trim()

        val injuryMinute = matchObject
            .optString("inj_minute")
            .trim()

        val matchTime = when {

            status.equals("FT", true) ->
                "FT"

            status.equals("HT", true) ->
                "HT"

            injuryMinute.isNotBlank() &&
                status.isNotBlank() &&
                status.all { it.isDigit() } ->
                "$status+$injuryMinute"

            injuryMinute.isNotBlank() ->
                injuryMinute

            status.equals("NS", true) ||
                status.equals("TBD", true) ->
                kickoff.ifBlank { "Hamarosan" }

            status.matches(Regex("\\d{1,2}:\\d{2}")) ->
                status

            status.isNotBlank() &&
                status.all { it.isDigit() } ->
                "$status'"

            status.isNotBlank() ->
                status

            kickoff.isNotBlank() ->
                kickoff

            else ->
                "ÉLŐ"
        }

        output.add(
            ScorexMatch(
                id = matchId,
                league = leagueName,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeScore = homeScore,
                awayScore = awayScore,
                matchTime = matchTime,
                date = date,
                rawStatus = status
            )
        )
    }
}
