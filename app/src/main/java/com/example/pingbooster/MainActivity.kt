**کد کامل MainActivity.kt نسخه 3.0**

```kotlin
package com.example.pingbooster

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var statusText: TextView
    private lateinit var qualityText: TextView
    private lateinit var output: TextView
    private lateinit var historyText: TextView
    private lateinit var runButton: Button
    private lateinit var chart: LatencyChart
    private lateinit var gamingButton: Button
    private lateinit var codButton: Button

    private var gamingMode = false
    private var codMode = false

    private val preferences by lazy {
        getSharedPreferences("ping_booster_data", Context.MODE_PRIVATE)
    }

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
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 100, 190))
        }

        val subtitle = TextView(this).apply {
            text = "VERSION 3.0 - GAMING NETWORK TOOLS"
            textSize = 13f
            gravity = Gravity.CENTER
        }

        statusText = createTextView(17f)
        qualityText = createTextView(20f)

        val networkTitle = createTitle("NETWORK ANALYZER")

        runButton = Button(this).apply {
            text = "START NETWORK ANALYSIS"
            setOnClickListener { runTest() }
        }

        val chartTitle = createTitle("LATENCY CHART")

        chart = LatencyChart(this)

        val gamingTitle = createTitle("GAMING TOOLS")

        gamingButton = Button(this).apply {
            text = "GAMING MODE: OFF"
            setOnClickListener { toggleGamingMode() }
        }

        codButton = Button(this).apply {
            text = "COD MOBILE MODE: OFF"
            setOnClickListener { toggleCodMode() }
        }

        val historyTitle = createTitle("TEST HISTORY")

        historyText = createTextView(14f)

        val clearHistoryButton = Button(this).apply {
            text = "CLEAR HISTORY"
            setOnClickListener { clearHistory() }
        }

        output = createTextView(15f).apply {
            text = "Ready. Start a network analysis."
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(networkTitle)
        layout.addView(runButton)
        layout.addView(qualityText)
        layout.addView(chartTitle)
        layout.addView(chart)
        layout.addView(gamingTitle)
        layout.addView(gamingButton)
        layout.addView(codButton)
        layout.addView(output)
        layout.addView(historyTitle)
        layout.addView(historyText)
        layout.addView(clearHistoryButton)

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        showNetworkStatus()
        loadHistory()
    }

    private fun createTextView(size: Float): TextView {
        return TextView(this).apply {
            textSize = size
            setPadding(0, 12, 0, 12)
        }
    }

    private fun createTitle(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 18f
            setPadding(0, 20, 0, 8)
            setTextColor(Color.rgb(30, 100, 180))
        }
    }

    private fun getNetworkType(): String {
        val manager = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val network = manager.activeNetwork ?: return "No connection"

        val capabilities = manager.getNetworkCapabilities(network)
            ?: return "Unknown"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                "Wi-Fi"

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                "Mobile Data"

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                "Ethernet"

            else -> "Other Network"
        }
    }

    private fun hasInternetConnection(): Boolean {
        val manager = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val network = manager.activeNetwork ?: return false

        val capabilities = manager.getNetworkCapabilities(network)
            ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) && capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
    }

    private fun showNetworkStatus() {
        val connected = hasInternetConnection()

        statusText.text =
            "Internet: ${if (connected) "Connected" else "Not verified"}\n" +
            "Network: ${getNetworkType()}"

        statusText.setTextColor(
            if (connected) Color.rgb(30, 150, 70) else Color.RED
        )
    }

    private fun toggleGamingMode() {
        gamingMode = !gamingMode

        if (gamingMode) {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            gamingButton.text = "GAMING MODE: ON"
        } else {
            window.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            gamingButton.text = "GAMING MODE: OFF"
        }
    }

    private fun toggleCodMode() {
        codMode = !codMode

        codButton.text = if (codMode) {
            "COD MOBILE MODE: ON"
        } else {
            "COD MOBILE MODE: OFF"
        }

        output.text = if (codMode) {
            "COD Mobile testing mode enabled.\n" +
            "Network tests will still use Cloudflare, not the actual game server."
        } else {
            "Standard network testing mode enabled."
        }
    }

    private fun runTest() {
        runButton.isEnabled = false
        output.text = "Analyzing network...\nPlease wait."

        qualityText.text = "NETWORK QUALITY: TESTING"
        qualityText.setTextColor(Color.GRAY)

        chart.updateSamples(emptyList())

        executor.execute {

            val host = "1.1.1.1"
            val samples = mutableListOf<Long>()
            var failed = 0

            repeat(8) {

                val start = System.nanoTime()

                try {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(host, 443),
                            2000
                        )
                    }

                    val elapsed =
                        (System.nanoTime() - start) / 1_000_000

                    samples.add(elapsed)

                    runOnUiThread {
                        chart.updateSamples(samples.toList())
                    }

                } catch (e: Exception) {
                    failed++
                }
            }

            val dnsStart = System.nanoTime()

            val dnsResult = try {
                InetAddress.getAllByName("cloudflare.com")
                    .firstOrNull()?.hostAddress ?: "Resolved"
            } catch (e: Exception) {
                "DNS lookup failed"
            }

            val dnsMs =
                (System.nanoTime() - dnsStart) / 1_000_000

            val networkType = getNetworkType()
            val connected = hasInternetConnection()

            val report: String
            var average = 0.0
            var jitter = 0.0

            if (samples.isEmpty()) {

                report = """
                    NETWORK ANALYSIS FAILED

                    Network: $networkType
                    Internet verified: $connected

                    Could not reach:
                    1.1.1.1:443

                    DNS result: $dnsResult
                    DNS time: $dnsMs ms

                    Check your internet connection.
                """.trimIndent()

            } else {

                val sorted = samples.sorted()

                average = samples.average()

                jitter = if (samples.size > 1) {
                    samples.zipWithNext { a, b ->
                        abs(a - b).toDouble()
                    }.average()
                } else {
                    0.0
                }

                val loss = failed * 100.0 / 8.0

                report = """
                    NETWORK ANALYSIS COMPLETE

                    Connection: ${if (connected) "Connected" else "Not verified"}
                    Network: $networkType

                    TCP Latency: ${format(average)} ms

                    Best: ${sorted.first()} ms
                    Worst: ${sorted.last()} ms

                    Jitter Estimate: ${format(jitter)} ms

                    Failed Attempts: ${format(loss)}%

                    DNS Lookup: $dnsMs ms
                    DNS Address: $dnsResult

                    Gaming Mode: ${if (gamingMode) "ON" else "OFF"}
                    COD Mode: ${if (codMode) "ON" else "OFF"}

                    ----------------------------

                    This is a TCP test to Cloudflare.
                    It is not
