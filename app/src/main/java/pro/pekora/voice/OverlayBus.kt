package pro.pekora.voice

object OverlayBus {
    @Volatile var room: String = ""
    @Volatile var speakers: List<Long> = emptyList()
    @Volatile var connected: Boolean = false
    @Volatile var muted: Boolean = false

    @Volatile var onToggleMute: (() -> Unit)? = null

    @Volatile var onClose: (() -> Unit)? = null

    fun label(): String {
        if (!connected) return "idle"
        if (speakers.isEmpty()) return if (room.isEmpty()) "connected" else "$room • quiet"
        return if (room.isEmpty()) "speaking: ${speakers.size}"
        else "$room • speaking: ${speakers.size}"
    }
}
