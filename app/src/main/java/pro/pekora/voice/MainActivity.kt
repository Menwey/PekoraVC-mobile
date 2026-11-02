package pro.pekora.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var relay: UdpRelay? = null
    private var mic: MicCapture? = null
    private var playback: AudioPlayback? = null
    private val codec = OpusCodec()
    private var token: RelayProtocol.TokenResponse? = null
    @Volatile private var micLevel: Float = 0f

    private val logs = mutableStateListOf<String>()

    private fun log(s: String) {
        runOnUiThread {
            logs.add(0, s)
            if (logs.size > 200) logs.removeLast()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF191919.toInt()
        window.navigationBarColor = 0xFF232527.toInt()
        playback = AudioPlayback(::log, onSpeakers = { OverlayBus.speakers = it }, volumeBoost = 2.5f)
        val reqPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val reqOverlay = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) startOverlay()
            else log("overlay: grant 'display over other apps' permission in settings")
        }

        val ticketFromIntent = intent.getStringExtra("ticket")
        val prefs = getSharedPreferences("pekora_voice", MODE_PRIVATE)
        val savedTicket = prefs.getString("last_ticket", "")
        val initialTicket = ticketFromIntent ?: savedTicket ?: ""

        setContent {
            RobloxTheme {
                var serverText by remember { mutableStateOf("pekora.pro") }
                var ticketText by remember { mutableStateOf(initialTicket) }
                var relayText by remember { mutableStateOf("") }
                var muted by remember { mutableStateOf(false) }
                var connected by remember { mutableStateOf(false) }
                var level by remember { mutableFloatStateOf(0f) }
                var speakers by remember { mutableStateOf<List<Long>>(emptyList()) }
                var tokenReady by remember { mutableStateOf(token != null) }
                val cs = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    while (true) {
                        level = micLevel
                        speakers = OverlayBus.speakers
                        delay(120)
                    }
                }

                fun needMic(): Boolean {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        reqPerm.launch(Manifest.permission.RECORD_AUDIO)
                        return false
                    }
                    return true
                }

                fun ensureOverlay() {
                    if (Settings.canDrawOverlays(this@MainActivity)) startOverlay()
                    else reqOverlay.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }

                fun doDisconnect() {
                    relay?.disconnect()
                    mic?.stop()
                    playback?.stop()
                    OverlayBus.connected = false
                    try { stopService(Intent(this@MainActivity, OverlayService::class.java)) } catch (_: Exception) {}
                    connected = false
                    log("disconnected")
                }

                fun getToken() {
                    cs.launch {
                        try {
                            val vc = VoiceClient(serverText.trim())
                            val t = if (relayText.isNotBlank() && ticketText.isNotBlank()) {
                                vc.manual(relayText.trim(), ticketText.trim())
                            } else {
                                vc.fetchToken(ticketText.trim())
                            }
                            token = t
                            tokenReady = true
                            log("token ok: relay=${t.relay}")
                        } catch (e: Exception) {
                            log("token error: ${e.message}")
                        }
                    }
                }

                fun doConnect() {
                    if (!needMic()) return
                    val t = token ?: if (relayText.isNotBlank() && ticketText.isNotBlank()) {
                        VoiceClient(serverText.trim()).manual(relayText.trim(), ticketText.trim()).also { token = it }
                    } else { log("Get Token first"); return }
                    relay = UdpRelay(scope,
                        onAudio = { opus, sender, _ -> playback?.pushAudio(opus, sender) },
                        onLog = ::log).also { it.connect(t) }
                    playback?.start()
                    val r = relay
                    mic = MicCapture(this@MainActivity, onFrame = { pcm ->
                        if (!muted && r != null) {
                            val opus = runCatching { codec.encode(pcm) }.getOrElse {
                                log("opus encode error: ${it.message}"); return@MicCapture
                            }
                            r.sendAudio(opus)
                        }
                    }, onLevel = { l ->
                        micLevel = l
                    }, onLog = ::log).also { it.start() }
                    OverlayBus.room = ""
                    OverlayBus.muted = muted
                    OverlayBus.onToggleMute = { muted = !muted; OverlayBus.muted = muted }
                    OverlayBus.onClose = { doDisconnect() }
                    OverlayBus.connected = true
                    connected = true
                    ensureOverlay()
                }

                var showLog by remember { mutableStateOf(false) }
                val state = when {
                    !connected -> OrbState.Offline
                    muted -> OrbState.Muted
                    else -> OrbState.Live
                }
                fun toggleMute() {
                    muted = !muted; OverlayBus.muted = muted
                    log(if (muted) "mic muted" else "mic live")
                }

                Column(Modifier.fillMaxSize().background(Rbx.Bg)) {
                    Row(
                        Modifier.fillMaxWidth().background(Rbx.Nav).statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).background(Rbx.Blue, Rbx.Radius), contentAlignment = Alignment.Center) {
                            Text("P", color = Rbx.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Pekora Voice", color = Rbx.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        StatusPill(state)
                    }

                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(28.dp))
                        MicOrb(state, level, onClick = { if (connected) toggleMute() })
                        Spacer(Modifier.height(14.dp))
                        Text(
                            when (state) {
                                OrbState.Offline -> "Not connected"
                                OrbState.Muted -> "You are muted"
                                OrbState.Live -> "You are live"
                            },
                            style = MaterialTheme.typography.titleLarge, color = Rbx.Text,
                        )
                        Text(
                            if (connected) "Tap the circle to ${if (muted) "unmute" else "mute"}" else "Fill in the ticket below, then connect",
                            style = MaterialTheme.typography.bodyMedium, color = Rbx.TextMuted,
                        )
                        Spacer(Modifier.height(24.dp))

                        if (connected) {
                            RbxButton("Disconnect", { doDisconnect() }, Modifier.fillMaxWidth().height(52.dp), RbxButtonKind.Danger)
                            Spacer(Modifier.height(8.dp))
                            RbxButton("Show bubble over games", { ensureOverlay() }, Modifier.fillMaxWidth(), RbxButtonKind.Secondary)
                        } else {
                            RbxButton("Connect", { doConnect() }, Modifier.fillMaxWidth().height(52.dp), RbxButtonKind.Play)
                            Spacer(Modifier.height(16.dp))
                            RbxCard {
                                RbxField(serverText, { serverText = it }, "Server", Modifier.fillMaxWidth())
                                RbxField(ticketText, { ticketText = it }, "Ticket", Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (tokenReady) "Token ready" else "No token yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (tokenReady) Rbx.Green else Rbx.TextDim,
                                        modifier = Modifier.weight(1f),
                                    )
                                    RbxButton("Get token", { getToken() }, kind = RbxButtonKind.Secondary)
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Speaking now", style = MaterialTheme.typography.titleMedium, color = Rbx.Text, modifier = Modifier.weight(1f))
                            Text("${speakers.size}", style = MaterialTheme.typography.titleMedium, color = Rbx.TextMuted)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (speakers.isEmpty()) {
                            Text(
                                "Nobody is talking right now.", style = MaterialTheme.typography.bodySmall,
                                color = Rbx.TextDim, modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) { speakers.forEach { Speaker(it) } }
                        }

                        Spacer(Modifier.height(20.dp))
                        RbxCard(title = "How to connect") {
                            listOf(
                                "Open pekora.pro on your phone and press Play in a game with voice.",
                                "Choose this app when asked to open pekora-player://.",
                                "The ticket fills in by itself. Tap Get token, then Connect.",
                            ).forEachIndexed { i, t ->
                                Row {
                                    Text("${i + 1}.", color = Rbx.Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t, style = MaterialTheme.typography.bodySmall, color = Rbx.TextMuted)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (showLog) "Hide log" else "Show log",
                            color = Rbx.BlueHover, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clickable { showLog = !showLog }.padding(8.dp),
                        )
                        if (showLog) {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 220.dp)
                                    .background(Rbx.CardDark, Rbx.Radius).padding(8.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                if (logs.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodySmall, color = Rbx.TextDim)
                                logs.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Rbx.TextMuted) }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Created by 1dev2 | @ruplayer228 on dc",
                            style = MaterialTheme.typography.bodySmall, color = Rbx.TextDim,
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        relay?.disconnect()
        mic?.stop()
        playback?.stop()
        OverlayBus.connected = false
        try { stopService(Intent(this, OverlayService::class.java)) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun startOverlay() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            log("overlay: bubble over all apps")
        } catch (e: Exception) { log("overlay failed: ${e.message}") }
    }
}

enum class OrbState { Offline, Muted, Live }

@androidx.compose.runtime.Composable
private fun StatusPill(state: OrbState) {
    val (txt, col) = when (state) {
        OrbState.Offline -> "Offline" to Rbx.TextDim
        OrbState.Muted -> "Muted" to Rbx.Red
        OrbState.Live -> "Live" to Rbx.Green
    }
    Row(
        Modifier.border(1.dp, col, Rbx.Pill).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(col, Rbx.Pill))
        Spacer(Modifier.width(6.dp))
        Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@androidx.compose.runtime.Composable
private fun MicOrb(state: OrbState, level: Float, onClick: () -> Unit) {
    val col = when (state) {
        OrbState.Offline -> Rbx.Disabled
        OrbState.Muted -> Rbx.Red
        OrbState.Live -> Rbx.Green
    }
    val target = if (state == OrbState.Live) 1f + level.coerceIn(0f, 1f) * 0.35f else 1f
    val scale by animateFloatAsState(target, tween(120), label = "ring")
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(150.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .background(col.copy(alpha = 0.18f), Rbx.Pill),
        )
        Box(
            Modifier.size(150.dp).background(col.copy(alpha = 0.30f), Rbx.Pill),
        )
        Box(
            Modifier.size(116.dp).clip(Rbx.Pill).background(col).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 22.dp, height = 36.dp).background(Rbx.Text, RoundedCornerShape(11.dp)))
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.size(width = 34.dp, height = 14.dp)
                        .border(3.dp, Rbx.Text, RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp)),
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Speaker(id: Long) {
    val palette = listOf(Rbx.Blue, Color(0xFF8E44AD), Color(0xFFE67E22), Color(0xFF16A085), Color(0xFFD63384))
    val c = palette[(id % palette.size).toInt().let { if (it < 0) -it else it }]
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).border(3.dp, Rbx.Green, Rbx.Pill).padding(4.dp).background(c, Rbx.Pill),
            contentAlignment = Alignment.Center,
        ) { Text("${id % 100}", color = Rbx.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        Spacer(Modifier.height(4.dp))
        Text("User $id", color = Rbx.TextMuted, fontSize = 11.sp)
    }
}
