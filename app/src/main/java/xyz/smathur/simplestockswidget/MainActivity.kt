package xyz.smathur.simplestockswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
        mutableStateOf(prefs.getInt("update_interval", 5).toString())
    }

    var testApiCall by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding(),
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
                    singleLine = true
                )

                Button(
                    onClick = {
                        with(prefs.edit()) {
                            putString("finnhub_api_key", apiKey)
                            putInt("update_interval", updateInterval.toIntOrNull() ?: 5)
                            apply()
                        }

                        // Restart the update service with new interval
                        StockUpdateService.cancelUpdate(context)
                        StockUpdateService.scheduleUpdate(context)

                        // Trigger immediate widget update (forced)
                        val intent = android.content.Intent(context, StockUpdateService::class.java)
                        intent.action = "xyz.smathur.simplestockswidget.UPDATE_STOCKS"
                        intent.putExtra("force_update", true)
                        context.sendBroadcast(intent)

                        // Test API
                        testApiCall = true
                        testResult = "Starting API test..."

                        scope.launch {
                            try {
                                testResult = "Step 1: Starting API call..."

                                withContext(Dispatchers.IO) {
                                    val url = "https://finnhub.io/api/v1/quote?symbol=SPY"
                                    testResult = "Step 2: URL created: $url"

                                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                    testResult = "Step 3: Connection opened"

                                    connection.requestMethod = "GET"
                                    connection.connectTimeout = 5000
                                    connection.readTimeout = 5000
                                    connection.setRequestProperty("X-Finnhub-Token", apiKey)
                                    testResult = "Step 4: Headers set, connecting..."

                                    val responseCode = connection.responseCode
                                    testResult = "Step 5: Got response code: $responseCode"

                                    if (responseCode == 200) {
                                        val response = connection.inputStream.bufferedReader().readText()
                                        testResult = "Step 6: Response: ${response.take(100)}..."

                                        val json = org.json.JSONObject(response)
                                        val price = json.optDouble("c", -1.0)
                                        val change = json.optDouble("d", 0.0)
                                        val percentChange = json.optDouble("dp", 0.0)

                                        if (price > 0) {
                                            val changeStr = if (change >= 0) "+${"%.2f".format(change)}" else "-${"%.2f".format(Math.abs(change))}"
                                            val percentStr = if (percentChange >= 0) "+${"%.1f".format(percentChange)}%" else "${"%.1f".format(percentChange)}%"

                                            testResult = "✅ SPY: ${"%.2f".format(price)} $changeStr ($percentStr)"
                                        } else {
                                            testResult = "❌ Invalid price data: $response"
                                        }
                                    } else {
                                        val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "No error details"
                                        testResult = "❌ HTTP $responseCode: $errorStream"
                                    }
                                }
                            } catch (e: Exception) {
                                testResult = "❌ Exception: ${e.javaClass.simpleName}: ${e.localizedMessage}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
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
                    .padding(top = 16.dp)
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SimpleStocksWidgetTheme {
        SettingsScreen()
    }
}