
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
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            } else {
                connection.errorStream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()
            }

            if (code !in 200..299) {
                throw Exception(
                    when (code) {
                        401, 403 -> "Hibás API-kulcs vagy hozzáférés."
                        429 -> "API lekérési korlát elérve."
                        else -> "StatPal HTTP hiba: $code"
                    }
                )
            }

            val root = JSONTokener(response).nextValue()

            if (root is JSONObject) {
                if (
                    root.optBoolean("success", true).not()
                ) {
                    throw Exception(
                        root.optString(
                            "message",
                            "A StatPal hibát jelzett."
                        )
                    )
                }
            }

            val matches = mutableListOf<ScorexMatch>()

            collectMatches(root, matches)

            return matches.distinctBy { it.id }

        } finally {
            connection.disconnect()
        }
    }

    private fun collectMatches(
        value: Any?,
        output: MutableList<ScorexMatch>
    ) {
        when (value) {

            is JSONObject -> {

                val home = getTeam(
                    value,
                    "home_team",
                    "homeTeam",
                    "home"
                )

                val away = getTeam(
                    value,
                    "away_team",
                    "awayTeam",
                    "away"
                )

                if (home.isNotBlank() && away.isNotBlank()) {

                    output.add(
                        ScorexMatch(
                            id = getString(
                                value,
                                "id",
                                "match_id",
                                "fixture_id"
                            ).ifBlank {
                                "$home-$away"
                            },
                            league = getNestedString(
                                value,
                                "league",
                                "competition",
                                "tournament",
                                "league_name"
                            ).ifBlank {
                                "Ismeretlen bajnokság"
                            },
                            homeTeam = home,
                            awayTeam = away,
                            homeScore = getScore(
                                value,
                                true
                            ),
                            awayScore = getScore(
                                value,
                                false
                            ),
                            matchTime = getString(
                                value,
                                "minute",
                                "elapsed",
                                "match_time",
                                "status",
                                "time"
                            ).ifBlank {
                                "ÉLŐ"
                            }
                        )
                    )
                }

                val iterator = value.keys()

                while (iterator.hasNext()) {
                    val child = value.opt(iterator.next())

                    if (
                        child is JSONObject ||
                        child is JSONArray
                    ) {
                        collectMatches(child, output)
                    }
                }
            }

            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectMatches(value.opt(i), output)
                }
            }
        }
    }

    private fun getTeam(
        json: JSONObject,
        vararg keys: String
    ): String {
        val value = getValue(json, *keys)

        return when (value) {
            is JSONObject -> getString(
                value,
                "name",
                "team_name",
                "display_name"
            )

            is String -> value

            else -> ""
        }
    }

    private fun getScore(
        json: JSONObject,
        home: Boolean
    ): String {

        val keys = if (home) {
            arrayOf(
                "home_score",
                "homeScore",
                "score_home",
                "localteam_score"
            )
        } else {
            arrayOf(
                "away_score",
                "awayScore",
                "score_away",
                "visitorteam_score"
            )
        }

        val value = getValue(json, *keys)

        if (value != null) {
            return value.toString()
        }

        val scores = getValue(json, "scores", "score")

        if (scores is JSONObject) {
            val side = if (home) "home" else "away"
            val sideValue = getValue(scores, side)

            if (sideValue != null) {
                return sideValue.toString()
            }
        }

        return "–"
    }

    private fun getNestedString(
        json: JSONObject,
        vararg keys: String
    ): String {

        val value = getValue(json, *keys)

        return when (value) {
            is JSONObject -> getString(
                value,
                "name",
                "title",
                "league_name"
            )

            is String -> value

            else -> ""
        }
    }

    private fun getString(
        json: JSONObject,
        vararg keys: String
    ): String {
        val value = getValue(json, *keys)

        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject -> getString(
                value,
                "name",
                "value",
                "title"
            )
            else -> value.toString()
        }
    }

    private fun getValue(
        json: JSONObject,
        vararg keys: String
    ): Any? {

        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                return json.opt(key)
            }
        }

        val iterator = json.keys()

        while (iterator.hasNext()) {
            val actualKey = iterator.next()

            if (
                keys.any {
                    it.equals(actualKey, ignoreCase = true)
                } && !json.isNull(actualKey)
            ) {
                return json.opt(actualKey)
            }
        }

        return null
    }
}
