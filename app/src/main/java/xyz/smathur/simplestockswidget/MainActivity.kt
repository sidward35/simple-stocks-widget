package xyz.smathur.simplestockswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import xyz.smathur.simplestockswidget.ui.theme.SimpleStocksWidgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleStocksWidgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    var apiKey by remember {
        mutableStateOf(prefs.getString("finnhub_api_key", "") ?: "")
    }

    var updateInterval by remember {
        mutableStateOf(prefs.getInt("update_interval", 15).toString()) // Changed default to 15
    }

    var testApiCall by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }

    // Debug info state
    var debugInfo by remember { mutableStateOf("Loading...") }

    // Validation state for update interval
    val updateIntervalInt = updateInterval.toIntOrNull() ?: 0
    val isUpdateIntervalValid = updateIntervalInt >= 15
    val updateIntervalError = if (updateInterval.isNotEmpty() && !isUpdateIntervalValid) {
        "Minimum 15 minutes required"
    } else null

    // Function to refresh debug info
    fun refreshDebugInfo() {
        val lastWidgetUpdate = prefs.getLong("last_widget_update", 0L)
        val lastSuccessfulUpdate = prefs.getLong("last_successful_update", 0L)
        val lastUpdateAttempt = prefs.getLong("last_update_attempt", 0L)

        val dateFormat = java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.getDefault())

        debugInfo = buildString {
            appendLine(
                "Last widget update: ${
                    if (lastWidgetUpdate == 0L) "Never" else dateFormat.format(
                        java.util.Date(lastWidgetUpdate)
                    )
                }"
            )
            appendLine(
                "Last successful API call: ${
                    if (lastSuccessfulUpdate == 0L) "Never" else dateFormat.format(
                        java.util.Date(lastSuccessfulUpdate)
                    )
                }"
            )
            appendLine(
                "Last update attempt: ${
                    if (lastUpdateAttempt == 0L) "Never" else dateFormat.format(
                        java.util.Date(lastUpdateAttempt)
                    )
                }"
            )

            // Show active symbols
            val activeSymbols = StockDataCache.getActiveSymbols(context)
            if (activeSymbols.isNotEmpty()) {
                appendLine("Active symbols: ${activeSymbols.joinToString(", ")}")

                // Show cached data timestamps
                activeSymbols.forEach { symbol ->
                    val stockData = StockDataCache.getStockData(symbol, context)
                    val cacheTime =
                        if (stockData.lastUpdated == 0L) "Default data" else dateFormat.format(
                            java.util.Date(stockData.lastUpdated)
                        )
                    appendLine("$symbol cache: $cacheTime")
                }
            } else {
                appendLine("No active widgets found")
            }

            // Show cache file info
            val cachePrefs =
                context.getSharedPreferences("stock_cache", android.content.Context.MODE_PRIVATE)
            val cacheSavedAt = cachePrefs.getLong("cache_saved_at", 0L)
            if (cacheSavedAt > 0L) {
                val cacheAge = (System.currentTimeMillis() - cacheSavedAt) / (1000 * 60)
                appendLine("Cache file age: ${cacheAge}min")
            } else {
                appendLine("No cache file found")
            }
        }
    }

    // Load debug info on startup
    LaunchedEffect(Unit) {
        refreshDebugInfo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Simple Stocks Widget",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Widget Settings",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Finnhub API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your API key") },
                        singleLine = true
                    )

                    Text(
                        text = "Leave empty to use demo data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = updateInterval,
                        onValueChange = { updateInterval = it },
                        label = { Text("Update Interval (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = updateIntervalError != null,
                        supportingText = if (updateIntervalError != null) {
                            { Text(updateIntervalError, color = MaterialTheme.colorScheme.error) }
                        } else null
                    )

                    Text(
                        text = "Market hours: 9:30 AM - 4:00 PM ET (Mon-Fri)\nMinimum interval: 15 minutes (WorkManager limitation)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            with(prefs.edit()) {
                                putString("finnhub_api_key", apiKey)
                                putInt("update_interval", updateIntervalInt)
                                apply()
                            }

                            // Cancel old WorkManager and schedule new with updated interval
                            StockUpdateWorker.cancelUpdate(context)
                            StockUpdateWorker.scheduleUpdate(context)

                            // Trigger immediate update
                            StockUpdateWorker.triggerImmediateUpdate(context)

                            // Test API
                            testApiCall = true
                            testResult = "Starting API test..."

                            scope.launch {
                                try {
                                    testResult = "Step 1: Starting API call..."

                                    withContext(Dispatchers.IO) {
                                        val url = "https://finnhub.io/api/v1/quote?symbol=SPY"
                                        testResult = "Step 2: URL created: $url"

                                        val connection = java.net.URL(url)
                                            .openConnection() as java.net.HttpURLConnection
                                        testResult = "Step 3: Connection opened"

                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 5000
                                        connection.readTimeout = 5000
                                        connection.setRequestProperty("X-Finnhub-Token", apiKey)
                                        testResult = "Step 4: Headers set, connecting..."

                                        val responseCode = connection.responseCode
                                        testResult = "Step 5: Got response code: $responseCode"

                                        if (responseCode == 200) {
                                            val response =
                                                connection.inputStream.bufferedReader().readText()
                                            testResult =
                                                "Step 6: Response: ${response.take(100)}..."

                                            val json = org.json.JSONObject(response)
                                            val price = json.optDouble("c", -1.0)
                                            val change = json.optDouble("d", 0.0)
                                            val percentChange = json.optDouble("dp", 0.0)

                                            if (price > 0) {
                                                val changeStr =
                                                    if (change >= 0) "+${"%.2f".format(change)}" else "-${
                                                        "%.2f".format(Math.abs(change))
                                                    }"
                                                val percentStr = if (percentChange >= 0) "+${
                                                    "%.1f".format(percentChange)
                                                }%" else "${"%.1f".format(percentChange)}%"

                                                testResult =
                                                    "✅ SPY: ${"%.2f".format(price)} $changeStr ($percentStr)"

                                                // Update successful API call timestamp
                                                with(prefs.edit()) {
                                                    putLong(
                                                        "last_successful_update",
                                                        System.currentTimeMillis()
                                                    )
                                                    apply()
                                                }
                                            } else {
                                                testResult = "❌ Invalid price data: $response"
                                            }
                                        } else {
                                            val errorStream =
                                                connection.errorStream?.bufferedReader()?.readText()
                                                    ?: "No error details"
                                            testResult = "❌ HTTP $responseCode: $errorStream"
                                        }
                                    }
                                } catch (e: Exception) {
                                    testResult =
                                        "❌ Exception: ${e.javaClass.simpleName}: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        enabled = isUpdateIntervalValid // Disable button if validation fails
                    ) {
                        Text("Save Settings & Update Widgets")
                    }
                }
            }

            // API Test Result Card
            if (testApiCall) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "API Test Result",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = testResult,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Debug Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Debug Info",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = debugInfo,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { refreshDebugInfo() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Refresh")
                        }

                        OutlinedButton(
                            onClick = {
                                // Force reload cache from storage
                                StockDataCache.reloadFromStorage(context)
                                refreshDebugInfo()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reload Cache")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to Use",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "1. Long press on your home screen\n" +
                                "2. Select 'Widgets'\n" +
                                "3. Find 'Simple Stocks Widget'\n" +
                                "4. Choose 2×1 or 1×1 size\n" +
                                "5. Configure the stock symbol\n" +
                                "\nSupports: AAPL, SPY, TSLA, NVDA, etc.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SimpleStocksWidgetTheme {
        SettingsScreen()
    }
}