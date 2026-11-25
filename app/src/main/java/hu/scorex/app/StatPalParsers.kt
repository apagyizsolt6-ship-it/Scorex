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
    val awayStarting: List<LineupPlayerItem>,
    val homeBench: List<LineupPlayerItem> = emptyList(),
    val awayBench: List<LineupPlayerItem> = emptyList(),
    val homeCoach: String = "",
    val awayCoach: String = "",
    val status: String = ""
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

        fun extractYear(s: String): String? {
            val trimmed = s.trim()
            // Prefer pure 4-digit year or "2023/2024" style
            val pureYear = Regex("^20\\d{2}$").find(trimmed)
            if (pureYear != null) return pureYear.value
            val range = Regex("(20\\d{2})/(20\\d{2})").find(trimmed)
            if (range != null) return range.value
            val anyYear = Regex("20\\d{2}").find(trimmed)
            return anyYear?.value
        }

        fun walk(v: Any?) {
            when (v) {
                is JSONObject -> {
                    // Prefer explicit season/name fields
                    listOf("season", "name", "year").forEach { key ->
                        val s = v.optString(key).trim()
                        if (s.isNotBlank() && !s.startsWith("{") && !s.startsWith("[")) {
                            extractYear(s)?.let { seasons.add(it) }
                        }
                    }
                    val it = v.keys()
                    while (it.hasNext()) walk(v.opt(it.next()))
                }
                is JSONArray -> {
                    for (i in 0 until v.length()) {
                        val item = v.opt(i)
                        when (item) {
                            is String -> {
                                if (!item.trim().startsWith("{") && !item.trim().startsWith("[")) {
                                    extractYear(item)?.let { seasons.add(it) }
                                }
                            }
                            else -> walk(item)
                        }
                    }
                }
                is String -> {
                    if (!v.trim().startsWith("{") && !v.trim().startsWith("[")) {
                        extractYear(v)?.let { seasons.add(it) }
                    }
                }
            }
        }
        walk(root)
        return seasons.toList().sortedDescending()
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

        fun addIfValid(market: String, h: String, d: String, a: String) {
            val cleanH = h.trim().ifBlank { "-" }
            val cleanD = d.trim().ifBlank { "-" }
            val cleanA = a.trim().ifBlank { "-" }
            if (cleanH != "-" || cleanD != "-" || cleanA != "-") {
                oddsList.add(MatchOddsItem(market.trim().ifBlank { "1X2" }, cleanH, cleanD, cleanA))
            }
        }

        // Handle live odds structure: live_matches[].odds or stats
        fun processMatchContainer(container: JSONObject) {
            // 1) Explicit odds array
            val oddsArr = container.optJSONArray("odds")
            if (oddsArr != null) {
                for (i in 0 until oddsArr.length()) {
                    val o = oddsArr.optJSONObject(i) ?: continue
                    val market = o.optString("name").ifBlank { o.optString("market") }.ifBlank { o.optString("type") }.ifBlank { "1X2" }
                    val h = o.optString("1").ifBlank { o.optString("home") }.ifBlank { o.optString("home_odd") }
                    val d = o.optString("X").ifBlank { o.optString("draw") }.ifBlank { o.optString("draw_odd") }
                    val a = o.optString("2").ifBlank { o.optString("away") }.ifBlank { o.optString("away_odd") }
                    addIfValid(market, h, d, a)
                }
            }

            // 2) Stats object (Goal, Corner, YellowCard etc. as live counts or odds)
            val stats = container.optJSONObject("stats")
            if (stats != null) {
                val keys = stats.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val s = stats.optJSONObject(k) ?: continue
                    val name = s.optString("name").ifBlank { k }
                    val h = s.optString("home").ifBlank { s.optString("1") }
                    val a = s.optString("away").ifBlank { s.optString("2") }
                    if (name.isNotBlank() && (h.isNotBlank() || a.isNotBlank()) && name != "Team") {
                        addIfValid(name, h, "-", a)
                    }
                }
            }

            // 3) Fallback: scan for simple odd fields
            if (container.has("1") || container.has("home") || container.has("odd")) {
                val market = container.optString("name").ifBlank { container.optString("market") }.ifBlank { "1X2" }
                val h = container.optString("1").ifBlank { container.optString("home") }
                val d = container.optString("X").ifBlank { container.optString("draw") }
                val a = container.optString("2").ifBlank { container.optString("away") }
                addIfValid(market, h, d, a)
            }
        }

        when (root) {
            is JSONObject -> {
                val liveMatches = root.optJSONArray("live_matches")
                if (liveMatches != null) {
                    for (i in 0 until liveMatches.length()) {
                        processMatchContainer(liveMatches.optJSONObject(i) ?: continue)
                    }
                } else {
                    processMatchContainer(root)
                }
            }
            is JSONArray -> {
                for (i in 0 until root.length()) {
                    val item = root.optJSONObject(i) ?: continue
                    processMatchContainer(item)
                }
            }
        }

        return oddsList.distinctBy { "${it.market}-${it.homeOdd}-${it.drawOdd}-${it.awayOdd}" }.take(12)
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
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() as? JSONObject ?: return null
        val pred = root.optJSONObject("prediction") ?: return null
        val choice = pred.optString("choice").ifBlank { pred.optString("winner") }.ifBlank { "-" }
        val reasoning = pred.optString("reasoning").ifBlank { pred.optString("advice") }.ifBlank { "Nincs részletes indoklás" }
        val odds = pred.optJSONObject("prematch_odds")
        val homeP = odds?.optString("home").orEmpty().ifBlank { "-" }
        val drawP = odds?.optString("draw").orEmpty().ifBlank { "-" }
        val awayP = odds?.optString("away").orEmpty().ifBlank { "-" }
        return MatchPredictionInfo(
            favoriteSide = choice,
            homeWinProb = homeP,
            drawProb = drawP,
            awayWinProb = awayP,
            advice = reasoning
        )
    }

    fun parseH2H(rawJson: String): List<String> {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() as? JSONObject ?: return emptyList()
        val h2h = root.optJSONObject("head-to-head") ?: root
        val recent = h2h.optJSONObject("recent_meetings")?.optJSONArray("match")
            ?: h2h.optJSONArray("match")
        val results = mutableListOf<String>()
        if (recent != null) {
            for (i in 0 until minOf(recent.length(), 8)) {
                val m = recent.optJSONObject(i) ?: continue
                val date = m.optString("date").trim()
                val t1 = m.optString("team1_name").ifBlank { m.optJSONObject("home")?.optString("name").orEmpty() }
                val t2 = m.optString("team2_name").ifBlank { m.optJSONObject("away")?.optString("name").orEmpty() }
                val s1 = m.optString("team1_score").ifBlank { m.optJSONObject("home")?.optString("goals").orEmpty() }.ifBlank { "-" }
                val s2 = m.optString("team2_score").ifBlank { m.optJSONObject("away")?.optString("goals").orEmpty() }.ifBlank { "-" }
                if (t1.isNotBlank() && t2.isNotBlank()) {
                    results.add(if (date.isNotBlank()) "$date • $t1 $s1 : $s2 $t2" else "$t1 $s1 : $s2 $t2")
                }
            }
        }
        // Fallback to old style
        if (results.isEmpty()) {
            val matchObjs = mutableListOf<JSONObject>()
            extractObjects(root, { it.has("home") && it.has("away") }, matchObjs)
            matchObjs.take(8).forEach { m ->
                val h = m.optJSONObject("home")
                val a = m.optJSONObject("away")
                val hName = h?.optString("name").orEmpty().trim()
                val aName = a?.optString("name").orEmpty().trim()
                val hGoals = h?.optString("goals").orEmpty().ifBlank { "-" }
                val aGoals = a?.optString("goals").orEmpty().ifBlank { "-" }
                val date = m.optString("date").trim()
                if (hName.isNotBlank() && aName.isNotBlank()) {
                    results.add(if (date.isNotBlank()) "$date • $hName $hGoals : $aGoals $aName" else "$hName $hGoals : $aGoals $aName")
                }
            }
        }
        return results.distinct().take(10)
    }

    fun parseLineups(rawJson: String): MatchLineupsInfo? {
        val root = runCatching { JSONTokener(rawJson).nextValue() }.getOrNull() as? JSONObject ?: return null

        fun readPlayers(arr: JSONArray?): List<LineupPlayerItem> {
            if (arr == null) return emptyList()
            val list = mutableListOf<LineupPlayerItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val name = p.optString("name").ifBlank { p.optString("player_name") }.trim()
                if (name.isBlank()) continue
                list.add(
                    LineupPlayerItem(
                        number = p.optString("number").ifBlank { p.optString("shirt_number") }.ifBlank { "•" },
                        name = name,
                        position = p.optString("position").ifBlank { p.optString("pos") }.ifBlank { "player" },
                        playerId = p.optString("id").ifBlank { p.optString("player_id") }
                    )
                )
            }
            return list
        }

        fun readSide(side: JSONObject?): Triple<String, List<LineupPlayerItem>, List<LineupPlayerItem>> {
            if (side == null) return Triple("-", emptyList(), emptyList())
            val formation = side.optString("team_formation").ifBlank { side.optString("formation") }.ifBlank { "-" }
            val starting = readPlayers(side.optJSONArray("starting_xi") ?: side.optJSONArray("starting_lineup") ?: side.optJSONArray("players"))
            val bench = readPlayers(side.optJSONArray("bench"))
            return Triple(formation, starting, bench)
        }

        val home = root.optJSONObject("home")
        val away = root.optJSONObject("away")
        if (home == null && away == null) return null

        val (hForm, hStart, hBench) = readSide(home)
        val (aForm, aStart, aBench) = readSide(away)

        return MatchLineupsInfo(
            homeFormation = hForm,
            awayFormation = aForm,
            homeStarting = hStart,
            awayStarting = aStart,
            homeBench = hBench,
            awayBench = aBench,
            homeCoach = home?.optJSONObject("coach")?.optString("name").orEmpty(),
            awayCoach = away?.optJSONObject("coach")?.optString("name").orEmpty(),
            status = root.optString("status")
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
