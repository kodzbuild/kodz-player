package com.kodz.player.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kodz.player.AppTab
import com.kodz.player.AppViewModel
import com.kodz.player.PlayItem
import com.kodz.player.Phase
import com.kodz.player.R
import com.kodz.player.data.Catalog
import com.kodz.player.data.Category
import com.kodz.player.data.Channel
import com.kodz.player.data.Movie
import com.kodz.player.data.ROUTES
import com.kodz.player.data.SUPPORT_PHONE
import com.kodz.player.data.isAdult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Placeholder = ColorPainter(KChip)

// ---------------------------------------------------------------- raiz

@Composable
fun App(vm: AppViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.message = null
        }
    }
    Surface(Modifier.fillMaxSize(), color = KBg, contentColor = Color.White) {
        val playing = vm.playing
        if (playing != null) {
            PlayerScreen(vm, playing)
        } else {
            when (vm.phase) {
                Phase.Splash -> Splash()
                Phase.Login -> LoginScreen(vm)
                Phase.Main -> MainScreen(vm)
            }
        }
    }
}

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(160.dp))
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = KCyan)
        }
    }
}

// ---------------------------------------------------------------- login

@Composable
fun LoginScreen(vm: AppViewModel) {
    var route by remember { mutableIntStateOf(vm.store.route.coerceIn(0, ROUTES.lastIndex)) }
    var user by remember { mutableStateOf(vm.store.user) }
    var pass by remember { mutableStateOf(vm.store.pass) }

    fun go() {
        if (user.isNotBlank() && pass.isNotBlank() && !vm.busy) vm.login(route, user, pass)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1238), Color.Black))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(130.dp))
            Text("Kodz Player", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ROUTES.forEachIndexed { i, r -> Pill(r.label, selected = route == i) { route = i } }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuário") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { go() }),
            )
            vm.loginError?.let {
                Text(it, color = KError, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(18.dp))
            GradientButton(
                "Entrar",
                Modifier.fillMaxWidth(),
                enabled = user.isNotBlank() && pass.isNotBlank(),
                loading = vm.busy,
            ) { go() }
        }
    }
}

// ---------------------------------------------------------------- principal

@Composable
fun MainScreen(vm: AppViewModel) {
    BackHandler(enabled = vm.tab != AppTab.Canais) { vm.tab = AppTab.Canais }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.tab) {
                    AppTab.Canais -> ChannelsTab(vm)
                    AppTab.Filmes -> MoviesTab(vm)
                    AppTab.Perfil -> ProfileTab(vm)
                    AppTab.Config -> SettingsTab(vm)
                }
            }
        }
        if (vm.loadingCatalog) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KCyan)
                    Spacer(Modifier.height(12.dp))
                    Text("Carregando conteúdo…")
                }
            }
        }
    }
}

@Composable
fun TopBar(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D14))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(40.dp))
        AppTab.values().forEach { t ->
            Pill(t.label, selected = vm.tab == t, icon = t.icon) { vm.tab = t }
        }
    }
}

@Composable
fun CatalogGate(vm: AppViewModel, content: @Composable (Catalog) -> Unit) {
    val c = vm.catalog
    if (c != null) {
        content(c)
    } else if (!vm.loadingCatalog) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally,
        ) {
            Text(vm.catalogError ?: "Nenhum conteúdo carregado.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            GradientButton("Tentar novamente") { vm.loadCatalog(true) }
        }
    }
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> String?) {
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> if (v.length <= 4 && v.all { c -> c.isDigit() }) code = v },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                err?.let { Text(it, color = KError, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code.length != 4) {
                    err = "Digite 4 dígitos."
                } else {
                    val e = onOk(code)
                    if (e == null) onDismiss() else err = e
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------- canais

@Composable
fun ChannelsTab(vm: AppViewModel) {
    CatalogGate(vm) { cat -> ChannelsContent(vm, cat) }
}

@Composable
fun ChannelsContent(vm: AppViewModel, cat: Catalog) {
    val cats = remember(cat) { cat.liveCategories.filter { !cat.channelsByCat[it.id].isNullOrEmpty() } }
    val selected = cats.firstOrNull { it.id == vm.liveCat } ?: cats.firstOrNull()
    var ask by remember { mutableStateOf<Category?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cats) { c ->
                val locked = vm.isLocked(c)
                Pill(c.name, selected = c.id == selected?.id, icon = if (locked) Icons.Default.Lock else null) {
                    if (locked) {
                        ask = c
                    } else {
                        vm.liveCat = c.id
                    }
                }
            }
        }
        val locked = selected != null && vm.isLocked(selected)
        val list: List<Channel> = if (selected == null || locked) emptyList() else cat.channelsByCat[selected.id].orEmpty()
        if (locked) {
            Text(
                "Categoria bloqueada. Toque nela para digitar o código.",
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list) { ch ->
                ChannelCard(ch) { vm.playing = PlayItem(ch.name, ch.id, live = true) }
            }
        }
    }

    ask?.let { c ->
        PinDialog("Código parental", onDismiss = { ask = null }) { code ->
            if (code == vm.pin) {
                vm.unlocked.add(c.id)
                vm.liveCat = c.id
                null
            } else {
                "Código incorreto."
            }
        }
    }
}

