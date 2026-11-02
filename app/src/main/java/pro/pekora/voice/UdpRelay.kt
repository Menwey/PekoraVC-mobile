package pro.pekora.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

class UdpRelay(
    private val scope: CoroutineScope,
    private val onAudio: (opus: ByteArray, sender: Long, seq: Int) -> Unit,
    private val onLog: (String) -> Unit,
    private val onStatus: (String) -> Unit = { _ -> },
) {
    private var job: Job? = null
    @Volatile private var sock: DatagramSocket? = null
    @Volatile private var addr: InetAddress? = null
    @Volatile private var port: Int = 0
    @Volatile private var token: String = ""
    @Volatile private var sessionId: Long = 0
    private val seq = AtomicLong(0)

    fun connect(t: RelayProtocol.TokenResponse) {
        disconnect()
        token = t.token
        sessionId = 0
        seq.set(0)
        job = scope.launch(Dispatchers.IO) { loop(t) }
    }

    private suspend fun loop(t: RelayProtocol.TokenResponse) {
        val (host, p) = RelayProtocol.relayHostPort(t.relay)
        if (host.isEmpty() || p == 0) {
            onLog("cannot resolve relay: " + t.relay)
            onStatus("failed")
            return
        }
        val a = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            onLog("cannot resolve relay $host: " + e.message)
            onStatus("failed")
            return
        }
        val s = try {
            DatagramSocket()
        } catch (e: Exception) {
            onLog("udp setup failed: " + e.message)
            onStatus("failed")
            return
        }
        addr = a
        port = p
        sock = s
        s.soTimeout = 100
        try {
            val hello = RelayProtocol.buildHello(token)
            runCatching { s.send(DatagramPacket(hello, hello.size, a, p)) }
                .onSuccess { onLog("hello " + hello.size + "B -> " + a.hostAddress + ":" + p) }
                .onFailure { onLog("hello failed: " + it.message) }

            val buf = ByteArray(2048)
            var lastHello = System.currentTimeMillis()
            var lastStatus = System.currentTimeMillis()
            var lastHeard = System.currentTimeMillis()
            var connected = false

            while (job?.isActive == true) {
                val now = System.currentTimeMillis()

                if (sessionId == 0L) {
                    if (now - lastHeard > 5000 && !connected) {
                        onLog("connect timeout: no WELCOME from relay")
                        onStatus("failed")
                        return
                    }
                    if (now - lastHello >= RelayProtocol.HELLO_INTERVAL_MS) {
                        lastHello = now
                        val h = RelayProtocol.buildHello(token)
                        runCatching { s.send(DatagramPacket(h, h.size, a, p)) }
                            .onSuccess { onLog("hello " + h.size + "B -> " + a.hostAddress + ":" + p) }
                            .onFailure { onLog("hello failed: " + it.message) }
                    }
                } else {
                    connected = true
                    onStatus("connected")
                    if (now - lastHeard > RelayProtocol.SILENCE_TIMEOUT_MS) {
                        onLog("relay went quiet, reconnecting")
                        sessionId = 0
                        lastHello = now - RelayProtocol.HELLO_INTERVAL_MS
                        onStatus("connecting")
                        continue
                    }
                    if (now - lastStatus >= RelayProtocol.STATUS_INTERVAL_MS) {
                        lastStatus = now
                        val status = RelayProtocol.buildStatus(sessionId, RelayProtocol.StatusMsg())
                        runCatching { s.send(DatagramPacket(status, status.size, a, p)) }
                    }
                }

                val pkt = DatagramPacket(buf, buf.size)
                try {
                    s.receive(pkt)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    if (job?.isActive == true) onLog("udp recv: " + e.message)
                    break
                }
                lastHeard = System.currentTimeMillis()

                when (val m = RelayProtocol.parsePacket(pkt.data, pkt.length)) {
                    is RelayProtocol.Packet.Welcome -> {
                        sessionId = m.sessionId
                        connected = true
                        onLog("connected (user " + m.userId + ") session=" + m.sessionId)
                        onStatus("connected")
                    }
                    is RelayProtocol.Packet.State -> {
                    }
                    is RelayProtocol.Packet.Audio -> {
                        if (m.opus.isNotEmpty()) onAudio(m.opus, m.sender, m.sequence)
                    }
                    is RelayProtocol.Packet.Pos -> {
                    }
                    is RelayProtocol.Packet.Bye -> {
                        onLog("relay closed session: " + m.reason.take(140))
                        if (m.reason.startsWith("no-session")) {
                            sessionId = 0
                            lastHello = System.currentTimeMillis() - RelayProtocol.HELLO_INTERVAL_MS
                            onStatus("connecting")
                        } else {
                            break
                        }
                    }
                    is RelayProtocol.Packet.Unknown ->
                        onLog("unknown " + pkt.length + "B")
                }
            }
        } finally {
            runCatching { s.close() }
            if (sock === s) sock = null
            onLog("relay socket closed")
            onStatus("disconnected")
        }
    }

    fun sendAudio(opus: ByteArray) {
        if (opus.isEmpty() || sessionId == 0L) return
        val s = sock
        val a = addr
        if (s == null || a == null || s.isClosed) return
        val p = RelayProtocol.buildAudio(sessionId, seq.incrementAndGet().toInt(), opus)
        runCatching { s.send(DatagramPacket(p, p.size, a, port)) }
    }

    fun disconnect() {
        sessionId = 0
        job?.cancel()
        job = null
        runCatching { sock?.close() }
        sock = null
    }
}
