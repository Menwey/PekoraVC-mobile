package pro.pekora.voice

object Resampler {
    fun to48k(input: ShortArray, inRate: Int): ShortArray {
        if (inRate == RelayProtocol.SAMPLE_RATE) return input
        val ratio = RelayProtocol.SAMPLE_RATE.toDouble() / inRate
        val outLen = (input.size * ratio).toInt()
        return ShortArray(outLen) { i ->
            val pos = i / ratio
            val a = input[pos.toInt().coerceIn(input.indices)]
            val b = input[(pos.toInt() + 1).coerceIn(input.indices)]
            val f = (pos - pos.toInt()).toFloat()
            (a + ((b - a) * f)).toInt().toShort()
        }
    }
}
