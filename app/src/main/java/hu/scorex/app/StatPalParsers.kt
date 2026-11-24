package hu.scorex.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class LeagueSummaryItem(
    val id: String,
    val name: String,
    val country: String,
    val season: String
)

data class LeagueStatsSummary(
    val leagueId: String,
    val title: String,
    val highlights: List<Pair<String, String>>,
    val recentMatches: List<String>
)

data class MatchOddsItem(
    val market: String,
    val homeOdd: String,
    val drawOdd: String,
    val awayOdd: String
)

data class MatchWeatherInfo(
    val condition: String,
    val temperature: String,
    val wind: String,
    val humidity: String
)

data class MatchPredictionInfo(
    val favoriteSide: String,
    val homeWinProb: String,
    val drawProb: String,
    val awayWinProb: String,
    val advice: String
)

data class LineupPlayerItem(
    val number: String,
    val name: String,
    val position: String,
    val playerId: String
)

data class MatchLineupsInfo(
    val homeFormation: String,
    val awayFormation: String,
    val homeStarting: List<LineupPlayerItem>,
    val awayStarting: List<LineupPlayerItem>
)

data class InjuryItem(
    val team: String,
    val playerName: String,
    val playerId: String,
    val reason: String,
    val status: String
)

data class EntityProfileData(
    val kindLabel: String,
    val id: String,
    val name: String,
    val subtitle: String,
    val details: List<Pair<String, String>>,
    val relatedItems: List<Triple<String, String, String>> // (kind: "team"|"player"|"coach", id, label)
)

object StatPalParsers {

