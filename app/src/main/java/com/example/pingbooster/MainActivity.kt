package com.example.pingbooster

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val testRunning = AtomicBoolean(false)

    private lateinit var statusText: TextView
    private lateinit var qualityText: TextView
    private lateinit var output: TextView
    private lateinit var historyText: TextView
    private lateinit var monitorText: TextView
    private lateinit var recoveryText: TextView

    private lateinit var runButton: Button
    private lateinit var monitorButton: Button
    private lateinit var stabilityButton: Button
    private lateinit var chart: LatencyChart
    private lateinit var gamingButton: Button
    private lateinit var codButton: Button

    private var gamingMode = false
    private var codMode = false
    private var monitorEnabled = false
    private var networkCallbackRegistered = false

    private var lastNetworkAvailable: Boolean? = null

    private val monitorSamples = mutableListOf<Long>()

    private val preferences by lazy {
        getSharedPreferences(
            "ping_booster_data",
            Context.MODE_PRIVATE
        )
    }

    private val connectivityManager by lazy {
        getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {

            if (!monitorEnabled) return

            if (testRunning.compareAndSet(false, true)) {

                executor.execute {

                    val result = tcpTest()

                    runOnUiThread {

                        if (monitorEnabled) {

                            if (result >= 0) {

                                monitorSamples.add(result)

                                if (monitorSamples.size > 20) {
                                    monitorSamples.removeAt(0)
                                }

                                monitorText.text =
                                    "LIVE NETWORK MONITOR\n" +
                                    "Current TCP Latency: $result ms\n" +
                                    "Successful Samples: ${monitorSamples.size}"

                                chart.updateSamples(
                                    monitorSamples.toList()
                                )

                            } else {

                                monitorText.text =
                                    "LIVE NETWORK MONITOR\n" +
                                    "Connection test failed."
                            }
                        }
                    }

                    testRunning.set(false)
                }
            }

            handler.postDelayed(this, 3000)
        }
    }

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {

                runOnUiThread {

                    lastNetworkAvailable = true

                    recoveryText.text =
                        "CONNECTION RECOVERY MONITOR\n" +
                        "Network is available.\n" +
                        "Connection restored or available."

                    showNetworkStatus()
                }
            }

            override fun onLost(network: Network) {

                runOnUiThread {

                    lastNetworkAvailable = false

                    recoveryText.text =
                        "CONNECTION RECOVERY MONITOR\n" +
                        "Network connection lost.\n" +
                        "Waiting for reconnection..."

                    showNetworkStatus()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildInterface()
        registerNetworkMonitor()

        showNetworkStatus()
        loadHistory()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun buildInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val scrollView = ScrollView(this)

        val title = createTextView(27f).apply {
            text = "PING BOOSTER PRO"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 100, 190))
        }

        val subtitle = createTextView(13f).apply {
            text = "VERSION 4.0 - FINAL EDITION"
            gravity = Gravity.CENTER
        }

        statusText = createTextView(16f)
        qualityText = createTextView(20f)

        val networkTitle = createTitle("NETWORK ANALYZER")

        runButton = Button(this).apply {
            text = "START NETWORK ANALYSIS"
            setOnClickListener {
                runTest()
            }
        }

        val monitorTitle = createTitle("LIVE NETWORK MONITOR")

        monitorText = createTextView(15f).apply {
            text = "Live monitor is OFF."
        }

        monitorButton = Button(this).apply {
            text = "START LIVE MONITOR"
            setOnClickListener {
                toggleMonitor()
            }
        }

        val stabilityTitle = createTitle("CONNECTION STABILITY")

        stabilityButton = Button(this).apply {
            text = "START 20 TESTS"
            setOnClickListener {
                runStabilityTest()
            }
        }

        val recoveryTitle = createTitle("CONNECTION RECOVERY")

        recoveryText = createTextView(15f).apply {
            text = "Monitoring network changes."
        }

        val chartTitle = createTitle("LATENCY CHART")

        chart = LatencyChart(this)

        val gamingTitle = createTitle("GAMING DASHBOARD")

        gamingButton = Button(this).apply {
            text = "GAMING MODE: OFF"
            setOnClickListener {
                toggleGamingMode()
            }
        }

        codButton = Button(this).apply {
            text = "COD MOBILE MODE: OFF"
            setOnClickListener {
                toggleCodMode()
            }
        }

        output = createTextView(15f).apply {
            text = "Ready. Start a network analysis."
        }

        val historyTitle = createTitle("TEST HISTORY")

        historyText = createTextView(14f)

        val clearHistoryButton = Button(this).apply {
            text = "CLEAR HISTORY"
            setOnClickListener {
                clearHistory()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(networkTitle)
        root.addView(runButton)
        root.addView(qualityText)

        root.addView(monitorTitle)
        root.addView(monitorText)
        root.addView(monitorButton)

        root.addView(stabilityTitle)
        root.addView(stabilityButton)

        root.addView(recoveryTitle)
        root.addView(recoveryText)

        root.addView(chartTitle)
        root.addView(chart)

        root.addView(gamingTitle)
        root.addView(gamingButton)
        root.addView(codButton)

        root.addView(output)

        root.addView(historyTitle)
        root.addView(historyText)
        root.addView(clearHistoryButton)

        scrollView.addView(root)

        setContentView(scrollView)
    }

    private fun createTextView(size: Float): TextView {
        return TextView(this).apply {
            textSize = size
            setPadding(0, dp(10), 0, dp(10))
        }
    }

    private fun createTitle(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 18f
            setPadding(0, dp(20), 0, dp(8))
            setTextColor(Color.rgb(30, 100, 180))
        }
    }

    private fun getNetworkType(): String {

        val network = connectivityManager.activeNetwork
            ?: return "No connection"

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return "Unknown"

        return when {

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) -> "Wi-Fi"

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) -> "Mobile Data"

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_ETHERNET
            ) -> "Ethernet"

            else -> "Other Network"
        }
    }

    private fun hasInternetConnection(): Boolean {

        val network = connectivityManager.activeNetwork
            ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
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
            if (connected) {
                Color.rgb(30, 150, 70)
            } else {
                Color.RED
            }
        )
    }

    private fun tcpTest(): Long {

        val start = System.nanoTime()

        return try {

            Socket().use { socket ->

                socket.connect(
                    InetSocketAddress("1.1.1.1", 443),
                    2000
                )
            }

            (System.nanoTime() - start) / 1_000_000

        } catch (e: Exception) {
            -1L
        }
    }

    private fun toggleMonitor() {

        monitorEnabled = !monitorEnabled

        if (monitorEnabled) {

            monitorSamples.clear()

            monitorButton.text = "STOP LIVE MONITOR"

            monitorText.text =
                "Live monitoring started.\n" +
                "Testing every 3 seconds..."

            handler.post(monitorRunnable)

        } else {

            handler.removeCallbacks(monitorRunnable)

            monitorButton.text = "START LIVE MONITOR"

            monitorText.text =
                "Live monitoring stopped."
        }
    }

    private fun runTest() {

        if (!testRunning.compareAndSet(false, true)) {
            output.text = "Another test is running. Please wait."
            return
        }

        runButton.isEnabled = false
        stabilityButton.isEnabled = false

        output.text = "Analyzing network...\nPlease wait."

        qualityText.text = "NETWORK QUALITY: TESTING"
        qualityText.setTextColor(Color.GRAY)

        chart.updateSamples(emptyList())

        executor.execute {

            val samples = mutableListOf<Long>()
            var failed = 0

            repeat(8) {

                val result = tcpTest()

                if (result >= 0) {

                    samples.add(result)

                    runOnUiThread {
                        chart.updateSamples(samples.toList())
                    }

                } else {
                    failed++
                }
            }

            val dnsStart = System.nanoTime()

            val dnsAddress = try {
                InetAddress.getAllByName("cloudflare.com")
                    .firstOrNull()?.hostAddress ?: "Resolved"
            } catch (e: Exception) {
                "DNS lookup failed"
            }

            val dnsTime =
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

                    DNS Address: $dnsAddress
                    DNS Time: $dnsTime ms

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

                    DNS Lookup: $dnsTime ms
                    DNS Address: $dnsAddress

                    Gaming Mode: ${if (gamingMode) "ON" else "OFF"}
                    COD Mode: ${if (codMode) "ON" else "OFF"}

                    ----------------------------

                    This is a TCP test to Cloudflare.
                    It is not your actual game ping.
                    No network route has been changed.
                """.trimIndent()

                saveHistory(
                    average,
                    jitter,
                    networkType
                )
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
                stabilityButton.isEnabled = true

                testRunning.set(false)
            }
        }
    }

    private fun runStabilityTest() {

        stopMonitor()

        if (!testRunning.compareAndSet(false, true)) {
            output.text = "Another test is running. Please wait."
            return
        }

        runButton.isEnabled = false
        stabilityButton.isEnabled = false

        output.text =
            "CONNECTION STABILITY TEST\n" +
            "Running 20 TCP tests...\nPlease wait."

        chart.updateSamples(emptyList())

        executor.execute {

            val samples = mutableListOf<Long>()
            var failed = 0

            repeat(20) { index ->

                val result = tcpTest()

                if (result >= 0) {
                    samples.add(result)
                } else {
                    failed++
                }

                runOnUiThread {

                    output.text =
                        "CONNECTION STABILITY TEST\n" +
                        "Progress: ${index + 1}/20\n" +
                        "Successful: ${samples.size}\n" +
                        "Failed: $failed"

                    chart.updateSamples(samples.toList())
                }
            }

            val report: String

            var average = 0.0
            var jitter = 0.0

            if (samples.isEmpty()) {

                report =
                    "STABILITY TEST FAILED\n" +
                    "All 20 connection attempts failed."

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

                val failureRate = failed * 100.0 / 20.0

                report = """
                    STABILITY TEST COMPLETE

                    Total Tests: 20
                    Successful: ${samples.size}
                    Failed: $failed

                    Average: ${format(average)} ms
                    Best: ${sorted.first()} ms
                    Worst: ${sorted.last()} ms

                    Jitter: ${format(jitter)} ms
                    Failure Rate: ${format(failureRate)}%

                    ${getSmartDiagnosis(average, jitter)}

                    ----------------------------

                    TCP measurements to Cloudflare.
                    Not actual Call of Duty server ping.
                """.trimIndent()

                saveHistory(
                    average,
                    jitter,
                    getNetworkType()
                )
            }

            runOnUiThread {

                output.text = report

                updateQuality(
                    average,
                    jitter,
                    samples.isNotEmpty()
                )

                loadHistory()
                showNetworkStatus()

                runButton.isEnabled = true
                stabilityButton.isEnabled = true

                testRunning.set(false)
            }
        }
    }

    private fun getSmartDiagnosis(
        average: Double,
        jitter: Double
    ): String {

        return when {

            average < 100 && jitter < 20 ->
                "Diagnosis: GOOD CONNECTION"

            average < 180 && jitter < 40 ->
                "Diagnosis: MODERATE CONNECTION"

            else ->
                "Diagnosis: POOR CONNECTION"
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

            average < 100 && jitter < 20 ->
                "GOOD"

            average < 180 && jitter < 40 ->
                "MODERATE"

            else ->
                "POOR"
        }

        val color = when (quality) {

            "GOOD" -> Color.rgb(30, 160, 70)

            "MODERATE" -> Color.rgb(220, 150, 0)

            else -> Color.RED
        }

        qualityText.text = "NETWORK QUALITY: $quality"
        qualityText.setTextColor(color)
    }

    private fun toggleGamingMode() {

        gamingMode = !gamingMode

        if (gamingMode) {

            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )

            gamingButton.text = "GAMING MODE: ON"

        } else {

            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
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

            "COD Mobile mode enabled.\n" +
            "Network tests still use Cloudflare."

        } else {

            "Standard network testing mode enabled."
        }
    }

    private fun registerNetworkMonitor() {

        try {

            val request = NetworkRequest.Builder()
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                networkCallback
            )

            networkCallbackRegistered = true

        } catch (e: Exception) {

            recoveryText.text =
                "Network monitoring unavailable."
        }
    }

    private fun saveHistory(
        average: Double,
        jitter: Double,
        networkType: String
    ) {

        val history = try {

            JSONArray(
                preferences.getString("tests", "[]")
            )

        } catch (e: Exception) {

            JSONArray()
        }

        val item = JSONObject().apply {
            put("latency", average)
            put("jitter", jitter)
            put("network", networkType)
            put("time", System.currentTimeMillis())
        }

        val updated = JSONArray()

        updated.put(item)

        for (i in 0 until minOf(history.length(), 9)) {

            updated.put(
                history.getJSONObject(i)
            )
        }

        preferences.edit()
            .putString("tests", updated.toString())
            .apply()
    }

    private fun loadHistory() {

        val history = try {

            JSONArray(
                preferences.getString("tests", "[]")
            )

        } catch (e: Exception) {

            JSONArray()
        }

        if (history.length() == 0) {

            historyText.text = "No saved tests yet."

            return
        }

        val dateFormat = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        )

        val result = StringBuilder()

        for (i in 0 until history.length()) {

            val item = history.getJSONObject(i)

            val date = dateFormat.format(
                Date(item.getLong("time"))
            )

            result.append("${i + 1}. $date\n")

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

    private fun format(value: Double): String {

        return String.format(
            Locale.US,
            "%.1f",
            value
        )
    }

    private fun stopMonitor() {

        monitorEnabled = false

        handler.removeCallbacks(monitorRunnable)

        if (::monitorButton.isInitialized) {
            monitorButton.text = "START LIVE MONITOR"
        }
    }

    override fun onDestroy() {

        stopMonitor()

        if (networkCallbackRegistered) {

            try {
                connectivityManager.unregisterNetworkCallback(
                    networkCallback
                )
            } catch (e: Exception) {
                // Callback may already be unregistered.
            }
        }

        executor.shutdownNow()

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onDestroy()
    }
}

class LatencyChart(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var values = emptyList<Long>()

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    init {

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(220f).toInt()
        )
    }

    fun updateSamples(samples: List<Long>) {

        values = samples

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val chartWidth = width.toFloat()
        val chartHeight = height.toFloat()

        val left = dp(12f)
        val right = chartWidth - dp(12f)
        val top = dp(45f)
        val bottom = chartHeight - dp(25f)

        paint.color = Color.LTGRAY
        paint.strokeWidth = dp(1f)

        canvas.drawLine(
            left,
            bottom,
            right,
            bottom,
            paint
        )

        canvas.drawLine(
            left,
            top,
            left,
            bottom,
            paint
        )

        if (values.isEmpty()) {

            paint.color = Color.GRAY
            paint.textSize = dp(15f)

            canvas.drawText(
                "Waiting for network tests...",
                left,
                chartHeight / 2f,
                paint
            )

            return
        }

        val maxValue = (values.maxOrNull() ?: 1L)
            .coerceAtLeast(1L)

        val minValue = values.minOrNull() ?: 0L

        val range = maxValue.toFloat().coerceAtLeast(1f)

        val step = if (values.size > 1) {
            (right - left) / (values.size - 1)
        } else {
            0f
        }

        val points = values.mapIndexed { index, value ->

            val x = if (values.size == 1) {
                (left + right) / 2f
            } else {
                left + step * index
            }

            val y = bottom -
                (value.toFloat() / range) *
                (bottom - top)

            x to y
        }

        paint.color = Color.rgb(30, 130, 220)
        paint.strokeWidth = dp(3f)
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
                dp(4f),
                paint
            )
        }

        paint.color = Color.DKGRAY
        paint.textSize = dp(13f)

        canvas.drawText(
            "TCP Latency: $minValue - $maxValue ms",
            left,
            dp(25f),
            paint
        )
    }
}
