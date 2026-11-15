package com.example.pingbooster

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var output: TextView
    private lateinit var runButton: Button
    private lateinit var statusText: TextView

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
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "SMART NETWORK ANALYZER"
            textSize = 14f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }

        runButton = Button(this).apply {
            text = "START NETWORK ANALYSIS"
            setOnClickListener {
                runTest()
            }
        }

        output = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, 0)
            text = "Ready to analyze your network."
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(runButton)
        layout.addView(output)

        setContentView(
            ScrollView(this).apply {
                addView(layout)
            }
        )

        showNetworkStatus()
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

        val networkType = getNetworkType()
        val connected = hasInternetConnection()

        statusText.text = if (connected) {
            "Internet: Connected\nNetwork: $networkType"
        } else {
            "Internet: Not verified\nNetwork: $networkType"
        }
    }

    private fun runTest() {

        runButton.isEnabled = false
        output.text = "Analyzing network...\nPlease wait."

        executor.execute {

            val host = "1.1.1.1"
            val samples = mutableListOf<Long>()
            var failed = 0

            repeat(5) {

                val start = System.nanoTime()

                try {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(host, 443),
                            1800
                        )
                    }

                    val elapsed =
                        (System.nanoTime() - start) / 1_000_000

                    samples.add(elapsed)

                } catch (e: Exception) {
                    failed++
                }
            }

            val dnsStart = System.nanoTime()

            val dnsResult = try {
                InetAddress.getByName("cloudflare.com").hostAddress
                    ?: "Resolved"
            } catch (e: Exception) {
                "DNS lookup failed"
            }

            val dnsMs =
                (System.nanoTime() - dnsStart) / 1_000_000

            val networkType = getNetworkType()
            val connected = hasInternetConnection()

            val report = if (samples.isEmpty()) {

                """
                NETWORK ANALYSIS FAILED

                Network: $networkType
                Internet verified: $connected

                Could not connect to:
                1.1.1.1:443

                Please check your internet connection.
                """.trimIndent()

            } else {

                val sorted = samples.sorted()

                val average = samples.average()

                val jitter = if (samples.size > 1) {
                    samples.zipWithNext { a, b ->
                        abs(a - b).toDouble()
                    }.average()
                } else {
                    0.0
                }

                val loss = failed * 100.0 / 5.0

                val quality = when {
                    average < 80 -> "Good"
                    average < 150 -> "Moderate"
                    else -> "High Latency"
                }

                """
                NETWORK ANALYSIS COMPLETE

                Connection: ${if (connected) "Connected" else "Not verified"}
                Network Type: $networkType

                TCP Latency: ${String.format(Locale.US, "%.1f", average)} ms

                Best: ${sorted.first()} ms
                Worst: ${sorted.last()} ms

                Jitter Estimate: ${String.format(Locale.US, "%.1f", jitter)} ms

                Failed Attempts: ${String.format(Locale.US, "%.0f", loss)}%

                DNS Lookup: ${dnsMs} ms
                DNS Address: $dnsResult

                Network Assessment: $quality

                ----------------------------

                IMPORTANT:
                This test measures TCP connection latency
                to Cloudflare, not Call of Duty Mobile's
                actual game server ping.

                No traffic routing has been changed.
                """.trimIndent()
            }

            runOnUiThread {
                output.text = report
                statusText.text =
                    "Network: $networkType\nInternet: ${if (connected) "Connected" else "Not verified"}"

                runButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
