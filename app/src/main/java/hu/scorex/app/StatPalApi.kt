
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
    val matchTime: String
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

    // A feldolgozás során megtalált összes API-meccs.
    @Volatile
    var lastApiMatchCount: Int = 0
        private set

    // A Scorex által sikeresen feldolgozott meccsek.
    @Volatile
    var lastParsedMatchCount: Int = 0
        private set

    // A hibás vagy hiányos adatok miatt kihagyott meccsek.
    @Volatile
    var lastSkippedMatchCount: Int = 0
        private set

    // Kompatibilitás a korábbi kóddal.
    @Volatile
    var lastMatchCount: Int = 0
        private set

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

            val rawMatchCounter = intArrayOf(0)

            collectAllLeagues(
                value = root,
                output = matches,
                leagueIds = leagueIds,
                seenMatchIds = seenMatchIds,
                rawMatchCounter = rawMatchCounter
            )

            lastLeagueCount = leagueIds.size
            lastApiMatchCount = rawMatchCounter[0]
            lastParsedMatchCount = matches.size
            lastMatchCount = matches.size

            lastSkippedMatchCount =
                (lastApiMatchCount - lastParsedMatchCount)
                    .coerceAtLeast(0)

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
        rawMatchCounter: IntArray
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
                        rawMatchCounter = rawMatchCounter
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
                            rawMatchCounter = rawMatchCounter
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
                        rawMatchCounter = rawMatchCounter
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
        rawMatchCounter: IntArray
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
                        rawMatchCounter = rawMatchCounter
                    )
                }

                is JSONArray -> {

                    parseMatches(
                        matchArray = matchValue,
                        leagueName = leagueName,
                        output = output,
                        seenMatchIds = seenMatchIds,
                        rawMatchCounter = rawMatchCounter
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
        rawMatchCounter: IntArray
    ) {

        for (i in 0 until matchArray.length()) {

            val matchObject =
                matchArray.optJSONObject(i) ?: continue

            parseSingleMatch(
                matchObject = matchObject,
                leagueName = leagueName,
                index = i,
                output = output,
                seenMatchIds = seenMatchIds,
                rawMatchCounter = rawMatchCounter
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
        rawMatchCounter: IntArray
    ) {

        rawMatchCounter[0]++

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

        // Hiányos csapatadat esetén nem jelenítjük meg.
        if (
            homeTeam.isBlank() ||
            awayTeam.isBlank()
        ) {
            return
        }

        val date = matchObject
            .optString("date")
            .trim()

        val kickoff = matchObject
            .optString("time")
            .trim()

        val matchId = matchObject
            .optString("main_id")
            .ifBlank {
                matchObject.optString("fallback_id_1")
            }
            .ifBlank {
                matchObject.optString("fallback_id_2")
            }
            .ifBlank {
                matchObject.optString("fallback_id_3")
            }
            .ifBlank {
                "$leagueName-$homeTeam-$awayTeam-$date-$kickoff"
            }

        // Duplikált mérkőzés kiszűrése.
        if (!seenMatchIds.add(matchId)) {
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

            // Kezdés előtti meccs.
            status.equals("NS", true) ||
                status.equals("TBD", true) ->
                kickoff.ifBlank { "Hamarosan" }

            // A StatPal a kezdési időt is adhatja status mezőben.
            status.matches(Regex("\\d{1,2}:\\d{2}")) ->
                status

            // Élő mérkőzés perce.
            status.isNotBlank() &&
                status.all { it.isDigit() } ->
                "$status'"

            // Egyéb státusz.
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
                matchTime = matchTime
            )
        )
    }
}
