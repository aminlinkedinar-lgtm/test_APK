
package com.example.pingbooster

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
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
    private lateinit var chart: LatencyChart
    private lateinit var historyText: TextView

    private val preferences by lazy {
        getSharedPreferences("network_history", Context.MODE_PRIVATE)
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
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "SMART NETWORK ANALYZER"
            textSize = 14f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }

        runButton = Button(this).apply {
            text = "START NETWORK ANALYSIS"
            setOnClickListener { runTest() }
        }

        val chartTitle = TextView(this).apply {
            text = "LATENCY CHART"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, pad, 0, 0)
        }

        chart = LatencyChart(this)

        output = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, 0)
            text = "Ready to analyze your network."
        }

        val historyTitle = TextView(this).apply {
            text = "TEST HISTORY"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, pad, 0, 0)
        }

        historyText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 10, 0, pad)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(runButton)
        layout.addView(chartTitle)
        layout.addView(chart)
        layout.addView(output)
        layout.addView(historyTitle)
        layout.addView(historyText)

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        showNetworkStatus()
        loadHistory()
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
        val networkType = getNetworkType()

        statusText.text = "Internet: ${if (connected) "Connected" else "Not verified"}\nNetwork: $networkType"
        statusText.setTextColor(
            if (connected) Color.rgb(30, 150, 70)
            else Color.RED
        )
    }

    private fun runTest() {
        runButton.isEnabled = false
        output.text = "Analyzing network...\nPlease wait."

        chart.updateSamples(emptyList())

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

                    runOnUiThread {
                        chart.updateSamples(samples.toList())
                    }

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

            val report: String
            val average: Double
            val jitter: Double

            if (samples.isEmpty()) {
                average = 0.0
                jitter = 0.0

                report = """
                    NETWORK ANALYSIS FAILED

                    Network: $networkType
                    Internet verified: $connected

                    Could not connect to:
                    1.1.1.1:443

                    Please check your connection.
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

                val loss = failed * 100.0 / 5.0

                report = """
                    NETWORK ANALYSIS COMPLETE

                    Connection: ${if (connected) "Connected" else "Not verified"}
                    Network Type: $networkType

                    TCP Latency: ${format(average)} ms

                    Best: ${sorted.first()} ms
                    Worst: ${sorted.last()} ms

                    Jitter Estimate: ${format(jitter)} ms

                    Failed Attempts: ${format(loss)}%

                    DNS Lookup: ${dnsMs} ms
                    DNS Address: $dnsResult

                    IMPORTANT:
                    This is TCP latency to Cloudflare,
                    not your game's exact ping.
                """.trimIndent()

                saveHistory(average, jitter, networkType)
            }

            runOnUiThread {
                output.text = report
                updateQuality(average, jitter, samples.isNotEmpty())
                showNetworkStatus()
                loadHistory()
                runButton.isEnabled = true
            }
        }
    }

    private fun format(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun updateQuality(
        average: Double,
        jitter: Double,
        success: Boolean
    ) {
        if (!success) {
            statusText.text = "NETWORK STATUS: POOR"
            statusText.setTextColor(Color.RED)
            return
        }

        val quality = when {
            average < 100 && jitter < 20 -> "GOOD"
            average < 180 && jitter < 40 -> "MODERATE"
            else -> "POOR"
        }

        val color = when (quality) {
            "GOOD" -> Color.rgb(30, 160, 70)
            "MODERATE" -> Color.rgb(220, 150, 0)
            else -> Color.RED
        }

        statusText.text = "NETWORK QUALITY: $quality"
        statusText.setTextColor(color)
    }

    private fun saveHistory(
        average: Double,
        jitter: Double,
        networkType: String
    ) {
        val history = JSONArray(
            preferences.getString("tests", "[]")
        )

        val item = JSONObject().apply {
            put("latency", average)
            put("jitter", jitter)
            put("network", networkType)
            put("time", System.currentTimeMillis())
        }

        val updated = JSONArray()
        updated.put(item)

        for (i in 0 until minOf(history.length(), 9)) {
            updated.put(history.getJSONObject(i))
        }

        preferences.edit()
            .putString("tests", updated.toString())
            .apply()
    }

    private fun loadHistory() {
        val history = JSONArray(
            preferences.getString("tests", "[]")
        )

        if (history.length() == 0) {
            historyText.text = "No saved tests yet."
            return
        }

        val result = StringBuilder()

        for (i in 0 until history.length()) {
            val item = history.getJSONObject(i)

            result.append(
                "${i + 1}. ${format(item.getDouble("latency"))} ms"
            )
            result.append(
                " | Jitter: ${format(item.getDouble("jitter"))} ms"
            )
            result.append(
                " | ${item.getString("network")}\n"
            )
        }

        historyText.text = result.toString()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

class LatencyChart(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var values = emptyList<Long>()

    fun updateSamples(samples: List<Long>) {
        values = samples
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        paint.color = Color.LTGRAY
        paint.strokeWidth = 2f
        canvas.drawLine(0f, height - 10f, width, height - 10f, paint)

        if (values.isEmpty()) {
            paint.color = Color.GRAY
            paint.textSize = 36f
            canvas.drawText("Waiting for test...", 15f, height / 2f, paint)
            return
        }

        val maxValue = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
        val step = width / (values.size + 1)

        val points = values.mapIndexed { index, value ->
            val x = step * (index + 1)
            val y = height - 20f -
                (value.toFloat() / maxValue.toFloat()) * (height - 50f)
            x to y
        }

        paint.color = Color.rgb(30, 130, 220)
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND

        for (i in 0 until points.size - 1) {
            canvas.drawLine(
                points[i].first,
                points[i].second,
                points[i + 1].first,
                points[i + 1].second,
                paint
            )
        }

        paint.color = Color.rgb(20, 100, 200)

        for (point in points) {
            canvas.drawCircle(point.first, point.second, 8f, paint)
        }

        paint.color = Color.DKGRAY
        paint.textSize = 28f

        canvas.drawText(
            "TCP Latency (ms)",
            10f,
            30f,
            paint
        )
    }
}
