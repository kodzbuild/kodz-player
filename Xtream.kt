package com.kodz.player.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Alguns painéis bloqueiam user-agents desconhecidos; o app tenta estes em ordem.
val USER_AGENTS = listOf(
    "IPTVSmartersPro",
    "VLC/3.0.18 LibVLC/3.0.18",
    "okhttp/4.12.0",
    "Dalvik/2.1.0 (Linux; U; Android 11; SM-G991B Build/RP1A.200720.012)",
    "TiviMate/4.7.0 (Android 11)",
    "Kodi/20.2 (Linux; Android 11) Android/11 Sys_CPU/aarch64 App_Bitness/64 Version/20.2-(20.2.0)",
    "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
)
const val SUPPORT_PHONE = "1194979-3462"

data class Route(val label: String, val baseUrl: String)

// As URLs reais ficam escondidas: o usuário só vê "Rota 1" e "Rota 2".
val ROUTES = listOf(
    Route("Rota 1", "http://lt22.org"),
    Route("Rota 2", "http://kodztv.top"),
)

data class UserInfo(
    val username: String,
    val status: String,
    val expDate: Long?,
    val activeCons: String,
    val maxConnections: String,
)

data class Category(val id: String, val name: String)
data class Channel(val id: String, val name: String, val icon: String?, val categoryId: String)
data class Movie(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val ext: String,
)

data class Series(val id: String, val name: String, val cover: String?, val categoryId: String)
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val plot: String = "",
)

// Sinopse e detalhes de filmes e séries.
data class MediaInfo(
    val plot: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val duration: String = "",
    val cast: String = "",
    val director: String = "",
    val backdrop: String? = null,
)

// Item da programação (guia de TV). Horários em segundos desde 1970.
data class EpgItem(val title: String, val desc: String, val start: Long, val end: Long)

class Catalog(
    val liveCategories: List<Category>,
    val channels: List<Channel>,
    val movieCategories: List<Category>,
    val movies: List<Movie>,
    val seriesCategories: List<Category> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    val channelsByCat: Map<String, List<Channel>> = channels.groupBy { it.categoryId }
    val moviesByCat: Map<String, List<Movie>> = movies.groupBy { it.categoryId }
    val seriesByCat: Map<String, List<Series>> = series.groupBy { it.categoryId }
}

class AuthFailed(msg: String) : Exception(msg)

class HttpError(val code: Int) : IOException("HTTP $code")

fun describeError(e: Exception): String = when (e) {
    is HttpError -> when (e.code) {
        403, 406, 429 -> "O servidor bloqueou o acesso (erro ${e.code})."
        404 -> "Endereço não encontrado no servidor (erro 404)."
        else -> "O servidor respondeu com erro ${e.code}."
    }
    is java.net.UnknownHostException -> "Não foi possível encontrar o servidor. Verifique a internet."
    is java.net.SocketTimeoutException -> "O servidor demorou demais para responder."
    is java.net.ConnectException -> "Não foi possível conectar ao servidor."
    is javax.net.ssl.SSLException -> "Falha de segurança (SSL) ao conectar."
    else -> "Falha de conexão: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
}

fun JSONObject.str(key: String): String {
    val v = opt(key)
    return if (v == null || v == JSONObject.NULL) "" else v.toString()
}

class XtreamClient(baseUrl: String, val user: String, val pass: String) {
    val base: String = baseUrl.trim().trimEnd('/')

    // user-agent que funcionou no login; também é usado pelo player.
    var ua: String = USER_AGENTS[0]

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // Faz a requisição seguindo redirecionamentos (inclusive http -> https).
    private fun fetch(url: String, agent: String): String {
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 90_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", agent)
            c.setRequestProperty("Accept", "*/*")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw HttpError(code)
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw HttpError(code)
                return c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Redirecionamentos demais")
    }

    fun get(action: String?): String {
        var u = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        if (action != null) u += "&action=$action"
        return fetch(u, ua)
    }

    fun authenticate(): UserInfo {
        var last: Exception? = null
        for (agent in listOf(ua) + USER_AGENTS.filter { it != ua }) {
            try {
                ua = agent
                val body = get(null)
                if (!body.trimStart().startsWith("{")) {
                    throw IOException("O servidor não devolveu dados válidos")
                }
                val json = JSONObject(body)
                val u = json.optJSONObject("user_info") ?: throw AuthFailed("Resposta inválida do servidor.")
                val auth = u.str("auth")
                if (auth != "1" && auth != "true") throw AuthFailed("Usuário ou senha inválidos.")
                val status = u.str("status")
                if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
                    throw AuthFailed("Conta $status. Fale com o suporte.")
                }
                return UserInfo(
                    username = u.str("username").ifBlank { user },
                    status = status.ifBlank { "Ativa" },
                    expDate = u.str("exp_date").toLongOrNull(),
                    activeCons = u.str("active_cons").ifBlank { "0" },
                    maxConnections = u.str("max_connections").ifBlank { "-" },
                )
            } catch (e: AuthFailed) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Falha desconhecida")
    }

    fun liveUrl(id: String, fmt: String): String =
        "$base/live/${enc(user)}/${enc(pass)}/$id." + if (fmt == "HLS") "m3u8" else "ts"

    fun movieUrl(id: String, ext: String): String =
        "$base/movie/${enc(user)}/${enc(pass)}/$id.$ext"

    fun seriesUrl(id: String, ext: String): String =
        "$base/series/${enc(user)}/${enc(pass)}/$id.$ext"
}

private fun arr(s: String): JSONArray = try {
    JSONArray(s)
} catch (e: Exception) {
    JSONArray()
}

