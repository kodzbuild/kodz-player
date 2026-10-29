package com.kodz.player
 
import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodz.player.data.AuthFailed
import com.kodz.player.data.Catalog
import com.kodz.player.data.Category
import com.kodz.player.data.Episode
import com.kodz.player.data.ROUTES
import com.kodz.player.data.Series
import com.kodz.player.data.Store
import com.kodz.player.data.UserInfo
import com.kodz.player.data.XtreamClient
import com.kodz.player.data.describeError
import com.kodz.player.data.isAdult
import com.kodz.player.data.parseCats
import com.kodz.player.data.parseEpisodes
import com.kodz.player.data.parseChannels
import com.kodz.player.data.parseMovies
import com.kodz.player.data.parseSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
 
enum class Phase { Splash, Login, Main }
 
enum class AppTab(val label: String, val icon: ImageVector) {
    Canais("Canais", Icons.Default.Tv),
    Filmes("Filmes", Icons.Default.Movie),
    Series("Séries", Icons.Default.VideoLibrary),
    Perfil("Perfil", Icons.Default.Person),
    Config("Configurações", Icons.Default.Settings),
}
 
data class PlayItem(
    val title: String,
    val id: String,
    val live: Boolean,
    val ext: String = "mkv",
    val series: Boolean = false,
)
 
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
 
    var phase by mutableStateOf(Phase.Splash)
    var busy by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var userInfo by mutableStateOf<UserInfo?>(null)
 
    var catalog by mutableStateOf<Catalog?>(null)
    var loadingCatalog by mutableStateOf(false)
    var catalogError by mutableStateOf<String?>(null)
 
    var tab by mutableStateOf(AppTab.Canais)
    var liveCat by mutableStateOf<String?>(null)
    var playing by mutableStateOf<PlayItem?>(null)
    var seriesSel by mutableStateOf<Series?>(null)
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var episodesLoading by mutableStateOf(false)
    var episodesError by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
 
    var format by mutableStateOf(store.format)
    var pin by mutableStateOf(store.pin)
    val unlocked = mutableStateListOf<String>()
 
    var client: XtreamClient? = null
 
    init {
        if (store.user.isNotEmpty() && store.pass.isNotEmpty()) {
            login(store.route, store.user, store.pass)
        } else {
            phase = Phase.Login
        }
    }
 
    fun login(routeIdx: Int, user: String, pass: String) {
        viewModelScope.launch {
            busy = true
            loginError = null
            try {
                val idx = routeIdx.coerceIn(0, ROUTES.lastIndex)
                val u = user.trim()
                val p = pass.trim()
                val c = XtreamClient(ROUTES[idx].baseUrl, u, p)
                val info = withContext(Dispatchers.IO) { c.authenticate() }
                if (store.user != u || store.route != idx) clearCache()
                store.route = idx
                store.user = u
                store.pass = p
                client = c
                userInfo = info
                phase = Phase.Main
                loadCatalog(false)
            } catch (e: AuthFailed) {
                loginError = e.message
                phase = Phase.Login
            } catch (e: Exception) {
                loginError = describeError(e)
                phase = Phase.Login
            } finally {
                busy = false
            }
        }
    }
 
    fun logout() {
        store.clearLogin()
        clearCache()
        client = null
        userInfo = null
        catalog = null
        playing = null
        seriesSel = null
        liveCat = null
        unlocked.clear()
        tab = AppTab.Canais
        loginError = null
        phase = Phase.Login
    }
 
    private fun clearCache() {
        getApplication<Application>().cacheDir
            .listFiles { f -> f.name.startsWith("kodz_") }
            ?.forEach { it.delete() }
    }
 
    fun loadCatalog(force: Boolean) {
        val c = client ?: return
        viewModelScope.launch {
            loadingCatalog = true
            catalogError = null
            try {
                val dir = getApplication<Application>().cacheDir
                val result = withContext(Dispatchers.IO) {
                    fun fetch(action: String): String {
                        val f = File(dir, "kodz_$action.json")
                        if (!force && f.exists() && f.length() > 2) return f.readText()
                        val body = c.get(action)
                        f.writeText(body)
                        return body
                    }
                    // Séries são opcionais: se o servidor não tiver, segue sem elas.
                    fun fetchOpt(action: String): String = try {
                        fetch(action)
                    } catch (e: Exception) {
                        "[]"
                    }
                    val lc = async { fetch("get_live_categories") }
                    val ls = async { fetch("get_live_streams") }
                    val mc = async { fetch("get_vod_categories") }
                    val ms = async { fetch("get_vod_streams") }
                    val sc = async { fetchOpt("get_series_categories") }
                    val ss = async { fetchOpt("get_series") }
                    Catalog(
                        parseCats(lc.await()),
                        parseChannels(ls.await()),
                        parseCats(mc.await()),
                        parseMovies(ms.await()),
                        parseCats(sc.await()),
                        parseSeries(ss.await()),
                    )
                }
                catalog = result
                if (force) message = "Conteúdo atualizado!"
            } catch (e: Exception) {
                if (catalog == null) {
                    catalogError = "Não foi possível carregar o conteúdo. ${describeError(e)}"
                } else {
                    message = "Falha ao atualizar o conteúdo."
                }
            } finally {
                loadingCatalog = false
            }
        }
    }
 
    // Reinicia a playlist: renova os dados da conta e baixa tudo de novo.
    fun refresh() {
        val c = client ?: return
        viewModelScope.launch {
            try {
                userInfo = withContext(Dispatchers.IO) { c.authenticate() }
            } catch (e: Exception) {
                // segue para o catálogo; o erro de rede aparece lá
            }
            loadCatalog(true)
        }
    }
 
    fun openSeries(s: Series) {
        val c = client ?: return
        seriesSel = s
        episodes = emptyList()
        episodesError = null
        episodesLoading = true
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { c.get("get_series_info&series_id=${s.id}") }
                val list = withContext(Dispatchers.Default) { parseEpisodes(body) }
                if (seriesSel?.id == s.id) {
                    episodes = list
                    if (list.isEmpty()) episodesError = "Nenhum episódio encontrado."
                }
            } catch (e: Exception) {
                episodesError = describeError(e)
            } finally {
                episodesLoading = false
            }
        }
    }
 
    fun closeSeries() {
        seriesSel = null
    }
 
    fun changeFormat(f: String) {
        store.format = f
        format = f
    }
 
    fun changePin(v: String) {
        store.pin = v
        pin = v
        unlocked.clear()
    }
 
    fun isLocked(c: Category): Boolean = pin.isNotEmpty() && isAdult(c.name) && c.id !in unlocked
}
 
