package pro.pekora.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.pow
import kotlin.math.sqrt

class MicCapture(
    private val context: Context,
    private val onFrame: (ShortArray) -> Unit,
    private val onLevel: (Float) -> Unit = { _ -> },
    private val onLog: (String) -> Unit,
) {
    private var thread: Thread? = null
    @Volatile private var running = false

    fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        else true

    fun start() {
        if (!hasPermission()) { onLog("mic permission DENIED"); return }
        stop()
        running = true
        thread = Thread({
            val sr = 48000
            val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(960 * 2)
            var rec: AudioRecord? = null
            try {
                rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
                if (rec.state != AudioRecord.STATE_INITIALIZED) { onLog("mic init failed state=${rec.state}"); return@Thread }
                onLog("mic started ${sr}Hz")
                rec.startRecording()
                val frame = ShortArray(960)
                while (running) {
                    val n = rec.read(frame, 0, 960)
                    if (n < 0) { onLog("mic read error $n"); break }
                    if (n == 960) {
                        onFrame(frame.copyOf())
                        var sum = 0.0
                        for (i in frame.indices) {
                            sum += (frame[i].toInt() / 32768.0).pow(2)
                        }
                        val rms = sqrt(sum / frame.size).toFloat()
                        onLevel(rms.coerceIn(0f, 1f))
                    }
                }
                onLog("mic stream ended")
            } catch (e: Exception) {
                onLog("mic failed: ${e.message}")
            } finally {
                runCatching { rec?.stop() }
                rec?.release()
            }
        }, "voice-mic").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread?.join(500)
        thread = null
    }
}
