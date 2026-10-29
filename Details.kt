package com.kodz.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kodz.player.AppViewModel
import com.kodz.player.PlayItem
import com.kodz.player.data.Episode
import com.kodz.player.data.MediaInfo
import com.kodz.player.data.Movie
import com.kodz.player.data.Series

private val DetailPlaceholder = ColorPainter(KChip)

@Composable
fun DetailHeader(title: String, poster: String?, backdrop: String?, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp),
    ) {
        AsyncImage(
            model = backdrop ?: poster,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x990A0A0F), KBg))),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                error = DetailPlaceholder,
                fallback = DetailPlaceholder,
            )
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun InfoChips(info: MediaInfo) {
    val items = buildList {
        if (info.year.isNotBlank()) add(info.year)
        if (info.duration.isNotBlank()) add(info.duration)
        val r = info.rating.toDoubleOrNull()
        if (r != null && r > 0) add("★ ${info.rating}")
        if (info.genre.isNotBlank()) add(info.genre)
    }
    if (items.isNotEmpty()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { t ->
                Text(
                    t,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(KChip)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp)
    }
}

@Composable
fun Synopsis(plot: String, loading: Boolean) {
    when {
        plot.isNotBlank() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sinopse", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(plot, fontSize = 14.sp, color = Color(0xFFD5D5E2))
        }
        loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), color = KCyan, strokeWidth = 2.dp)
            Text("Carregando sinopse…", color = Color.Gray)
        }
        else -> Text("Sinopse não disponível para este título.", color = Color.Gray)
    }
}

// ---------------------------------------------------------------- filme

@Composable
fun MovieDetail(vm: AppViewModel, m: Movie) {
    BackHandler { vm.closeMovie() }
    val info = vm.infoCache[m.id]

    Column(
        Modifier
            .fillMaxSize()
            .background(KBg)
            .verticalScroll(rememberScrollState()),
    ) {
        DetailHeader(m.name, m.icon, info?.backdrop) { vm.closeMovie() }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (info != null) InfoChips(info)
            GradientButton(
                "▶  Assistir",
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
            ) {
                vm.playing = PlayItem(m.name, m.id, live = false, ext = m.ext)
            }
            Synopsis(info?.plot.orEmpty(), loading = info == null)
            if (info != null && info.director.isNotBlank()) LabelValue("Direção", info.director)
            if (info != null && info.cast.isNotBlank()) LabelValue("Elenco", info.cast)
        }
    }
}

// ---------------------------------------------------------------- série

@Composable
fun SeriesDetail(vm: AppViewModel, s: Series) {
    BackHandler { vm.closeSeries() }
    val info = vm.seriesInfo
    val seasons = remember(vm.episodes) { vm.episodes.map { it.season }.distinct() }
    var season by remember(s.id) { mutableIntStateOf(-1) }
    val current = if (season in seasons) season else seasons.firstOrNull()
    val list = vm.episodes.filter { it.season == current }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(KBg),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { DetailHeader(s.name, s.cover, info?.backdrop) { vm.closeSeries() } }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (info != null) InfoChips(info)
                Synopsis(info?.plot.orEmpty(), loading = vm.episodesLoading)
                if (info != null && info.cast.isNotBlank()) LabelValue("Elenco", info.cast)
            }
        }
        when {
            vm.episodesLoading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KCyan)
                }
            }
            vm.episodesError != null -> item {
                Text(
                    vm.episodesError ?: "",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
            else -> {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { n ->
                            Pill("Temporada $n", selected = n == current) { season = n }
                        }
                    }
                }
                items(list) { ep ->
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        EpisodeRow(ep) {
                            vm.playing = PlayItem(
                                title = "${s.name} – T${ep.season}E${ep.number}",
                                id = ep.id,
                                live = false,
                                ext = ep.ext,
                                series = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocus(12.dp)
            .clip(shape)
            .background(KCard)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${ep.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KCyan)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ep.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ep.plot.isNotBlank()) {
                Text(ep.plot, fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
