
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

            // A teljes JSON-válasz feldolgozása.
            collectAllLeagues(root, matches)

            // Nincs darabszámkorlát és nincs ID-alapú szűrés.
            return matches

        } finally {
            connection.disconnect()
        }
    }

    /**
     * A teljes JSON-struktúrát bejárja.
     * Minden olyan objektumot feldolgoz,
     * amely tartalmaz egy match nevű tömböt.
     */
    private fun collectAllLeagues(
        value: Any?,
        output: MutableList<ScorexMatch>
    ) {

        when (value) {

            is JSONObject -> {

                // Ha ez egy bajnokság objektuma,
                // feldolgozzuk az összes mérkőzését.
                if (value.optJSONArray("match") != null) {
                    parseLeagueObject(value, output)
                }

                // Minden további objektumot és tömböt
                // külön is átvizsgálunk.
                val iterator = value.keys()

                while (iterator.hasNext()) {

                    val child = value.opt(iterator.next())

                    if (
                        child is JSONObject ||
                        child is JSONArray
                    ) {
                        collectAllLeagues(child, output)
                    }
                }
            }

            is JSONArray -> {

                for (i in 0 until value.length()) {
                    collectAllLeagues(
                        value.opt(i),
                        output
                    )
                }
            }
        }
    }

    /**
     * Egy bajnokság és az összes hozzá tartozó
     * mérkőzés feldolgozása.
     */
    private fun parseLeagueObject(
        leagueObject: JSONObject,
        output: MutableList<ScorexMatch>
    ) {

        val leagueName = leagueObject
            .optString("name")
            .trim()
            .ifBlank {
                "Ismeretlen bajnokság"
            }

        val matchArray =
            leagueObject.optJSONArray("match")
                ?: return

        for (i in 0 until matchArray.length()) {

            val matchObject =
                matchArray.optJSONObject(i)
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

            val date = matchObject
                .optString("date")
                .trim()

            val time = matchObject
                .optString("time")
                .trim()

            // Elsődleges és tartalék mérkőzésazonosítók.
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
                    "$leagueName-$homeTeam-$awayTeam-$date-$time-$i"
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

                time.isNotBlank() ->
                    time

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
