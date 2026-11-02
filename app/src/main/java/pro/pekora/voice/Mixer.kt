package pro.pekora.voice

class Mixer {
    fun mix(inputs: List<ShortArray>, out: ShortArray) {
        for (i in out.indices) {
            var s = 0
            for (inp in inputs) s += inp.getOrElse(i) { 0 }.toInt()
            out[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