fun parseCats(s: String): List<Category> {
    val a = arr(s)
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Category(o.str("category_id"), o.str("category_name"))
    }
}

fun parseChannels(s: String): List<Channel> {
    val a = arr(s)
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Channel(
            id = o.str("stream_id"),
            name = o.str("name"),
            icon = o.str("stream_icon").ifBlank { null },
            categoryId = o.str("category_id"),
        )
    }
}

fun parseMovies(s: String): List<Movie> {
    val a = arr(s)
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Movie(
            id = o.str("stream_id"),
            name = o.str("name"),
            icon = o.str("stream_icon").ifBlank { null },
            categoryId = o.str("category_id"),
            ext = o.str("container_extension").ifBlank { "mkv" },
        )
    }
}

fun parseSeries(s: String): List<Series> {
    val a = arr(s)
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Series(
            id = o.str("series_id"),
            name = o.str("name"),
            cover = o.str("cover").ifBlank { null },
            categoryId = o.str("category_id"),
        )
    }
}

fun parseEpisodes(s: String): List<Episode> {
    val root = try {
        JSONObject(s)
    } catch (e: Exception) {
        return emptyList()
    }
    val out = mutableListOf<Episode>()

    fun addAll(seasonHint: Int, list: JSONArray) {
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            out.add(
                Episode(
                    id = o.str("id"),
                    title = o.str("title").ifBlank { "Episódio ${i + 1}" },
                    season = o.str("season").toIntOrNull() ?: seasonHint,
                    number = o.str("episode_num").toIntOrNull() ?: (i + 1),
                    ext = o.str("container_extension").ifBlank { "mkv" },
                    plot = o.optJSONObject("info")?.str("plot").orEmpty(),
                ),
            )
        }
    }

    val eps = root.opt("episodes")
    if (eps is JSONObject) {
        val keys = eps.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val l = eps.optJSONArray(k)
            if (l != null) addAll(k.toIntOrNull() ?: 1, l)
        }
    } else if (eps is JSONArray) {
        for (i in 0 until eps.length()) {
            val l = eps.optJSONArray(i)
            if (l != null) addAll(i + 1, l)
        }
    }
    return out.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
}

fun parseMediaInfo(s: String): MediaInfo {
    val parsed: JSONObject? = try {
        JSONObject(s).optJSONObject("info")
    } catch (e: Exception) {
        null
    }
    val o = parsed ?: return MediaInfo()
    val backdrop = o.optJSONArray("backdrop_path")?.optString(0)
        ?.takeIf { it.isNotBlank() && it != "null" }
    val release = o.str("releasedate").ifBlank { o.str("releaseDate") }.ifBlank { o.str("release_date") }
    return MediaInfo(
        plot = o.str("plot").ifBlank { o.str("description") },
        genre = o.str("genre"),
        year = o.str("year").ifBlank { release.take(4) },
        rating = o.str("rating"),
        duration = o.str("duration"),
        cast = o.str("cast").ifBlank { o.str("actors") },
        director = o.str("director"),
        backdrop = backdrop,
    )
}

// Os painéis Xtream costumam enviar título e descrição em base64.
private fun b64(s: String): String {
    val t = s.trim()
    if (t.isEmpty() || t.length % 4 != 0 || !Regex("^[A-Za-z0-9+/]+={0,2}$").matches(t)) return s
    return try {
        val d = String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
        val bad = d.any { it == '\uFFFD' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }
        if (bad) s else d
    } catch (e: Exception) {
        s
    }
}

fun parseEpg(s: String): List<EpgItem> {
    val root = try {
        JSONObject(s)
    } catch (e: Exception) {
        return emptyList()
    }
    val a = root.optJSONArray("epg_listings") ?: return emptyList()
    val out = ArrayList<EpgItem>()
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        val st = o.str("start_timestamp").toLongOrNull() ?: continue
        val en = o.str("stop_timestamp").toLongOrNull() ?: continue
        out.add(EpgItem(b64(o.str("title")), b64(o.str("description")), st, en))
    }
    return out.sortedBy { it.start }
}

fun currentProgram(list: List<EpgItem>): EpgItem? {
    val now = System.currentTimeMillis() / 1000
    return list.firstOrNull { it.start <= now && now < it.end }
}

fun nextProgram(list: List<EpgItem>): EpgItem? {
    val now = System.currentTimeMillis() / 1000
    return list.firstOrNull { it.start > now }
}

fun fmtTime(epochSec: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSec * 1000))

private val ADULT_WORDS = listOf("adult", "adulto", "xxx", "porn", "18+", "+18", "hentai", "erotic", "erótic")

fun isAdult(name: String): Boolean {
    val n = name.lowercase()
    return ADULT_WORDS.any { it in n }
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("kodz", Context.MODE_PRIVATE)

    var route: Int
        get() = p.getInt("route", 0)
        set(v) { p.edit().putInt("route", v).apply() }

    var user: String
        get() = p.getString("user", "") ?: ""
        set(v) { p.edit().putString("user", v).apply() }

    var pass: String
        get() = p.getString("pass", "") ?: ""
        set(v) { p.edit().putString("pass", v).apply() }

    var format: String
        get() = p.getString("format", "HLS") ?: "HLS"
        set(v) { p.edit().putString("format", v).apply() }

    // Vazio = código parental desativado.
    var pin: String
        get() = p.getString("pin", "") ?: ""
        set(v) { p.edit().putString("pin", v).apply() }

    fun clearLogin() {
        p.edit().remove("user").remove("pass").apply()
    }
}
