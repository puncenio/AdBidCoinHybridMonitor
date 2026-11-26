package com.example.adbidcoinmonitor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val requestCapture = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "AdBidCoin Hybrid Monitor"
            textSize = 23f
        }
        val info = TextView(this).apply {
            text = "Live OCR membaca teks yang tampil di layar. Buka Brave dan halaman klaim terlebih dahulu. Klaim tetap dilakukan manual."
            textSize = 16f
            setPadding(0, 20, 0, 24)
        }
        val overlayButton = Button(this).apply {
            text = "1. Izinkan floating overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else Toast.makeText(this@MainActivity, "Overlay sudah diizinkan", Toast.LENGTH_SHORT).show()
            }
        }
        val startButton = Button(this).apply {
            text = "2. Mulai Live OCR"
            setOnClickListener { requestScreenCapture() }
        }
        val stopButton = Button(this).apply {
            text = "Hentikan monitoring"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
            }
        }
        val openClaim = Button(this).apply {
            text = "Buka halaman klaim AdBidCoin"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://adbidcoin.com/claim")))
            }
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(overlayButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(openClaim)
        setContentView(layout)
    }

    private fun requestScreenCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan floating overlay terlebih dahulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    @Deprecated("Deprecated by Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            Toast.makeText(this, "Live OCR dimulai. Pindah ke Brave.", Toast.LENGTH_LONG).show()
        }
    }
}
