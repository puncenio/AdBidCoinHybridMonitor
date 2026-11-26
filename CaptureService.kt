package com.example.adbidcoinmonitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.speech.tts.TextToSpeech
import android.media.AudioManager
import android.media.ToneGenerator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "monitor.START"
        const val ACTION_STOP = "monitor.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "adbidcoin_monitor"
        private const val NOTIFICATION_ID = 77
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var worker: HandlerThread? = null
    private var overlay: View? = null
    private var lastCode: String = ""
    private val processing = AtomicBoolean(false)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START) {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            startCapture(intent)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "AdBidCoin Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, CaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("AdBidCoin monitoring aktif")
            .setContentText("Live OCR berjalan — klaim tetap manual")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(Notification.Action.Builder(null, "Stop", pending).build())
            .build()
    }

    private fun startCapture(intent: Intent) {
        try {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
            }

            if (data == null || resultCode != Activity.RESULT_OK) {
                stopMonitoring(); return
            }

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopMonitoring()
                }
            }, Handler(Looper.getMainLooper()))
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            worker = HandlerThread("AdBidCoinOCR").also { it.start() }
            val handler = Handler(worker!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader!!.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!processing.compareAndSet(false, true)) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                processImage(image)
            }, handler)

            virtualDisplay = projection?.createVirtualDisplay(
                "AdBidCoinLiveOCR", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler
            )
            showOverlay("Monitoring aktif — menunggu kode…")
        } catch (e: Exception) {
            stopMonitoring()
        }
    }

    private fun processImage(image: Image) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            image.close()

            val input = InputImage.fromBitmap(cropped, 0)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val text = result.text
                    val candidate = findCandidate(text)
                    if (candidate != null && candidate != lastCode) {
                        lastCode = candidate
                        alertUser(candidate)
                    }
                }
                .addOnCompleteListener {
                    cropped.recycle()
                    processing.set(false)
                }
        } catch (e: Exception) {
            try { image.close() } catch (_: Exception) {}
            processing.set(false)
        }
    }

    // AdBidCoin Art Drop format: AD-XXXX-XXXX (letters and digits).
    private fun findCandidate(text: String): String? {
        val normalized = text.uppercase(Locale.US)
            .replace('–', '-')
            .replace('—', '-')
            .replace(Regex("\\s*-\\s*"), "-")
        val regex = Pattern.compile("\\bAD-[A-Z0-9]{4}-[A-Z0-9]{4}\\b")
        val matcher = regex.matcher(normalized)
        return if (matcher.find()) matcher.group() else null
    }

    private fun alertUser(code: String) {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)

        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }

        showOverlay("Kandidat kode terdeteksi: $code")
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID + 1, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Periksa Art Drop")
            .setContentText("Teks kandidat terdeteksi: $code")
            .setAutoCancel(true)
            .build())
    }

    private fun showOverlay(message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                if (overlay == null) {
                    val panel = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(22, 16, 22, 12)
                        setBackgroundColor(Color.rgb(35, 35, 35))
                    }
                    val label = TextView(this).apply {
                        setTextColor(Color.WHITE); textSize = 15f
                    }
                    label.tag = "message"
                    val copy = Button(this).apply {
                        text = "Salin kandidat"
                        setOnClickListener {
                            val current = (panel.findViewWithTag<TextView>("message").text.toString())
                            val code = current.substringAfter(": ", "")
                            if (code.isNotBlank() && code != "menunggu kode…") {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Art Drop candidate", code))
                            }
                        }
                    }
                    val close = Button(this).apply {
                        text = "Tutup overlay"
                        setOnClickListener { try { wm.removeView(panel); overlay = null } catch (_: Exception) {} }
                    }
                    panel.addView(label); panel.addView(copy); panel.addView(close)
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply { gravity = Gravity.TOP; y = 80 }
                    wm.addView(panel, params)
                    overlay = panel
                }
                overlay?.findViewWithTag<TextView>("message")?.text = message
            } catch (_: Exception) { }
        }
    }

    private fun stopMonitoring() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        try { worker?.quitSafely() } catch (_: Exception) {}
        worker = null
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            overlay?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        overlay = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
