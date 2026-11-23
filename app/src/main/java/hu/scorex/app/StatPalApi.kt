
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
        "https://statpal.io/api/v2/soccer/matches/live"

    @Volatile
    var lastRawResponse: String = ""
        private set

    fun fetchLiveMatches(apiKey: String): List<ScorexMatch> {

        require(apiKey.isNotBlank()) {
            "Hiányzik a StatPal API-kulcs!"
        }

        val encodedKey = URLEncoder.encode(
            apiKey.trim(),
            "UTF-8"
        )

        val connection = URL(
            "$BASE_URL?access_key=$encodedKey"
        ).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
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

                        429 ->
                            "API lekérési korlát elérve."

                        else ->
                            "StatPal HTTP hiba: $code"
                    }
                )
            }

            val root = JSONTokener(response).nextValue()

            if (
                root is JSONObject &&
                !root.optBoolean("success", true)
            ) {
                throw Exception(
                    root.optString(
                        "message",
                        "A StatPal hibát jelzett."
                    )
                )
            }

            val matches = mutableListOf<ScorexMatch>()

            collectLeagues(root, matches)

            return matches.distinctBy { it.id }

        } finally {
            connection.disconnect()
        }
    }

    /**
     * A StatPal válaszának feldolgozása.
     *
     * A bajnokságok a league tömbben találhatók.
     * A bajnokság neve: league.name
     * A mérkőzések: league.match
     */
    private fun collectLeagues(
        value: Any?,
        output: MutableList<ScorexMatch>
    ) {

        when (value) {

            is JSONObject -> {

                // Megkeressük a bajnokságokat tartalmazó tömböt.
                val leagues = value.optJSONArray("league")

                if (leagues != null) {
                    parseLeagueArray(leagues, output)
                }

                // A külső objektumokban tovább keresünk.
                val iterator = value.keys()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val child = value.opt(key)

                    if (
                        child is JSONObject ||
                        child is JSONArray
                    ) {
                        collectLeagues(child, output)
                    }
                }
            }

            is JSONArray -> {

                for (i in 0 until value.length()) {
                    collectLeagues(
                        value.opt(i),
                        output
                    )
                }
            }
        }
    }

    /**
     * Egy bajnokságlista feldolgozása.
     */
    private fun parseLeagueArray(
        leagues: JSONArray,
        output: MutableList<ScorexMatch>
    ) {

        for (i in 0 until leagues.length()) {

            val leagueObject =
                leagues.optJSONObject(i) ?: continue

            // A bajnokság neve a league objektumban van.
            val leagueName = leagueObject
                .optString("name")
                .trim()
                .ifBlank {
                    "Ismeretlen bajnokság"
                }

            // Az adott bajnokság mérkőzései.
            val matchArray =
                leagueObject.optJSONArray("match")
                    ?: continue

            for (j in 0 until matchArray.length()) {

                val matchObject =
                    matchArray.optJSONObject(j)
                        ?: continue

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

                val matchId = matchObject
                    .optString("main_id")
                    .ifBlank {
                        matchObject.optString("id")
                    }
                    .ifBlank {
                        "$homeTeam-$awayTeam"
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

                val injuryTime = matchObject
                    .optString("inj_time")
                    .trim()

                val matchTime = when {
                    injuryMinute.isNotBlank() ->
                        injuryMinute

                    status.isNotBlank() ->
                        status

                    injuryTime.isNotBlank() ->
                        injuryTime

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
}
