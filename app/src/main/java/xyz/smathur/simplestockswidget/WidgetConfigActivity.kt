package xyz.smathur.simplestockswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import xyz.smathur.simplestockswidget.ui.theme.SimpleStocksWidgetTheme

// Data class for URL templates
data class UrlTemplate(
    val name: String,
    val template: String, // Use {SYMBOL} as placeholder
    val icon: String? = null
) {
    fun buildUrl(symbol: String): String = template.replace("{SYMBOL}", symbol)
}

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED initially
        setResult(Activity.RESULT_CANCELED)

        // Get the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If the intent doesn't have a valid widget ID, finish
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            SimpleStocksWidgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigScreen(
                        appWidgetId = appWidgetId,
                        onSave = { symbol, selectedApp, isDarkTheme, urlTemplate ->
                            saveWidgetConfig(symbol, selectedApp, isDarkTheme, urlTemplate)
                            finishWithSuccess()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun saveWidgetConfig(symbol: String, selectedApp: AppInfo?, isDarkTheme: Boolean, urlTemplate: UrlTemplate?) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("symbol_$appWidgetId", symbol)

            // Save either app or URL preference
            if (selectedApp != null) {
                putString("launch_app_$appWidgetId", selectedApp.packageName)
                remove("launch_url_$appWidgetId") // Clear URL if app is selected
            } else if (urlTemplate != null) {
                putString("launch_url_$appWidgetId", urlTemplate.template)
                remove("launch_app_$appWidgetId") // Clear app if URL is selected
            }

            putBoolean("theme_$appWidgetId", isDarkTheme)
            apply()
        }

        // Update the widget immediately
        val appWidgetManager = AppWidgetManager.getInstance(this)
        StockWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
    }

    private fun finishWithSuccess() {
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    appWidgetId: Int,
    onSave: (String, AppInfo?, Boolean, UrlTemplate?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)

    // Predefined URL templates for popular finance sites
    val urlTemplates = listOf(
        UrlTemplate("Yahoo Finance", "https://finance.yahoo.com/quote/{SYMBOL}"),
        UrlTemplate("Google Finance", "https://www.google.com/finance/quote/{SYMBOL}"),
        UrlTemplate("TradingView", "https://www.tradingview.com/symbols/{SYMBOL}/"),
        UrlTemplate("Webull", "https://www.webull.com/quote/{SYMBOL}"),
        UrlTemplate("Finviz", "https://finviz.com/quote.ashx?t={SYMBOL}"),
        UrlTemplate("MarketWatch", "https://www.marketwatch.com/investing/stock/{SYMBOL}"),
        UrlTemplate("Seeking Alpha", "https://seekingalpha.com/symbol/{SYMBOL}"),
        UrlTemplate("Bloomberg", "https://www.bloomberg.com/quote/{SYMBOL}:US")
    )

    // Load existing settings if they exist
    val existingSymbol = prefs.getString("symbol_$appWidgetId", "AAPL") ?: "AAPL"
    val existingLaunchApp = prefs.getString("launch_app_$appWidgetId", null)
    val existingLaunchUrl = prefs.getString("launch_url_$appWidgetId", null)
    val existingTheme = prefs.getBoolean("theme_$appWidgetId", true) // Default to dark

    var symbol by remember { mutableStateOf(existingSymbol) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedUrlTemplate by remember { mutableStateOf<UrlTemplate?>(null) }
    var isDarkTheme by remember { mutableStateOf(existingTheme) }
    var isUrlMode by remember { mutableStateOf(existingLaunchUrl != null) }

    // Add these new variables to remember last selections
    var lastSelectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var lastSelectedUrlTemplate by remember { mutableStateOf<UrlTemplate?>(null) }

    var showAppSelector by remember { mutableStateOf(false) }
    var showUrlSelector by remember { mutableStateOf(false) }
    var availableApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Load available apps and set current selection
    LaunchedEffect(Unit) {
        availableApps = AppUtils.getInstalledApps(context)

        if (existingLaunchApp != null) {
            selectedApp = availableApps.find { it.packageName == existingLaunchApp }
                ?: availableApps.firstOrNull()
            lastSelectedApp = selectedApp // Remember this selection
        }

        if (existingLaunchUrl != null) {
            selectedUrlTemplate = urlTemplates.find { it.template == existingLaunchUrl }
                ?: urlTemplates.firstOrNull()
            lastSelectedUrlTemplate = selectedUrlTemplate // Remember this selection
        }

        // If neither exists, default to app mode with first app
        if (existingLaunchApp == null && existingLaunchUrl == null) {
            selectedApp = availableApps.firstOrNull()
            lastSelectedApp = selectedApp // Remember this default
            isUrlMode = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Configure 2×1 Widget",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Stock Symbol Input
        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Stock Symbol") },
            placeholder = { Text("e.g., AAPL, SPY, TSLA") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        Text(
            text = "Examples:\n• AAPL (Apple stock)\n• SPY (S&P 500 ETF)\n• TSLA (Tesla stock)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Tap Action Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Tap Action",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Toggle between App and URL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        onClick = {
                            isUrlMode = false
                            // Restore last selected app, or use first available if none
                            selectedApp = lastSelectedApp ?: availableApps.firstOrNull()
                        },
                        label = { Text("Launch App") },
                        selected = !isUrlMode,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilterChip(
                        onClick = {
                            isUrlMode = true
                            // Restore last selected URL template, or use first available if none
                            selectedUrlTemplate = lastSelectedUrlTemplate ?: urlTemplates.firstOrNull()
                        },
                        label = { Text("Open Website") },
                        selected = isUrlMode,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show either App or URL selection
                if (isUrlMode) {
                    // URL Selection
                    selectedUrlTemplate?.let { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showUrlSelector = true }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Website",
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Opens ${template.buildUrl(symbol.ifBlank { "AAPL" })}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text("Change", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    // App Selection
                    selectedApp?.let { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAppSelector = true }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Tap widget to open this app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text("Change", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Widget Appearance
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Widget Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Theme Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isDarkTheme) "Dark background" else "Light background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { isDarkTheme = it }
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    if (isUrlMode) {
                        onSave(symbol, null, isDarkTheme, selectedUrlTemplate)
                    } else {
                        onSave(symbol, selectedApp, isDarkTheme, null)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = symbol.isNotBlank() &&
                        ((isUrlMode && selectedUrlTemplate != null) ||
                                (!isUrlMode && selectedApp != null))
            ) {
                Text("Save")
            }
        }
    }

    // App Selection Dialog
    if (showAppSelector && availableApps.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAppSelector = false },
            title = { Text("Select App to Launch") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(availableApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedApp = app
                                    lastSelectedApp = app // Remember this selection
                                    showAppSelector = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 12.dp)
                            )

                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // URL Selection Dialog
    if (showUrlSelector) {
        AlertDialog(
            onDismissRequest = { showUrlSelector = false },
            title = { Text("Select Finance Website") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(urlTemplates) { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUrlTemplate = template
                                    lastSelectedUrlTemplate = template // Remember this selection
                                    showUrlSelector = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Website",
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 12.dp)
                            )

                            Column {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = template.buildUrl(symbol.ifBlank { "AAPL" }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUrlSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}