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
                    It is not your actual game ping.
                    No network route has been changed.
                """.trimIndent()

                saveHistory(average, jitter, networkType)
            }

            runOnUiThread {

                output.text = report

                updateQuality(
                    average,
                    jitter,
                    samples.isNotEmpty()
                )

                showNetworkStatus()
                loadHistory()

                runButton.isEnabled = true
            }
        }
    }

    private fun updateQuality(
        average: Double,
        jitter: Double,
        success: Boolean
    ) {

        if (!success) {
            qualityText.text = "NETWORK QUALITY: POOR"
            qualityText.setTextColor(Color.RED)
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

        qualityText.text = "NETWORK QUALITY: $quality"
        qualityText.setTextColor(color)
    }

    private fun format(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
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

        val dateFormat = SimpleDateFormat(
            "MM-dd HH:mm",
            Locale.getDefault()
        )

        val result = StringBuilder()

        for (i in 0 until history.length()) {

            val item = history.getJSONObject(i)

            val date = dateFormat.format(
                Date(item.getLong("time"))
            )

            result.append(
                "${i + 1}. $date\n"
            )

            result.append(
                "Latency: ${format(item.getDouble("latency"))} ms\n"
            )

            result.append(
                "Jitter: ${format(item.getDouble("jitter"))} ms\n"
            )

            result.append(
                "Network: ${item.getString("network")}\n\n"
            )
        }

        historyText.text = result.toString()
    }

    private fun clearHistory() {
        preferences.edit()
            .remove("tests")
            .apply()

        loadHistory()
    }

    override fun onDestroy() {
        executor.shutdownNow()

        window.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onDestroy()
    }
}

class LatencyChart(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var values = emptyList<Long>()

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            220
        )
    }

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

        canvas.drawLine(
            0f,
            height - 15f,
            width,
            height - 15f,
            paint
        )

        if (values.isEmpty()) {
            paint.color = Color.GRAY
            paint.textSize = 30f

            canvas.drawText(
                "Waiting for test...",
                15f,
                height / 2f,
                paint
            )

            return
        }

        val maxValue = (values.maxOrNull() ?: 1L)
            .coerceAtLeast(1L)

        val minValue = (values.minOrNull() ?: 0L)

        val step = width / (values.size + 1)

        val points = values.mapIndexed { index, value ->

            val x = step * (index + 1)

            val y = height - 25f -
                (value.toFloat() / maxValue.toFloat()) *
                (height - 60f)

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
            canvas.drawCircle(
                point.first,
                point.second,
                8f,
                paint
            )
        }

        paint.color = Color.DKGRAY
        paint.textSize = 25f

        canvas.drawText(
            "TCP Latency: ${minValue} - ${maxValue} ms",
            10f,
            28f,
            paint
        )
    }
}
