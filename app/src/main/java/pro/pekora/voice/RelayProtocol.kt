package pro.pekora.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object RelayProtocol {
    const val PROTOCOL_VERSION: Byte = 0x01
    const val SAMPLE_RATE = 48000
    const val FRAME_SAMPLES = 960
    const val CHANNELS = 1
    const val OPUS_BITRATE_BPS = 32000
    const val MAX_OPUS_FRAME = 1000
    const val USER_AGENT: String = "PekoraVoice-Android/0.1"

    object Type {
        const val CLIENT_HELLO: Byte = 0x01
        const val CLIENT_STATUS: Byte = 0x02
        const val CLIENT_AUDIO: Byte = 0x04
        const val SERVER_WELCOME: Byte = 0x81.toByte()
        const val SERVER_STATE: Byte = 0x82.toByte()
        const val SERVER_AUDIO: Byte = 0x84.toByte()
        const val SERVER_POS: Byte = 0x85.toByte()
        const val SERVER_BYE: Byte = 0x8F.toByte()
    }

    const val HELLO_INTERVAL_MS = 1000L
    const val STATUS_INTERVAL_MS = 1000L
    const val SILENCE_TIMEOUT_MS = 6000L

    @Serializable
    data class TokenResponse(
        val token: String = "",
        val host: String = "",
        val port: Int = 0,
        val relay: String = "",
    )

    @Serializable
    data class StatusMsg(
        val ack: Int = 0,
        val inputs: List<String> = emptyList(),
        val outputs: List<String> = emptyList(),
        val input: String = "",
        val output: String = "",
        val level: Float = 0f,
        val err: String = "",
    )

    val json = Json { ignoreUnknownKeys = true }

    fun parseToken(body: String): TokenResponse {
        val parsed = json.decodeFromString<TokenResponse>(body)
        return parsed.copy(relay = parsed.host + ":" + parsed.port)
    }

    fun tokenUrl(server: String): String {
        val s = server.trim()
        return if (s.startsWith("http://") || s.startsWith("https://")) {
            s + "/v1/voice/launcher-token"
        } else {
            "http://" + s + "/v1/voice/launcher-token"
        }
    }

    fun relayHostPort(relay: String): Pair<String, Int> {
        val h = relay.substringBefore(":")
        val p = relay.substringAfter(":", "0").toIntOrNull() ?: 0
        return Pair(h, p)
    }

    fun buildHello(token: String): ByteArray {
        val tb = token.toByteArray()
        return byteArrayOf(Type.CLIENT_HELLO, PROTOCOL_VERSION) + tb
    }

    fun buildAudio(sessionId: Long, sequence: Int, opus: ByteArray): ByteArray {
        val out = ByteArray(1 + 8 + 2 + opus.size)
        out[0] = Type.CLIENT_AUDIO
        var s = sessionId
        for (i in 0 until 8) {
            out[1 + i] = (s and 0xFF).toByte()
            s = s shr 8
        }
        var seq = sequence.toLong()
        for (i in 0 until 2) {
            out[9 + i] = (seq and 0xFF).toByte()
            seq = seq shr 8
        }
        opus.copyInto(out, 11)
        return out
    }

    fun buildStatus(sessionId: Long, status: StatusMsg): ByteArray {
        val body = RelayProtocol.json.encodeToString(StatusMsg.serializer(), status).toByteArray()
        val out = ByteArray(1 + 8 + body.size)
        out[0] = Type.CLIENT_STATUS
        var s = sessionId
        for (i in 0 until 8) {
            out[1 + i] = (s and 0xFF).toByte()
            s = s shr 8
        }
        body.copyInto(out, 9)
        return out
    }

    sealed interface Packet {
        data class Welcome(val sessionId: Long, val userId: Long) : Packet
        data class State(val json: String) : Packet
        data class Audio(val sender: Long, val sequence: Int, val opus: ByteArray) : Packet
        data class Pos(val json: String) : Packet
        data class Bye(val reason: String) : Packet
        data class Unknown(val raw: ByteArray) : Packet
    }

    fun parsePacket(buf: ByteArray, len: Int): Packet {
        if (len < 1) return Packet.Unknown(byteArrayOf())
        return when (buf[0]) {
            Type.SERVER_WELCOME -> {
                if (len < 17) return Packet.Unknown(buf.copyOf(len))
                var sid = 0L
                for (i in 0 until 8) sid = sid or ((buf[1 + i].toLong() and 0xFF) shl (8 * i))
                var uid = 0L
                for (i in 0 until 8) uid = uid or ((buf[9 + i].toLong() and 0xFF) shl (8 * i))
                Packet.Welcome(sid, uid)
            }
            Type.SERVER_STATE -> Packet.State(buf.decodeToString(1, len))
            Type.SERVER_AUDIO -> {
                if (len < 11) return Packet.Unknown(buf.copyOf(len))
                var sender = 0L
                for (i in 0 until 8) sender = sender or ((buf[1 + i].toLong() and 0xFF) shl (8 * i))
                var seq = 0
                for (i in 0 until 2) seq = seq or ((buf[9 + i].toInt() and 0xFF) shl (8 * i))
                Packet.Audio(sender, seq, buf.copyOfRange(11, len))
            }
            Type.SERVER_POS -> Packet.Pos(buf.decodeToString(1, len))
            Type.SERVER_BYE -> Packet.Bye(buf.decodeToString(1, len))
            else -> Packet.Unknown(buf.copyOf(len))
        }
    }

    fun u64le(v: Long): ByteArray {
        val out = ByteArray(8)
        var x = v
        for (i in 0 until 8) {
            out[i] = (x and 0xFF).toByte()
            x = x shr 8
        }
        return out
    }

    fun u16le(v: Int): ByteArray {
        return byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }
}