    private fun extractObjects(value: Any?, predicate: (JSONObject) -> Boolean, out: MutableList<JSONObject>) {
        when (value) {
            is JSONObject -> {
                if (predicate(value)) {
                    out.add(value)
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    extractObjects(value.opt(keys.next()), predicate, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    extractObjects(value.opt(i), predicate, out)
                }
            }
        }
    }

    fun parseLeagues(rawJson: String): List<LeagueSummaryItem> {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return emptyList()
        val found = mutableListOf<JSONObject>()
        extractObjects(
            root,
            { obj -> obj.has("name") && (obj.has("id") || obj.has("league_id")) && !obj.has("home") },
            found
        )
        val seen = mutableSetOf<String>()
        return found.mapNotNull { obj ->
            val id = obj.optString("id").ifBlank { obj.optString("league_id") }.trim()
            val name = obj.optString("name").trim()
            if (id.isBlank() || name.isBlank() || !seen.add(id)) return@mapNotNull null
            val country = obj.optString("country")
                .ifBlank { obj.optJSONObject("country")?.optString("name").orEmpty() }
                .trim()
            val season = obj.optString("season").trim()
            LeagueSummaryItem(
                id = id,
                name = if (country.isNotBlank() && !name.contains(":")) "$country: $name" else name,
                country = country,
                season = season
            )
        }
    }

    fun parseSeasons(rawJson: String): List<String> {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return emptyList()
        val seasons = linkedSetOf<String>()
        fun walk(v: Any?) {
            when (v) {
                is JSONObject -> {
                    val s = v.optString("season").ifBlank { v.optString("name") }.trim()
                    if (s.matches(Regex(".*20\\d{2}.*"))) seasons.add(s)
                    val it = v.keys()
                    while (it.hasNext()) walk(v.opt(it.next()))
                }
                is JSONArray -> {
                    for (i in 0 until v.length()) {
                        val item = v.opt(i)
                        if (item is String && item.matches(Regex(".*20\\d{2}.*"))) {
                            seasons.add(item.trim())
                        } else {
                            walk(item)
                        }
                    }
                }
            }
        }
        walk(root)
        return seasons.toList()
    }

    fun parseLeagueStats(leagueId: String, leagueTitle: String, statsJson: String, matchesStatsJson: String): LeagueStatsSummary {
        val highlights = mutableListOf<Pair<String, String>>()
        val recentMatches = mutableListOf<String>()

        runCatching {
            val root = JSONTokener(statsJson).nextValue()
            val objs = mutableListOf<JSONObject>()
            extractObjects(root, { it.length() in 2..20 }, objs)
            val primary = objs.firstOrNull()
            if (primary != null) {
                val keys = primary.keys()
                while (keys.hasNext() && highlights.size < 10) {
                    val k = keys.next()
                    val v = primary.opt(k)
                    if (v is String || v is Number) {
                        val cleanVal = v.toString().trim()
                        if (cleanVal.isNotBlank() && k != "id") {
                            highlights.add(k.replace("_", " ").replaceFirstChar { it.uppercase() } to cleanVal)
                        }
                    }
                }
            }
        }

        runCatching {
            val root = JSONTokener(matchesStatsJson).nextValue()
            val matchObjs = mutableListOf<JSONObject>()
            extractObjects(root, { it.has("home") && it.has("away") }, matchObjs)
            matchObjs.take(8).forEach { m ->
                val h = m.optJSONObject("home")
                val a = m.optJSONObject("away")
                val hName = h?.optString("name").orEmpty().trim()
                val aName = a?.optString("name").orEmpty().trim()
                val hGoals = h?.optString("goals").orEmpty().ifBlank { "-" }
                val aGoals = a?.optString("goals").orEmpty().ifBlank { "-" }
                if (hName.isNotBlank() && aName.isNotBlank()) {
                    recentMatches.add("$hName $hGoals : $aGoals $aName")
                }
            }
        }

        return LeagueStatsSummary(
            leagueId = leagueId,
            title = leagueTitle,
            highlights = highlights,
            recentMatches = recentMatches
        )
    }

    fun parseOdds(rawJson: String): List<MatchOddsItem> {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return emptyList()
        val oddsList = mutableListOf<MatchOddsItem>()
        val objs = mutableListOf<JSONObject>()
        extractObjects(root, { it.has("odd") || it.has("odds") || it.has("home") || it.has("1") }, objs)

        objs.forEach { o ->
            val market = o.optString("name").ifBlank { o.optString("market") }.ifBlank { "1X2" }
            val h = o.optString("1").ifBlank { o.optString("home") }.trim()
            val d = o.optString("X").ifBlank { o.optString("draw") }.trim()
            val a = o.optString("2").ifBlank { o.optString("away") }.trim()
            if (h.isNotBlank() || d.isNotBlank() || a.isNotBlank()) {
                oddsList.add(
                    MatchOddsItem(
                        market = market,
                        homeOdd = h.ifBlank { "-" },
                        drawOdd = d.ifBlank { "-" },
                        awayOdd = a.ifBlank { "-" }
                    )
                )
            }
        }
        return oddsList.distinctBy { "${it.market}-${it.homeOdd}-${it.drawOdd}-${it.awayOdd}" }.take(8)
    }

    fun parseWeather(rawJson: String): MatchWeatherInfo? {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return null
        val objs = mutableListOf<JSONObject>()
        extractObjects(
            root,
            { it.has("temp") || it.has("temperature") || it.has("weather") || it.has("description") },
            objs
        )
        val w = objs.firstOrNull() ?: return null
        val cond = w.optString("description").ifBlank { w.optString("weather") }.ifBlank { "Elérhető" }
        val temp = w.optString("temperature").ifBlank { w.optString("temp") }.ifBlank { "-" }
        val wind = w.optString("wind").ifBlank { w.optString("wind_speed") }.ifBlank { "-" }
        val hum = w.optString("humidity").ifBlank { "-" }
        return MatchWeatherInfo(
            condition = cond,
            temperature = if (temp != "-" && !temp.contains("°")) "$temp °C" else temp,
            wind = wind,
            humidity = if (hum != "-" && !hum.contains("%")) "$hum%" else hum
        )
    }

    fun parsePrediction(rawJson: String): MatchPredictionInfo? {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return null
        val objs = mutableListOf<JSONObject>()
        extractObjects(
            root,
            { it.has("advice") || it.has("winner") || it.has("home") || it.has("probabilities") },
            objs
        )
        val p = objs.firstOrNull() ?: return null
        val probs = p.optJSONObject("probabilities") ?: p
        val homeP = probs.optString("home").ifBlank { "-" }
        val drawP = probs.optString("draw").ifBlank { "-" }
        val awayP = probs.optString("away").ifBlank { "-" }
        val advice = p.optString("advice").ifBlank { p.optString("prediction") }.ifBlank { "Nincs szöveges javaslat" }
        val fav = p.optString("winner").ifBlank {
            when {
                homeP.removeSuffix("%").toFloatOrNull() ?: 0f > (awayP.removeSuffix("%").toFloatOrNull() ?: 0f) -> "Hazai esélyesebb"
                awayP.removeSuffix("%").toFloatOrNull() ?: 0f > (homeP.removeSuffix("%").toFloatOrNull() ?: 0f) -> "Vendég esélyesebb"
                else -> "Kiegyenlített"
            }
        }
        return MatchPredictionInfo(
            favoriteSide = fav,
            homeWinProb = homeP,
            drawProb = drawP,
            awayWinProb = awayP,
            advice = advice
        )
    }

    fun parseH2H(rawJson: String): List<String> {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return emptyList()
        val matchObjs = mutableListOf<JSONObject>()
        extractObjects(root, { it.has("home") && it.has("away") }, matchObjs)
        return matchObjs.mapNotNull { m ->
            val h = m.optJSONObject("home")
            val a = m.optJSONObject("away")
            val hName = h?.optString("name").orEmpty().trim()
            val aName = a?.optString("name").orEmpty().trim()
            val hGoals = h?.optString("goals").orEmpty().ifBlank { "-" }
            val aGoals = a?.optString("goals").orEmpty().ifBlank { "-" }
            val date = m.optString("date").trim()
            if (hName.isBlank() || aName.isBlank()) null
            else if (date.isNotBlank()) "$date • $hName $hGoals : $aGoals $aName"
            else "$hName $hGoals : $aGoals $aName"
        }.distinct().take(10)
    }

    fun parseLineups(rawJson: String): MatchLineupsInfo? {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return null
        val objs = mutableListOf<JSONObject>()
        extractObjects(root, { it.has("home") && it.has("away") }, objs)
        val container = objs.firstOrNull() ?: return null

        fun readSide(sideObj: JSONObject?): Pair<String, List<LineupPlayerItem>> {
            if (sideObj == null) return "-" to emptyList()
            val formation = sideObj.optString("formation").ifBlank { "-" }
            val list = mutableListOf<LineupPlayerItem>()
            val arr = sideObj.optJSONArray("starting_lineup")
                ?: sideObj.optJSONArray("players")
                ?: sideObj.optJSONArray("lineup")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val name = p.optString("name").ifBlank { p.optString("player_name") }.trim()
                    if (name.isNotBlank()) {
                        list.add(
                            LineupPlayerItem(
                                number = p.optString("number").ifBlank { p.optString("shirt_number") }.ifBlank { "•" },
                                name = name,
                                position = p.optString("pos").ifBlank { p.optString("position") },
                                playerId = p.optString("id").ifBlank { p.optString("player_id") }
                            )
                        )
                    }
                }
            }
            return formation to list
        }

