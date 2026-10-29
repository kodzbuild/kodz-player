package com.kodz.player.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Chave de teste gratuita do TheSportsDB (limite de 30 consultas por minuto).
// Para uso comercial, troque pela chave de assinante em https://www.thesportsdb.com
const val SPORTS_DB_KEY = "3"
private const val SPORTS_DB = "https://www.thesportsdb.com/api/v1/json/$SPORTS_DB_KEY"

enum class GameState { Live, Started, Scheduled, Finished, Off }

data class Game(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val homeBadge: String?,
    val awayBadge: String?,
    val homeScore: String?,
    val awayScore: String?,
    val state: GameState,
    val startMillis: Long,
    val channels: List<String>,
)

private fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15_000
    c.readTimeout = 20_000
    c.setRequestProperty("User-Agent", "KodzPlayer/1.0")
    try {
        if (c.responseCode !in 200..299) throw HttpError(c.responseCode)
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

// O horário vem em UTC; convertemos para milissegundos.
private fun parseUtc(ts: String, date: String, time: String): Long? {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    val candidates = listOf(ts, if (date.isNotBlank() && time.isNotBlank()) "${date}T$time" else "")
    for (c in candidates) {
        if (c.length < 19) continue
        try {
            val d = fmt.parse(c.substring(0, 19))
            if (d != null) return d.time
        } catch (e: Exception) {
            // tenta o próximo formato
        }
    }
    return null
}

private fun stateOf(status: String, start: Long, now: Long): GameState {
    val s = status.trim().lowercase()
    return when {
        listOf("postpon", "cancel", "abandon", "suspend", "walkover").any { it in s } -> GameState.Off
        "finish" in s || s in setOf("ft", "aet", "pen", "aot") -> GameState.Finished
        s.isEmpty() || s in setOf("ns", "not started", "tbd") ->
            // A chave gratuita não atualiza o placar em tempo real; estimamos pelo horário.
            if (now >= start && now < start + 150 * 60_000L) GameState.Started else GameState.Scheduled
        else -> GameState.Live
    }
}

private val TOP_LEAGUES = listOf(
    "brazil", "libertadores", "sudamericana", "champions", "premier", "la liga",
    "italian serie a", "bundesliga", "ligue 1", "major league soccer", "copa",
)

private fun leagueRank(league: String): Int {
    val l = league.lowercase()
    val i = TOP_LEAGUES.indexOfFirst { it in l }
    return if (i < 0) 99 else i
}

private fun stateRank(s: GameState): Int = when (s) {
    GameState.Live -> 0
    GameState.Started -> 1
    GameState.Scheduled -> 2
    GameState.Finished -> 3
    GameState.Off -> 4
}

private fun firstArray(root: JSONObject): JSONArray? {
    val keys = root.keys()
    while (keys.hasNext()) {
        val a = root.optJSONArray(keys.next())
        if (a != null) return a
    }
    return null
}

// Busca os jogos de futebol do dia (no fuso do aparelho) e os canais que transmitem no Brasil.
fun fetchTodayGames(): List<Game> {
    val now = System.currentTimeMillis()
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = dayFmt.format(Date(now))
    val dates = listOf(-1, 0, 1).map { dayFmt.format(Date(now + it * 86_400_000L)) }.distinct()

    val events = LinkedHashMap<String, JSONObject>()
    var ok = false
    var lastError: Exception? = null
    for (d in dates) {
        try {
            val root = JSONObject(httpGet("$SPORTS_DB/eventsday.php?d=$d&s=Soccer"))
            ok = true
            val a = root.optJSONArray("events") ?: continue
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                events[o.str("idEvent")] = o
            }
        } catch (e: Exception) {
            lastError = e
        }
    }
    if (!ok) throw lastError ?: IOException("Sem resposta do servidor de jogos")

    // Canais de transmissão (opcional: se falhar, os jogos aparecem sem canais).
    val tv = HashMap<String, MutableList<String>>()
    for (d in dates) {
        try {
            val root = JSONObject(httpGet("$SPORTS_DB/eventstv.php?d=$d&a=Brazil&s=Soccer"))
            val a = firstArray(root) ?: continue
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val ch = o.str("strChannel")
                if (ch.isBlank()) continue
                val list = tv.getOrPut(o.str("idEvent")) { mutableListOf() }
                if (ch !in list) list.add(ch)
            }
        } catch (e: Exception) {
            // sem canais
        }
    }

    val out = ArrayList<Game>()
    for ((id, o) in events) {
        val start = parseUtc(o.str("strTimestamp"), o.str("dateEvent"), o.str("strTime")) ?: continue
        if (dayFmt.format(Date(start)) != today) continue
        out.add(
            Game(
                id = id,
                league = o.str("strLeague"),
                home = o.str("strHomeTeam"),
                away = o.str("strAwayTeam"),
                homeBadge = o.str("strHomeTeamBadge").ifBlank { null },
                awayBadge = o.str("strAwayTeamBadge").ifBlank { null },
                homeScore = o.str("intHomeScore").ifBlank { null },
                awayScore = o.str("intAwayScore").ifBlank { null },
                state = stateOf(o.str("strStatus"), start, now),
                startMillis = start,
                channels = tv[id].orEmpty(),
            ),
        )
    }
    return out
        .sortedWith(compareBy<Game>({ stateRank(it.state) }, { leagueRank(it.league) }, { it.startMillis }))
        .take(40)
}
