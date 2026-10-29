package com.kodz.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodz.player.AppViewModel
import com.kodz.player.PlayItem
import com.kodz.player.data.Catalog

@Composable
fun SearchTab(vm: AppViewModel) {
    CatalogGate(vm) { cat -> SearchContent(vm, cat) }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun SearchContent(vm: AppViewModel, cat: Catalog) {
    val q = vm.query.trim()

    // Categorias bloqueadas pelo código parental não aparecem na busca.
    val hiddenLive = remember(cat, vm.pin, vm.unlocked.size) {
        cat.liveCategories.filter { vm.isLocked(it) }.map { it.id }.toSet()
    }
    val hiddenMovies = remember(cat, vm.pin, vm.unlocked.size) {
        cat.movieCategories.filter { vm.isLocked(it) }.map { it.id }.toSet()
    }
    val hiddenSeries = remember(cat, vm.pin, vm.unlocked.size) {
        cat.seriesCategories.filter { vm.isLocked(it) }.map { it.id }.toSet()
    }

    val channels = remember(q, cat, hiddenLive) {
        if (q.length < 2) emptyList()
        else cat.channels.filter { it.name.contains(q, ignoreCase = true) && it.categoryId !in hiddenLive }.take(40)
    }
    val movies = remember(q, cat, hiddenMovies) {
        if (q.length < 2) emptyList()
        else cat.movies.filter { it.name.contains(q, ignoreCase = true) && it.categoryId !in hiddenMovies }.take(40)
    }
    val series = remember(q, cat, hiddenSeries) {
        if (q.length < 2) emptyList()
        else cat.series.filter { it.name.contains(q, ignoreCase = true) && it.categoryId !in hiddenSeries }.take(40)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                singleLine = true,
                label = { Text("Buscar canais, filmes e séries") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        if (q.length < 2) {
            item { Text("Digite pelo menos 2 letras.", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp)) }
        } else if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            item { Text("Nada encontrado para \"$q\".", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        if (channels.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Canais")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(channels) { ch ->
                            Box(Modifier.width(140.dp)) {
                                ChannelCard(vm, ch) { vm.playing = PlayItem(ch.name, ch.id, live = true) }
                            }
                        }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Filmes")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(movies) { m -> Poster(m) { vm.openMovie(m) } }
                    }
                }
            }
        }
        if (series.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Séries")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(series) { s -> PosterCard(s.name, s.cover) { vm.openSeries(s) } }
                    }
                }
            }
        }
    }
}
