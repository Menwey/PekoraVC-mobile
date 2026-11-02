package pro.pekora.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var dot: TextView? = null
    private var info: TextView? = null
    private var muteBtn: Button? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        buildBubble()
        handler.post(tick)
    }

    private fun startFg() {
        val ch = "voice-call"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(ch, "Voice call", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, ch)
            .setContentTitle("Voice connected" + (if (OverlayBus.room.isNotEmpty()) " • ${OverlayBus.room}" else ""))
            .setContentText("Works over games. Use the bubble to mute.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 1, notif,
            if (Build.VERSION.SDK_INT >= 29)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
    }

    private fun buildBubble() {
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val ctx = this

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        fun flat(fill: Int, stroke: Int? = null) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 4 * dp
            if (stroke != null) setStroke(px(1), stroke)
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = flat(Color.parseColor("#E6232527"), Color.parseColor("#4F5254"))
            setPadding(px(10), px(8), px(8), px(8))
        }
        val d = TextView(ctx).apply { text = "●"; textSize = 16f; setTextColor(Color.parseColor("#8C8E90")) }
        val t = TextView(ctx).apply {
            text = OverlayBus.label()
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(px(8), 0, px(10), 0)
        }
        fun chip(label: String, fill: Int, stroke: Int? = null) = Button(ctx).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = flat(fill, stroke)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(px(12), px(6), px(12), px(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, px(32),
            ).apply { marginStart = px(6) }
        }
        val mute = chip("Mute", Color.TRANSPARENT, Color.parseColor("#BDBEBE"))
        val close = chip("✕", Color.parseColor("#E2231A"))
        dot = d
        info = t
        muteBtn = mute
        layout.addView(d)
        layout.addView(t)
        layout.addView(mute)
        layout.addView(close)

        mute.setOnClickListener { OverlayBus.onToggleMute?.invoke() }
        close.setOnClickListener { OverlayBus.onClose?.invoke() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        var dx = 0
        var dy = 0
        layout.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dx = params.x - ev.rawX.toInt()
                    dy = params.y - ev.rawY.toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ev.rawX.toInt() + dx
                    params.y = ev.rawY.toInt() + dy
                    try { wm?.updateViewLayout(v, params) } catch (_: Exception) {}
                }
            }
            false
        }

        root = layout
        try {
            wm?.addView(layout, params)
        } catch (e: Exception) {
            stopSelf()
        }
        refresh()
    }

    private fun refresh() {
        val speaking = OverlayBus.speakers.isNotEmpty() && OverlayBus.connected
        dot?.setTextColor(if (speaking) Color.parseColor("#02B757") else Color.parseColor("#8C8E90"))
        info?.text = OverlayBus.label()
        muteBtn?.text = if (OverlayBus.muted) "Unmute" else "Mute"
        muteBtn?.background = GradientDrawable().apply {
            cornerRadius = 4 * resources.displayMetrics.density
            if (OverlayBus.muted) setColor(Color.parseColor("#335FFF"))
            else { setColor(Color.TRANSPARENT); setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#BDBEBE")) }
        }
        if (!OverlayBus.connected) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            root?.let { wm?.removeView(it) }
        } catch (_: Exception) {}
        root = null
        wm = null
        super.onDestroy()
    }
}
