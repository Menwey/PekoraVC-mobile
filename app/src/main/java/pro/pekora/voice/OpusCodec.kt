package pro.pekora.voice

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder

class OpusCodec(
    private val sampleRate: Int = RelayProtocol.SAMPLE_RATE,
    private val channels: Int = RelayProtocol.CHANNELS,
) {
    private val encoder: OpusEncoder by lazy {
        OpusEncoder(sampleRate, channels, OpusApplication.OPUS_APPLICATION_VOIP).apply {
        }
    }
    private val decoder: OpusDecoder by lazy { OpusDecoder(sampleRate, channels) }
    private val encBuf = ByteArray(4000)

    @Synchronized
    fun encode(pcm960: ShortArray): ByteArray {
        require(pcm960.size == RelayProtocol.FRAME_SAMPLES) { "need 960 samples, got ${pcm960.size}" }
        val n = encoder.encode(pcm960, 0, RelayProtocol.FRAME_SAMPLES, encBuf, 0, encBuf.size)
        return encBuf.copyOf(n)
    }

    @Synchronized
    fun decode(opus: ByteArray, out960: ShortArray): Int {
        require(out960.size >= RelayProtocol.FRAME_SAMPLES) { "out buffer too small" }
        return decoder.decode(opus, 0, opus.size, out960, 0, RelayProtocol.FRAME_SAMPLES, false)
    }
}
