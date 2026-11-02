package pro.pekora.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class AudioPlayback(
    private val onLog: (String) -> Unit,
    private val onSpeakers: (List<Long>) -> Unit = {},
    private val volumeBoost: Float = 2.5f,
) {
    private var track: AudioTrack? = null
    private val codec = OpusCodec()
    private val mixer = Mixer()
    private val queues = ConcurrentHashMap<Long, ArrayDeque<ShortArray>>()
    private val lastSpoke = ConcurrentHashMap<Long, Long>()
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        stop()
        val track = tryTrack()
        if (track == null) {
            onLog("AudioTrack creation failed — voice output disabled")
            return
        }
        this.track = track
        track.play()
        running = true
        thread = Thread({ mixLoop() }, "voice-mix").also { it.start() }
        onLog("audio output started")
    }

    private fun tryTrack(): AudioTrack? {
        val configs = listOf(
            AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_DEFAULT).build(),
        )
        for (fmt in configs) {
            try {
                val minBuf = AudioTrack.getMinBufferSize(fmt.sampleRate, fmt.channelMask, fmt.encoding)
                val buf = maxOf(minBuf, 960 * 2 * 2)
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(buf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (t.state == AudioTrack.STATE_INITIALIZED) {
                    return t
                }
                t.release()
            } catch (e: Exception) {
                onLog("track cfg fail: ${e.message}")
            }
        }
        try {
            val t = AudioTrack(
                3,
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                960 * 2 * 4,
                AudioTrack.MODE_STREAM
            )
            if (t.state == AudioTrack.STATE_INITIALIZED) {
                onLog("legacy AudioTrack created (44100 mono STREAM_MUSIC)")
                return t
            }
            t.release()
        } catch (e: Exception) {
            onLog("legacy track failed: ${e.message}")
        }
        return null
    }

    fun pushAudio(opus: ByteArray, sender: Long) {
        if (!running || opus.isEmpty()) return
        val pcm = ShortArray(960)
        val n = runCatching { codec.decode(opus, pcm) }.getOrDefault(0)
        if (n <= 0) return
        val q = queues.getOrPut(sender) { ArrayDeque() }
        synchronized(q) { q.clear(); q.add(pcm) }
        lastSpoke[sender] = System.currentTimeMillis()
    }

    private fun mixLoop() {
        val out = ShortArray(960)
        val frame = ArrayList<ShortArray>(4)
        while (running) {
            frame.clear()
            for ((_, q) in queues) {
                synchronized(q) {
                    if (q.isNotEmpty()) {
                        var last: ShortArray? = null
                        while (q.isNotEmpty()) last = q.poll()
                        if (last != null) frame.add(last)
                    }
                }
            }
            if (frame.isNotEmpty()) {
                if (frame.size == 1) track?.write(applyGain(frame[0]), 0, 960)
                else { mixer.mix(frame, out); track?.write(applyGain(out), 0, 960) }
            }
            val cutoff = System.currentTimeMillis() - 1500
            lastSpoke.entries.removeIf { it.value < cutoff - 5000 }
            val active = lastSpoke.filter { it.value >= cutoff }.keys.sorted()
            if (active != lastSpoke.keys.toList()) {
                onSpeakers(active)
            }
            Thread.sleep(20)
        }
    }

    private fun applyGain(pcm: ShortArray): ShortArray {
        val r = ShortArray(pcm.size)
        for (i in pcm.indices) {
            var v = (pcm[i].toInt() * volumeBoost).toInt()
            v = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            r[i] = v.toShort()
        }
        return r
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread?.join(500)
        thread = null
        queues.clear()
        lastSpoke.clear()
        runCatching { track?.stop() }
        track?.release()
        track = null
    }
}
