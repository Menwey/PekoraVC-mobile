package pro.pekora.voice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme

class TicketHandlerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        if (data != null) {
            val uri = data.toString()
            val ticket = extractTicket(uri)
            val prefs = getSharedPreferences("pekora_voice", MODE_PRIVATE)
            prefs.edit().putString("last_ticket", ticket ?: "").apply()
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (ticket != null) putExtra("ticket", ticket)
            }
            startActivity(mainIntent)
        }
        finish()
    }

    private fun extractTicket(uri: String): String? {
        val idx = uri.indexOf("gameinfo:", ignoreCase = true)
        if (idx < 0) return null
        val start = idx + "gameinfo:".length
        val end = uri.indexOf('+', start)
        return if (end >= 0) uri.substring(start, end) else uri.substring(start)
    }
}