        val (hForm, hPlayers) = readSide(container.optJSONObject("home"))
        val (aForm, aPlayers) = readSide(container.optJSONObject("away"))
        if (hPlayers.isEmpty() && aPlayers.isEmpty() && hForm == "-" && aForm == "-") return null
        return MatchLineupsInfo(
            homeFormation = hForm,
            awayFormation = aForm,
            homeStarting = hPlayers,
            awayStarting = aPlayers
        )
    }

    fun parseInjuries(rawJson: String): List<InjuryItem> {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() ?: return emptyList()
        val objs = mutableListOf<JSONObject>()
        extractObjects(
            root,
            { (it.has("player") || it.has("player_name") || it.has("name")) && (it.has("reason") || it.has("type") || it.has("injury")) },
            objs
        )
        return objs.mapNotNull { o ->
            val pName = o.optString("player_name")
                .ifBlank { o.optJSONObject("player")?.optString("name").orEmpty() }
                .ifBlank { o.optString("name") }
                .trim()
            if (pName.isBlank()) return@mapNotNull null
            val pId = o.optString("player_id").ifBlank { o.optJSONObject("player")?.optString("id").orEmpty() }
            val team = o.optString("team_name").ifBlank { o.optJSONObject("team")?.optString("name").orEmpty() }
            val reason = o.optString("reason").ifBlank { o.optString("injury") }.ifBlank { "Sérülés / eltiltás" }
            val status = o.optString("type").ifBlank { o.optString("status") }.ifBlank { "Hiányzó" }
            InjuryItem(
                team = team,
                playerName = pName,
                playerId = pId,
                reason = reason,
                status = status
            )
        }.distinctBy { "${it.team}-${it.playerName}" }.take(15)
    }

    fun parseEntityProfile(kind: String, id: String, rawJson: String): EntityProfileData {
        val kindLabel = when (kind) {
            "team" -> "CSAPAT PROFIL"
            "player" -> "JÁTÉKOS PROFIL"
            "coach" -> "EDZŐ PROFIL"
            else -> "PROFIL"
        }
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull()
        val objs = mutableListOf<JSONObject>()
        extractObjects(root, { it.has("name") }, objs)
        val main = objs.firstOrNull()

        val name = main?.optString("name").orEmpty().ifBlank { "#$id" }
        val subtitle = when (kind) {
            "team" -> main?.optString("country").orEmpty().ifBlank { main?.optString("venue_name").orEmpty() }
            "player" -> main?.optString("position").orEmpty().ifBlank { main?.optString("nationality").orEmpty() }
            "coach" -> main?.optString("nationality").orEmpty().ifBlank { main?.optString("team").orEmpty() }
            else -> ""
        }

        val details = mutableListOf<Pair<String, String>>()
        val related = mutableListOf<Triple<String, String, String>>()

        if (main != null) {
            val skipKeys = setOf("id", "name", "squad", "players", "coach")
            val keys = main.keys()
            while (keys.hasNext() && details.size < 12) {
                val k = keys.next()
                if (k in skipKeys) continue
                val v = main.opt(k)
                if (v is String || v is Number) {
                    val s = v.toString().trim()
                    if (s.isNotBlank() && s != "null") {
                        details.add(k.replace("_", " ").replaceFirstChar { it.uppercase() } to s)
                    }
                }
            }

            val coachObj = main.optJSONObject("coach")
            if (coachObj != null) {
                val cId = coachObj.optString("id").trim()
                val cName = coachObj.optString("name").trim()
                if (cId.isNotBlank() && cName.isNotBlank()) {
                    related.add(Triple("coach", cId, "Edző: $cName"))
                }
            }

            val squadArr = main.optJSONArray("squad") ?: main.optJSONArray("players")
            if (squadArr != null) {
                for (i in 0 until squadArr.length()) {
                    val p = squadArr.optJSONObject(i) ?: continue
                    val pId = p.optString("id").trim()
                    val pName = p.optString("name").trim()
                    val pos = p.optString("position").trim()
                    if (pId.isNotBlank() && pName.isNotBlank()) {
                        val label = if (pos.isNotBlank()) "$pName ($pos)" else pName
                        related.add(Triple("player", pId, label))
                    }
                }
            }

            val teamObj = main.optJSONObject("team")
            if (teamObj != null) {
                val tId = teamObj.optString("id").trim()
                val tName = teamObj.optString("name").trim()
                if (tId.isNotBlank() && tName.isNotBlank()) {
                    related.add(Triple("team", tId, "Csapat: $tName"))
                }
            }
        }

        return EntityProfileData(
            kindLabel = kindLabel,
            id = id,
            name = name,
            subtitle = subtitle,
            details = details,
            relatedItems = related
        )
    }
}
