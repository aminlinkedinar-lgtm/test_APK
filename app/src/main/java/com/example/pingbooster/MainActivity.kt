package com.example.pingbooster

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var output: TextView
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "PING BOOSTER PRO"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Network check for online games"
            textSize = 14f
            gravity = Gravity.CENTER
        }
        runButton = Button(this).apply {
            text = "START NETWORK TEST"
            setOnClickListener { runTest() }
        }
        output = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, 0)
            text = "Ready. Connect to the Wi-Fi or mobile network you use for gaming, then run the test."
        }
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(runButton)
        layout.addView(output)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun runTest() {
        runButton.isEnabled = false
        output.text = "Testing DNS and connection latency…"
        executor.execute {
            val host = "1.1.1.1"
            val samples = mutableListOf<Long>()
            var failed = 0
            repeat(8) {
                val start = System.nanoTime()
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, 443), 2500)
                    }
                    samples.add((System.nanoTime() - start) / 1_000_000)
                } catch (_: Exception) {
                    failed++
                }
            }
            val dnsStart = System.nanoTime()
            val dnsResult = try {
                InetAddress.getByName("cloudflare.com").hostAddress ?: "Resolved"
            } catch (_: Exception) { "DNS lookup failed" }
            val dnsMs = (System.nanoTime() - dnsStart) / 1_000_000

            val report = if (samples.isEmpty()) {
                "Could not reach the test server (1.1.1.1:443).\nCheck your connection and try again."
            } else {
                val sorted = samples.sorted()
                val avg = samples.average()
                val jitter = if (samples.size > 1) {
                    samples.zipWithNext { a, b -> abs(a - b).toDouble() }.average()
                } else 0.0
                val loss = failed * 100.0 / 8.0
                "TCP connection latency to Cloudflare: ${"%.1f".format(avg)} ms\n" +
                "Best / worst: ${sorted.first()} / ${sorted.last()} ms\n" +
                "Variation estimate: ${"%.1f".format(jitter)} ms\n" +
                "Failed attempts: ${"%.0f".format(loss)}%\n" +
                "DNS lookup: ${dnsMs} ms ($dnsResult)\n\n" +
                "Important: this is a TCP test, not your game's exact ping. " +
                "It does not change your route or guarantee lower in-game latency."
            }
            runOnUiThread {
                output.text = report
                runButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
