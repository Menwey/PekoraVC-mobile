package pro.pekora.voice

import android.content.Context
import android.media.AudioManager

object AudioDevices {
    data class Dev(val id: String, val name: String)

    fun inputs(ctx: Context): List<Dev> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return listOf(Dev("default", "Microphone (default)")) +
            am.getDevices(AudioManager.GET_DEVICES_ALL).filter { it.isSource }
                .map { Dev(it.productName?.toString() ?: it.type.toString(), it.productName?.toString() ?: "input") }
    }

    fun outputs(ctx: Context): List<Dev> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return listOf(Dev("default", "Speakers (default)")) +
            am.getDevices(AudioManager.GET_DEVICES_ALL).filter { it.isSink }
                .map { Dev(it.productName?.toString() ?: it.type.toString(), it.productName?.toString() ?: "output") }
    }
}
