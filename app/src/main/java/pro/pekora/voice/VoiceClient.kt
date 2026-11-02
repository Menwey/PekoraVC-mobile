package pro.pekora.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class VoiceClient(
    private val server: String,
    private val ticket: String = "",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchToken(ticket: String): RelayProtocol.TokenResponse = withContext(Dispatchers.IO) {
        val body = "{\"ticket\":\"" + ticket.trim() + "\"}"
        val mediaType = "application/json".toMediaType()
        val reqBody = body.toRequestBody(mediaType)
        val req = Request.Builder()
            .url(RelayProtocol.tokenUrl(server))
            .header("User-Agent", RelayProtocol.USER_AGENT)
            .post(reqBody)
            .build()
        val call = http.newCall(req)
        val response = call.execute()
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException("voice token unavailable: HTTP " + r.code + " " + text.take(160))
            RelayProtocol.parseToken(text)
        }
    }

    fun manual(relay: String, token: String, userId: Long = 0, room: String = ""): RelayProtocol.TokenResponse {
        val (h, p) = RelayProtocol.relayHostPort(relay)
        return RelayProtocol.TokenResponse(relay = h + ":" + p, token = token, host = h, port = p)
    }
}
