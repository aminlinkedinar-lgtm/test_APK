package com.example.pingbooster

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.Canvas
import android.content.Context
import android.graphics.drawable.GradientDrawable
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
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
    private lateinit var speedText: TextView
    private lateinit var dnsText: TextView
    private lateinit var optimizerText: TextView

    private lateinit var runButton: Button
    private lateinit var speedButton: Button
    private lateinit var dnsButton: Button
    private lateinit var optimizerButton: Button
    private lateinit var monitorButton: Button
    private lateinit var stabilityButton: Button
    private lateinit var gamingButton: Button
    private lateinit var codButton: Button

    private lateinit var chart: LatencyChart

    private var gamingMode = false
    private var codMode = false
    private var monitorEnabled = false
    private var callbackRegistered = false

    private val monitorSamples = mutableListOf<Long>()

    private val preferences by lazy {
        getSharedPreferences("ping_booster_data", Context.MODE_PRIVATE)
    }

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val blue = Color.rgb(30, 140, 255)
    private val background = Color.rgb(10, 16, 30)
    private val cardColor = Color.rgb(21, 31, 51)
    private val green = Color.rgb(30, 210, 130)
    private val white = Color.WHITE
    private val gray = Color.rgb(170, 185, 205)

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

    private fun createBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(16).toFloat()
        }
    }

    private fun createText(
        value: String,
        size: Float,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun createButton(value: String): Button {
        return Button(this).apply {
            text = value
            textSize = 13f
            isAllCaps = false
            setTextColor(white)
            background = createBackground(blue)
        }
    }

    private fun createCard(title: String): LinearLayout {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = createBackground(cardColor)
        }

        val heading = createText(title, 17f, green, true)
        card.addView(heading)

        return card
    }

    private fun addSection(
        parent: LinearLayout,
        title: String,
        content: View
    ) {
        val card = createCard(title)

        card.addView(content)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(0, dp(8), 0, dp(8))

        parent.addView(card, params)
    }

    private fun buildInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(background)
        }

        val scroll = ScrollView(this)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, dp(20))
        }

        header.addView(
            createText("PING BOOSTER PRO", 27f, blue, true)
        )

        header.addView(
            createText("ULTIMATE GAMING NETWORK TOOL", 12f, gray)
        )

        header.addView(
            createText("VERSION 5.0", 13f, green, true)
        )

        root.addView(header)

        statusText = createText("Checking network...", 15f)
        addSection(root, "NETWORK STATUS", statusText)

        qualityText = createText("NETWORK QUALITY: UNKNOWN", 20f, gray, true)
        addSection(root, "CONNECTION QUALITY", qualityText)

        speedText = createText(
            "Download: -- Mbps\nUpload: -- Mbps",
            16f
        )

        speedButton = createButton("START INTERNET SPEED TEST")
        speedButton.setOnClickListener {
            runSpeedTest()
        }

        val speedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(speedText)
            addView(speedButton)
        }

        addSection(root, "INTERNET SPEED TEST", speedLayout)

        dnsText = createText("DNS comparison has not started.", 14f)

        dnsButton = createButton("COMPARE DNS SERVERS")
        dnsButton.setOnClickListener {
            runDnsTest()
        }

        val dnsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dnsText)
            addView(dnsButton)
        }

        addSection(root, "DNS OPTIMIZER", dnsLayout)

        optimizerText = createText(
            "Run network analysis to receive recommendations.",
            14f
        )

        optimizerButton = createButton("ANALYZE MY NETWORK")
        optimizerButton.setOnClickListener {
            runSmartOptimizer()
        }

        val optimizerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(optimizerText)
            addView(optimizerButton)
        }

        addSection(root, "SMART NETWORK OPTIMIZER", optimizerLayout)

        runButton = createButton("START NETWORK ANALYSIS")
        runButton.setOnClickListener {
            runTest()
        }

        addSection(root, "NETWORK ANALYZER", runButton)

        monitorText = createText("Live monitor is OFF.", 14f)

        monitorButton = createButton("START LIVE MONITOR")
        monitorButton.setOnClickListener {
            toggleMonitor()
        }

        val monitorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(monitorText)
            addView(monitorButton)
        }

        addSection(root, "LIVE NETWORK MONITOR", monitorLayout)

        stabilityButton = createButton("START 20 TESTS")
        stabilityButton.setOnClickListener {
            runStabilityTest()
        }

        addSection(root, "CONNECTION STABILITY", stabilityButton)

        recoveryText = createText("Monitoring network changes.", 14f)
        addSection(root, "CONNECTION RECOVERY", recoveryText)

        chart = LatencyChart(this)
        addSection(root, "LATENCY CHART", chart)

        gamingButton = createButton("GAMING MODE: OFF")
        gamingButton.setOnClickListener {
            toggleGamingMode()
        }

        codButton = createButton("COD MOBILE MODE: OFF")
        codButton.setOnClickListener {
            toggleCodMode()
        }

        val gamingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(gamingButton)
            addView(codButton)
        }

        addSection(root, "GAMING DASHBOARD", gamingLayout)

        output = createText("Welcome to Ping Booster Pro 5.0", 14f)
        addSection(root, "TEST REPORT", output)

        historyText = createText("No saved tests yet.", 13f)

        val clearButton = createButton("CLEAR HISTORY")
        clearButton.setOnClickListener {
            clearHistory()
        }

        val historyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(historyText)
            addView(clearButton)
        }

        addSection(root, "TEST HISTORY", historyLayout)

        val guide = createText(
            """
            1. Check your network status.
            2. Run Internet Speed Test.
            3. Compare DNS response times.
            4. Use Smart Optimizer for recommendations.
            5. Run Stability Test to check connection stability.
            6. Use Live Monitor to observe network latency.

            IMPORTANT:
            This application measures network performance.
            It does not guarantee lower game ping.
            Game server latency may be different.
            """.trimIndent(),
            13f,
            gray
        )

        addSection(root, "HOW TO USE", guide)

        val footer = createText(
            "PING BOOSTER PRO | FINAL PROJECT",
            12f,
            blue,
            true
        )

        footer.gravity = Gravity.CENTER

        root.addView(footer)

        scroll.addView(root)

        setContentView(scroll)
    }

    private fun getNetworkType(): String {

        val network = connectivityManager.activeNetwork
            ?: return "No connection"

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
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
            if (connected) green else Color.RED
        )
    }

    private fun tcpTest(): Long {

        val start = System.nanoTime()

        return try {

            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress("1.1.1.1", 443),
                    2500
                )
            }

            (System.nanoTime() - start) / 1_000_000

        } catch (e: Exception) {
            -1L
        }
    }

    private fun runOnBackground(
        button: Button,
        action: () -> Unit
    ) {

        if (!testRunning.compareAndSet(false, true)) {
            output.text = "Another test is running. Please wait."
            return
        }

        button.isEnabled = false

        executor.execute {

            try {
                action()
            } catch (e: Exception) {

                runOnUiThread {
                    output.text = "Test error: ${e.message}"
                }

            } finally {

                runOnUiThread {
                    button.isEnabled = true
                    testRunning.set(false)
                }
            }
        }
    }

    private fun runSpeedTest() {

        speedText.text = "Testing download and upload..."

        runOnBackground(speedButton) {

            val downloadBytes = 1_000_000
            val uploadBytes = 250_000

            var downloadSpeed = 0.0
            var uploadSpeed = 0.0

            try {

                val start = System.nanoTime()

                val connection = URL(
                    "https://speed.cloudflare.com/__down?bytes=$downloadBytes"
                ).openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"

                var received = 0L

                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)

                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        received += count
                    }
                }

                connection.disconnect()

                val seconds =
                    (System.nanoTime() - start) / 1_000_000_000.0

                if (seconds > 0) {
                    downloadSpeed =
                        received * 8.0 / seconds / 1_000_000.0
                }

            } catch (e: Exception) {
                downloadSpeed = -1.0
            }

            try {

                val data = ByteArray(uploadBytes) { 65 }

                val start = System.nanoTime()

                val connection = URL(
                    "https://speed.cloudflare.com/__up"
                ).openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/octet-stream"
                )

                connection.outputStream.use {
                    it.write(data)
                }

                val response = connection.responseCode

                if (response in 200..299) {
                    connection.inputStream.use {
                        while (it.read() != -1) {}
                    }
                }

                connection.disconnect()

                val seconds =
                    (System.nanoTime() - start) / 1_000_000_000.0

                if (seconds > 0 && response in 200..299) {
                    uploadSpeed =
                        uploadBytes * 8.0 / seconds / 1_000_000.0
                } else {
                    uploadSpeed = -1.0
                }

            } catch (e: Exception) {
                uploadSpeed = -1.0
            }

            val downloadResult = if (downloadSpeed >= 0) {
                "${format(downloadSpeed)} Mbps"
            } else {
                "Failed"
            }

            val uploadResult = if (uploadSpeed >= 0) {
                "${format(uploadSpeed)} Mbps"
            } else {
                "Failed"
            }

            runOnUiThread {

                speedText.text =
                    "Download: $downloadResult\n" +
                    "Upload: $uploadResult\n\n" +
                    "Test server: Cloudflare"
            }
        }
    }

    private fun runDnsTest() {

        dnsText.text = "Comparing DNS servers..."

        runOnBackground(dnsButton) {

            val cloudflare = measureDns(
                "https://cloudflare-dns.com/dns-query?name=example.com&type=A"
            )

            val google = measureDns(
                "https://dns.google/resolve?name=example.com&type=A"
            )

            val result = buildString {

                append("Cloudflare DNS: ")

                append(
                    if (cloudflare >= 0) {
                        "$cloudflare ms"
                    } else {
                        "Failed"
                    }
                )

                append("\nGoogle DNS: ")

                append(
                    if (google >= 0) {
                        "$google ms"
                    } else {
                        "Failed"
                    }
                )

                append("\n\n")

                when {

                    cloudflare < 0 && google < 0 ->
                        append("Both DNS tests failed.")

                    cloudflare < 0 ->
                        append("Google DNS responded successfully.")

                    google < 0 ->
                        append("Cloudflare DNS responded successfully.")

                    cloudflare < google ->
                        append("Cloudflare had the faster response.")

                    google < cloudflare ->
                        append("Google had the faster response.")

                    else ->
                        append("Both DNS response times were similar.")
                }

                append(
                    "\n\nThis comparison does not change your device DNS."
                )
            }

            runOnUiThread {
                dnsText.text = result
            }
        }
    }

    private fun measureDns(endpoint: String): Long {

        val start = System.nanoTime()

        return try {

            val connection = URL(endpoint)
                .openConnection() as HttpURLConnection

            connection.connectTimeout = 7000
            connection.readTimeout = 7000

            connection.setRequestProperty(
                "Accept",
                "application/dns-json"
            )

            val response = connection.responseCode

            connection.disconnect()

            if (response in 200..299) {
                (System.nanoTime() - start) / 1_000_000
            } else {
                -1L
            }

        } catch (e: Exception) {
            -1L
        }
    }

    private fun runSmartOptimizer() {

        optimizerText.text = "Analyzing your network..."

        runOnBackground(optimizerButton) {

            val samples = mutableListOf<Long>()

            repeat(5) {
                val result = tcpTest()
                if (result >= 0) {
                    samples.add(result)
                }
            }

            val diagnosis = if (samples.isEmpty()) {

                "Network connection could not be tested.\n" +
                "Check your internet connection."

            } else {

                val average = samples.average()

                val jitter = if (samples.size > 1) {
                    samples.zipWithNext { a, b ->
                        abs(a - b).toDouble()
                    }.average()
                } else {
                    0.0
                }

                buildString {

                    append("Average TCP latency: ${format(average)} ms\n")
                    append("Jitter estimate: ${format(jitter)} ms\n\n")

                    when {

                        average < 100 && jitter < 20 -> {
                            append("Network condition: GOOD\n")
                            append("Your connection looks stable.")
                        }

                        average < 180 && jitter < 40 -> {
                            append("Network condition: MODERATE\n")
                            append("Try testing closer to your Wi-Fi router.")
                            append("\nAvoid heavy downloads while gaming.")
                        }

                        else -> {
                            append("Network condition: POOR\n")
                            append("Check signal strength and network congestion.")
                            append("\nTry another connection and compare results.")
                        }
                    }

                    append(
                        "\n\nNo system settings were changed automatically."
                    )
                }
            }

            runOnUiThread {
                optimizerText.text = diagnosis
            }
        }
    }

    private fun runTest() {

        if (!testRunning.compareAndSet(false, true)) {
            output.text = "Another test is running."
            return
        }

        runButton.isEnabled = false
        stabilityButton.isEnabled = false

        output.text = "Analyzing network..."

        executor.execute {

            try {

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
                    java.net.InetAddress.getAllByName("cloudflare.com")
                        .firstOrNull()?.hostAddress ?: "Resolved"
                } catch (e: Exception) {
                    "DNS lookup failed"
                }

                val dnsTime =
                    (System.nanoTime() - dnsStart) / 1_000_000

                val networkType = getNetworkType()
                val connected = hasInternetConnection()

                var average = 0.0
                var jitter = 0.0

                val report = if (samples.isEmpty()) {

                    """
                    NETWORK ANALYSIS FAILED

                    Network: $networkType
                    Internet: $connected
                    Could not reach Cloudflare.
                    DNS: $dnsAddress
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

                    saveHistory(average, jitter, networkType)

                    """
                    NETWORK ANALYSIS COMPLETE

                    Network: $networkType
                    Connection: $connected

                    TCP Latency: ${format(average)} ms
                    Best: ${sorted.first()} ms
                    Worst: ${sorted.last()} ms

                    Jitter: ${format(jitter)} ms
                    Failed Attempts: ${format(failed * 100.0 / 8)}%

                    DNS Time: $dnsTime ms
                    DNS Address: $dnsAddress

                    Gaming Mode: $gamingMode
                    COD Mode: $codMode

                    This is a TCP test to Cloudflare.
                    It is not actual game server ping.
                    """.trimIndent()
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
                }

            } catch (e: Exception) {

                runOnUiThread {
                    output.text = "Analysis failed: ${e.message}"
                }

            } finally {

                runOnUiThread {
                    runButton.isEnabled = true
                    stabilityButton.isEnabled = true
                    testRunning.set(false)
                }
            }
        }
    }

    private fun runStabilityTest() {

        stopMonitor()

        if (!testRunning.compareAndSet(false, true)) {
            output.text = "Another test is running."
            return
        }

        runButton.isEnabled = false
        stabilityButton.isEnabled = false

        executor.execute {

            try {

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
                            "STABILITY TEST\n" +
                            "Progress: ${index + 1}/20\n" +
                            "Successful: ${samples.size}\n" +
                            "Failed: $failed"

                        chart.updateSamples(samples.toList())
                    }
                }

                var average = 0.0
                var jitter = 0.0

                val report = if (samples.isEmpty()) {

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

                    saveHistory(average, jitter, getNetworkType())

                    """
                    STABILITY TEST COMPLETE

                    Total: 20
                    Successful: ${samples.size}
                    Failed: $failed

                    Average: ${format(average)} ms
                    Best: ${sorted.first()} ms
                    Worst: ${sorted.last()} ms

                    Jitter: ${format(jitter)} ms
                    Failure Rate: ${format(failed * 5.0)}%

                    ${getSmartDiagnosis(average, jitter)}

                    TCP measurements to Cloudflare.
                    """.trimIndent()
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
                }

            } catch (e: Exception) {

                runOnUiThread {
                    output.text = "Stability test failed: ${e.message}"
                }

            } finally {

                runOnUiThread {
                    runButton.isEnabled = true
                    stabilityButton.isEnabled = true
                    testRunning.set(false)
                }
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

            average < 100 && jitter < 20 -> "GOOD"

            average < 180 && jitter < 40 -> "MODERATE"

            else -> "POOR"
        }

        qualityText.text = "NETWORK QUALITY: $quality"

        qualityText.setTextColor(
            when (quality) {
                "GOOD" -> green
                "MODERATE" -> Color.YELLOW
                else -> Color.RED
            }
        )
    }

    private fun toggleMonitor() {

        monitorEnabled = !monitorEnabled

        if (monitorEnabled) {

            monitorSamples.clear()

            monitorButton.text = "STOP LIVE MONITOR"

            monitorText.text =
                "Live monitoring started.\nEvery 3 seconds."

            handler.post(monitorRunnable)

        } else {
            stopMonitor()
            monitorText.text = "Live monitoring stopped."
        }
    }

    private val monitorRunnable = object : Runnable {

        override fun run() {

            if (!monitorEnabled) return

            if (testRunning.compareAndSet(false, true)) {

                executor.execute {

                    try {

                        val result = tcpTest()

                        runOnUiThread {

                            if (monitorEnabled) {

                                if (result >= 0) {

                                    monitorSamples.add(result)

                                    if (monitorSamples.size > 20) {
                                        monitorSamples.removeAt(0)
                                    }

                                    monitorText.text =
                                        "LIVE MONITOR\n" +
                                        "Latency: $result ms\n" +
                                        "Samples: ${monitorSamples.size}"

                                    chart.updateSamples(
                                        monitorSamples.toList()
                                    )

                                } else {
                                    monitorText.text =
                                        "Connection test failed."
                                }
                            }
                        }

                    } finally {
                        testRunning.set(false)
                    }
                }
            }

            handler.postDelayed(this, 3000)
        }
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

        output.text =
            if (codMode) {
                "COD Mobile mode enabled.\n" +
                "Tests still use Cloudflare."
            } else {
                "Standard network testing mode."
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
                object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        runOnUiThread {
                            recoveryText.text =
                                "Network is available.\nConnection restored or available."
                            showNetworkStatus()
                        }
                    }

                    override fun onLost(network: Network) {
                        runOnUiThread {
                            recoveryText.text =
                                "Network connection lost.\nWaiting for reconnection..."
                            showNetworkStatus()
                        }
                    }
                }
            )

            callbackRegistered = true

        } catch (e: Exception) {

            recoveryText.text = "Network monitoring unavailable."
        }
    }

    private fun saveHistory(
        average: Double,
        jitter: Double,
        networkType: String
    ) {

        val history = try {
            JSONArray(preferences.getString("tests", "[]"))
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
            updated.put(history.getJSONObject(i))
        }

        preferences.edit()
            .putString("tests", updated.toString())
            .apply()
    }

    private fun loadHistory() {

        val history = try {
            JSONArray(preferences.getString("tests", "[]"))
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

            try {

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

            } catch (e: Exception) {
                // Ignore invalid history entries.
            }
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
        return String.format(Locale.US, "%.1f", value)
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

        val left = dp(15f)
        val right = chartWidth - dp(15f)
        val top = dp(45f)
        val bottom = chartHeight - dp(25f)

        paint.color = Color.LTGRAY
        paint.strokeWidth = dp(1f)

        canvas.drawLine(left, bottom, right, bottom, paint)
        canvas.drawLine(left, top, left, bottom, paint)

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
                (value.toFloat() / maxValue.toFloat()) *
                (bottom - top)

            x to y
        }

        paint.color = Color.rgb(30, 140, 255)
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

        paint.color = Color.rgb(30, 210, 130)

        for (point in points) {
            canvas.drawCircle(
                point.first,
                point.second,
                dp(4f),
                paint
            )
        }

        paint.color = Color.WHITE
        paint.textSize = dp(13f)

        canvas.drawText(
            "TCP Latency: $minValue - $maxValue ms",
            left,
            dp(25f),
            paint
        )
    }
}
