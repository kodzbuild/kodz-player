@file:OptIn(UnstableApi::class)

package com.kodz.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import com.kodz.player.AppViewModel
import com.kodz.player.PlayItem
import com.kodz.player.data.XtreamClient
import com.kodz.player.data.currentProgram
import com.kodz.player.data.fmtTime
import com.kodz.player.data.nextProgram

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Cria o ExoPlayer.
 *  - HLS: usa .m3u8
 *  - TS: usa .ts e deixa o ExoPlayer detectar o formato
 *  - MPEGTS: usa .ts forçando MPEG-TS com detecção reforçada de quadros
 *  - VOD (filmes): usa a extensão original (mkv, mp4...); o ExoPlayer lê MKV nativamente
 */
fun buildPlayer(ctx: Context, client: XtreamClient, item: PlayItem, fmt: String): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(client.ua)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    val extractors = DefaultExtractorsFactory().apply {
        if (fmt == "MPEGTS") {
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            )
        }
    }

    // Buffer maior para evitar travamentos.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            if (item.live) 20_000 else 30_000, // mínimo mantido no buffer
            if (item.live) 50_000 else 60_000, // máximo
            3_000, // buffer para iniciar
            6_000, // buffer para retomar após travar
        )
        .setPrioritizeTimeOverSizeThresholds(item.live)
        .build()

    val url = when {
        item.live -> client.liveUrl(item.id, fmt)
        item.series -> client.seriesUrl(item.id, item.ext)
        else -> client.movieUrl(item.id, item.ext)
    }
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .apply {
            if (item.live && fmt == "HLS") setMimeType(MimeTypes.APPLICATION_M3U8)
            if (item.live && fmt == "MPEGTS") setMimeType(MimeTypes.VIDEO_MP2T)
        }
        .build()

    return ExoPlayer.Builder(ctx)
        .setRenderersFactory(DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http), extractors))
        .setLoadControl(loadControl)
        .build()
        .also { it.setMediaItem(mediaItem) }
}

@Composable
fun PlayerScreen(vm: AppViewModel, item: PlayItem) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val client = vm.client ?: return

    // Ao vivo: começa pelo formato das configurações e, se falhar, tenta os outros.
    val formats = remember(item) {
        if (item.live) listOf(vm.format) + listOf("HLS", "TS", "MPEGTS").filter { it != vm.format }
        else listOf("VOD")
    }
    var idx by remember(item) { mutableIntStateOf(0) }
    var failed by remember(item) { mutableStateOf(false) }
    var detail by remember(item) { mutableStateOf("") }
    var controls by remember { mutableStateOf(true) }
    val fmt = formats[idx]

    val player = remember(item, fmt) { buildPlayer(ctx, client, item, fmt) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                detail = error.errorCodeName + when (cause) {
                    is HttpDataSource.InvalidResponseCodeException -> " (HTTP ${cause.responseCode})"
                    else -> ""
                }
                if (idx < formats.lastIndex) idx++ else failed = true
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pausa ao sair do app e retoma ao voltar.
    DisposableEffect(player) {
        val owner = ctx.findActivity() as? LifecycleOwner
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) player.pause()
            else if (e == Lifecycle.Event.ON_RESUME) player.play()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // Tela cheia em paisagem enquanto o player está aberto.
    DisposableEffect(Unit) {
        val act = ctx.findActivity()
        val previous = act?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = act?.window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            act?.requestedOrientation = previous
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Programação do canal (agora / depois)
    LaunchedEffect(item.id) { if (item.live) vm.loadEpg(item.id) }
    val epg = vm.epgCache[item.id].orEmpty()
    val nowItem = currentProgram(epg)
    val nextItem = nextProgram(epg)
    var showGuide by remember(item) { mutableStateOf(false) }

    BackHandler { vm.playing = null }
    BackHandler(enabled = showGuide) { showGuide = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    isFocusable = true
                    controllerShowTimeoutMs = 4000
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controls = v == View.VISIBLE },
                    )
                    post { requestFocus() }
                }
            },
            update = { it.player = player },
        )

        if (controls || failed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { vm.playing = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.live && nowItem != null) {
                        Text(
                            "Agora: ${nowItem.title}" + if (nextItem != null) "  ·  Depois: ${nextItem.title}" else "",
                            fontSize = 12.sp,
                            color = Color(0xFFCCCCDD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (item.live && epg.isNotEmpty()) {
                    Pill("Programação", selected = showGuide) { showGuide = !showGuide }
                    Spacer(Modifier.width(8.dp))
                }
                if (item.live) Text(fmt, fontSize = 12.sp, color = KCyan, modifier = Modifier.padding(end = 8.dp))
            }
        }

        if (showGuide) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Color(0xE60A0A0F))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Programação", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                epg.forEach { p ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${fmtTime(p.start)} – ${fmtTime(p.end)}", fontSize = 12.sp, color = KCyan)
                        Text(p.title, fontWeight = FontWeight.SemiBold)
                        if (p.desc.isNotBlank()) {
                            Text(p.desc, fontSize = 12.sp, color = Color.Gray, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Não foi possível reproduzir este conteúdo.",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Verifique a conexão ou tente outra Rota ao entrar.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Detalhe: $detail", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))
                GradientButton("Voltar") { vm.playing = null }
            }
        }
    }
}
