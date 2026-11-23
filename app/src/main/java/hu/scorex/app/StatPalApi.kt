
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

    @Volatile
    var lastMatchCount: Int = 0
        private set

    /**
     * A mai nap összes mérkőzésének lekérése.
     *
     * offset=0:
     * mai mérkőzések
     *
     * Tartalmazza:
     * - kezdés előtti mérkőzések
     * - élő mérkőzések
     * - befejezett mérkőzések
     */
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

            collectAllLeagues(
                value = root,
                output = matches,
                leagueIds = leagueIds
            )

            lastLeagueCount = leagueIds.size
            lastMatchCount = matches.size

            return matches

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Végigjárja a teljes JSON-választ.
     *
     * Megkeresi a napi objektumot,
     * azon belül a league tömböt,
     * majd minden bajnokságot feldolgoz.
     */
    private fun collectAllLeagues(
        value: Any?,
        output: MutableList<ScorexMatch>,
        leagueIds: MutableSet<String>
    ) {

        when (value) {

            is JSONObject -> {

                val leagueArray =
                    value.optJSONArray("league")

                if (leagueArray != null) {

                    parseLeagueArray(
                        leagueArray,
                        output,
                        leagueIds
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
                            child,
                            output,
                            leagueIds
                        )
                    }
                }
            }

            is JSONArray -> {

                for (i in 0 until value.length()) {

                    collectAllLeagues(
                        value.opt(i),
                        output,
                        leagueIds
                    )
                }
            }
        }
    }

    /**
     * Az összes bajnokság feldolgozása.
     */
    private fun parseLeagueArray(
        leagues: JSONArray,
        output: MutableList<ScorexMatch>,
        leagueIds: MutableSet<String>
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

            val matchArray =
                leagueObject.optJSONArray("match")
                    ?: continue

            parseMatches(
                matchArray,
                leagueName,
                output
            )
        }
    }

    /**
     * Egy bajnokság összes mérkőzésének feldolgozása.
     */
    private fun parseMatches(
        matchArray: JSONArray,
        leagueName: String,
        output: MutableList<ScorexMatch>
    ) {

        for (i in 0 until matchArray.length()) {

            val matchObject =
                matchArray.optJSONObject(i) ?: continue

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

            if (
                homeTeam.isBlank() ||
                awayTeam.isBlank()
            ) {
                continue
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
                    "$leagueName-$homeTeam-$awayTeam-$date-$kickoff-$i"
                }

            val homeScore = homeObject
                ?.optString("goals")
                .orEmpty()
                .ifBlank { "-" }

            val awayScore = awayObject
                ?.optString("goals")
                .orEmpty()
                .ifBlank { "-" }

            val status = matchObject
                .optString("status")
                .trim()

            val injuryMinute = matchObject
                .optString("inj_minute")
                .trim()

            val matchTime = when {

                // Hosszabbításban megadott perc.
                injuryMinute.isNotBlank() ->
                    injuryMinute

                // Befejezett mérkőzés.
                status.equals("FT", true) ->
                    "FT"

                // Félidő.
                status.equals("HT", true) ->
                    "HT"

                // Élő mérkőzés státusza vagy perce.
                status.isNotBlank() &&
                    status !in listOf("NS", "TBD") ->
                    status

                // Még el nem kezdődött mérkőzés.
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
}