@Composable
fun ChannelCard(ch: Channel, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .tvFocus(12.dp)
            .clip(shape)
            .background(KCard)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = ch.icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            contentScale = ContentScale.Fit,
            error = Placeholder,
            fallback = Placeholder,
        )
        Spacer(Modifier.height(6.dp))
        Text(ch.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- filmes

@Composable
fun MoviesTab(vm: AppViewModel) {
    CatalogGate(vm) { cat -> MoviesContent(vm, cat) }
}

@Composable
fun MoviesContent(vm: AppViewModel, cat: Catalog) {
    val cats = remember(cat) { cat.movieCategories.filter { !cat.moviesByCat[it.id].isNullOrEmpty() } }
    val hero = remember(cat) {
        cats.firstOrNull { !isAdult(it.name) }
            ?.let { c -> cat.moviesByCat[c.id]?.firstOrNull { m -> !m.icon.isNullOrBlank() } }
    }
    var ask by remember { mutableStateOf<Category?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (hero != null) {
            item { Hero(hero) { vm.playing = PlayItem(hero.name, hero.id, live = false, ext = hero.ext) } }
        }
        items(cats) { c ->
            if (vm.isLocked(c)) {
                LockedRow(c.name) { ask = c }
            } else {
                Column {
                    Text(
                        c.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(cat.moviesByCat[c.id].orEmpty()) { m ->
                            Poster(m) { vm.playing = PlayItem(m.name, m.id, live = false, ext = m.ext) }
                        }
                    }
                }
            }
        }
    }

    ask?.let { c ->
        PinDialog("Código parental", onDismiss = { ask = null }) { code ->
            if (code == vm.pin) {
                vm.unlocked.add(c.id)
                null
            } else {
                "Código incorreto."
            }
        }
    }
}

@Composable
fun Hero(m: Movie, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1458), KBg)))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = m.icon,
            contentDescription = null,
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            error = Placeholder,
            fallback = Placeholder,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Em destaque", fontSize = 12.sp, color = KCyan, fontWeight = FontWeight.Bold)
            Text(m.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            GradientButton("▶  Assistir", onClick = onPlay)
        }
    }
}

@Composable
fun Poster(m: Movie, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(128.dp)) {
        AsyncImage(
            model = m.icon,
            contentDescription = m.name,
            modifier = Modifier
                .tvFocus(10.dp)
                .clip(shape)
                .background(KCard)
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            contentScale = ContentScale.Crop,
            error = Placeholder,
            fallback = Placeholder,
        )
        Text(
            m.name,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun LockedRow(name: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .tvFocus(12.dp)
            .clip(shape)
            .background(KCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = KCyan)
        Column {
            Text(name, fontWeight = FontWeight.Bold)
            Text("Bloqueado. Toque para digitar o código.", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------- perfil

private fun fmtDate(sec: Long?): String =
    if (sec == null || sec == 0L) "Ilimitado"
    else SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(sec * 1000))

@Composable
fun ProfileTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val u = vm.userInfo
    val days = u?.expDate?.takeIf { it > 0 }?.let { ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(KBlue, KPurple))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(56.dp))
        }
        Text(u?.username ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRow("Status", u?.status ?: "-")
            InfoRow("Vencimento", fmtDate(u?.expDate))
            if (days != null) InfoRow("Prazo", if (days >= 0) "$days dias restantes" else "Vencido")
            InfoRow("Conexões", "${u?.activeCons ?: "0"} de ${u?.maxConnections ?: "-"}")
            InfoRow("Servidor", ROUTES[vm.store.route.coerceIn(0, ROUTES.lastIndex)].label)
        }

        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .tvFocus(16.dp)
                .clip(shape)
                .background(KCard)
                .clickable {
                    runCatching {
                        val digits = SUPPORT_PHONE.filter { it.isDigit() }
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
                    }
                }
                .padding(16.dp),
        ) {
            Text("Suporte Kodz", fontSize = 13.sp, color = Color.Gray)
            Text(SUPPORT_PHONE, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KCyan)
        }

        GradientButton("Sair da conta", Modifier.widthIn(max = 480.dp).fillMaxWidth()) { vm.logout() }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- configurações

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun SettingsTab(vm: AppViewModel) {
    var dialog by remember { mutableIntStateOf(0) } // 1 = criar código, 2 = desativar

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Section("Formato de fluxo (canais ao vivo)") {
            listOf("HLS", "TS", "MPEGTS").forEach { f ->
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tvFocus(10.dp)
                        .clip(shape)
                        .clickable { vm.setFormat(f) }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.format == f, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(f)
                }
            }
            Text(
                "Se o formato escolhido falhar, o app tenta os outros automaticamente.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }

        Section("Código parental") {
            if (vm.pin.isEmpty()) {
                Text("Bloqueia as categorias adultas com um código de 4 dígitos.", fontSize = 13.sp, color = Color.Gray)
                GradientButton("Ativar código parental") { dialog = 1 }
            } else {
                Text("Ativo. As categorias adultas pedem o código para abrir.", fontSize = 13.sp, color = Color.Gray)
                GradientButton("Desativar código parental") { dialog = 2 }
            }
        }

        Section("Atualizar conteúdos") {
            Text(
                "Reinicia a playlist: limpa o cache e baixa canais e filmes novamente do servidor.",
                fontSize = 13.sp,
                color = Color.Gray,
            )
            GradientButton("Atualizar agora", loading = vm.loadingCatalog) { vm.refresh() }
        }

        Text("Kodz Player 1.0.0", fontSize = 12.sp, color = Color.Gray)
    }

    if (dialog == 1) {
        PinDialog("Crie um código de 4 dígitos", onDismiss = { dialog = 0 }) { code ->
            vm.setPin(code)
            null
        }
    }
    if (dialog == 2) {
        PinDialog("Digite o código atual", onDismiss = { dialog = 0 }) { code ->
            if (code == vm.pin) {
                vm.setPin("")
                null
            } else {
                "Código incorreto."
            }
        }
    }
}
