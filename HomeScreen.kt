package com.kodz.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kodz.player.AppTab
import com.kodz.player.AppViewModel
import com.kodz.player.data.Catalog
import com.kodz.player.data.Game
import com.kodz.player.data.GameState
import com.kodz.player.data.Movie
import com.kodz.player.data.fmtTime
import com.kodz.player.data.isAdult
import kotlinx.coroutines.delay

private val HomePlaceholder = ColorPainter(KChip)

@Composable
fun HomeTab(vm: AppViewModel) {
    CatalogGate(vm) { cat -> HomeContent(vm, cat) }
}

private fun pickFeatured(cat: Catalog): List<Movie> {
    val out = ArrayList<Movie>()
    for (c in cat.movieCategories) {
        if (isAdult(c.name)) continue
        val m = cat.moviesByCat[c.id]?.firstOrNull { !it.icon.isNullOrBlank() } ?: continue
        out.add(m)
        if (out.size == 4) break
    }
    return out
}

@Composable
fun HomeContent(vm: AppViewModel, cat: Catalog) {
    val featured = remember(cat) { pickFeatured(cat) }

    // Atualiza os jogos ao abrir e a cada 5 minutos enquanto a tela estiver aberta.
    LaunchedEffect(Unit) {
        while (true) {
            vm.loadGames(false)
            delay(300_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (featured.isNotEmpty()) {
            item { HeroCarousel(vm, featured) }
        }
        item { MainTiles(vm) }
        item { GamesSection(vm) }
    }
}

// ---------------------------------------------------------------- destaque

@Composable
fun HeroCarousel(vm: AppViewModel, list: List<Movie>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(list) {
        while (list.size > 1) {
            delay(7_000)
            index = (index + 1) % list.size
        }
    }
    val m = list[index.coerceIn(0, list.lastIndex)]
    LaunchedEffect(m.id) { vm.loadMovieInfo(m) }
    val info = vm.infoCache[m.id]
    val shape = RoundedCornerShape(20.dp)

    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(230.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1238), Color(0xFF0D1B3D)))),
    ) {
        AsyncImage(
            model = info?.backdrop ?: m.icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color(0xF00A0A0F), Color.Transparent))),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.64f)
                .padding(start = 20.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Filme em destaque", fontSize = 12.sp, color = KCyan, fontWeight = FontWeight.Bold)
            Text(m.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val plot = info?.plot.orEmpty()
            if (plot.isNotBlank()) {
                Text(plot, fontSize = 13.sp, color = Color(0xFFCCCCDD), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            GradientButton("▶  Ver filme") { vm.openMovie(m) }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            list.indices.forEach { i ->
                Box(
                    Modifier
                        .size(if (i == index) 9.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == index) Color.White else Color(0x88FFFFFF)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- atalhos

@Composable
fun MainTiles(vm: AppViewModel) {
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tile(
            "Canais", "Ao vivo e programação", Icons.Default.Tv,
            listOf(Color(0xFF0E1E45), Color(0xFF0B1330)), Modifier.weight(1f),
        ) { vm.tab = AppTab.Canais }
        Tile(
            "Filmes", "Catálogo completo", Icons.Default.Movie,
            listOf(Color(0xFF3A1A78), Color(0xFF1F1040)), Modifier.weight(1f),
        ) { vm.tab = AppTab.Filmes }
        Tile(
            "Séries", "Todas as temporadas", Icons.Default.VideoLibrary,
            listOf(Color(0xFF0F3A55), Color(0xFF0A2236)), Modifier.weight(1f),
        ) { vm.tab = AppTab.Seriados }
    }
}

@Composable
fun Tile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .tvFocus(16.dp)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(subtitle, fontSize = 11.sp, color = Color(0xFFBBBBCC), textAlign = TextAlign.Center, maxLines = 2)
    }
}

// ---------------------------------------------------------------- jogos do dia

@Composable
fun GamesSection(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Jogos de hoje", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val liveCount = vm.games.count { it.state == GameState.Live }
            if (liveCount > 0) {
                Text("$liveCount ao vivo", fontSize = 12.sp, color = Color(0xFFFF5C73), modifier = Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f))
            }
            Pill("Ver todos", selected = false) { vm.tab = AppTab.Jogos }
        }
        when {
            vm.games.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.games.take(12)) { g ->
                    GameCard(vm, g, Modifier.width(300.dp)) { vm.tab = AppTab.Jogos }
                }
            }
            vm.gamesLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KCyan)
            }
            else -> Text(
                vm.gamesError ?: "Nenhum jogo encontrado para hoje.",
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
fun GameCard(vm: AppViewModel, g: Game, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val hasScore = g.homeScore != null && g.awayScore != null
    val center = when {
        g.state == GameState.Scheduled -> fmtTime(g.startMillis / 1000)
        hasScore -> "${g.homeScore} x ${g.awayScore}"
        else -> "vs"
    }
    Column(
        modifier
            .tvFocus(16.dp)
            .clip(shape)
            .background(KCard)
            .border(1.dp, Color(0x332F7BFF), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                g.league,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFBBBBCC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StateBadge(g)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamColumn(g.home, g.homeBadge, Modifier.weight(1f))
            Text(center, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            TeamColumn(g.away, g.awayBadge, Modifier.weight(1f))
        }
        if (g.channels.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                g.channels.take(5).forEach { name ->
                    ChannelChip(name) {
                        vm.query = name
                        vm.tab = AppTab.Busca
                    }
                }
            }
        }
    }
}

@Composable
fun StateBadge(g: Game) {
    val (label, color) = when (g.state) {
        GameState.Live -> "Ao vivo" to Color(0xFFE5334B)
        GameState.Started -> "Em andamento" to Color(0xFFB7791F)
        GameState.Scheduled -> "Hoje" to Color(0xFF2F4B8F)
        GameState.Finished -> "Encerrado" to Color(0xFF444455)
        GameState.Off -> "Adiado" to Color(0xFF444455)
    }
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun TeamColumn(name: String, badge: String?, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AsyncImage(
            model = badge?.let { "$it/small" },
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            contentScale = ContentScale.Fit,
            error = HomePlaceholder,
            fallback = HomePlaceholder,
        )
        Text(
            name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ChannelChip(name: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        name,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .tvFocus(8.dp)
            .clip(shape)
            .background(KChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun GamesTab(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadGames(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Jogos de hoje", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Atualiza sozinho durante o dia. Toque num canal para procurá-lo na sua lista. Dados: TheSportsDB.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }
            Pill("Atualizar", selected = false) { vm.loadGames(true) }
        }
        when {
            vm.games.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.games) { g ->
                    GameCard(vm, g) {
                        val first = g.channels.firstOrNull()
                        if (first != null) {
                            vm.query = first
                            vm.tab = AppTab.Busca
                        }
                    }
                }
            }
            vm.gamesLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KCyan)
            }
            else -> Text(
                vm.gamesError ?: "Nenhum jogo encontrado para hoje.",
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
