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

// Base config activity that handles both widget sizes
abstract class BaseWidgetConfigActivity : ComponentActivity() {

    enum class WidgetType {
        NORMAL, SMALL
    }

    abstract val widgetType: WidgetType

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

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
                        widgetType = widgetType,
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
        val config = getWidgetConfig(widgetType)
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)

        with(prefs.edit()) {
            putString("${config.prefixKey}symbol_$appWidgetId", symbol)

            // Save either app or URL preference
            if (selectedApp != null) {
                putString("${config.prefixKey}launch_app_$appWidgetId", selectedApp.packageName)
                remove("${config.prefixKey}launch_url_$appWidgetId")
            } else if (urlTemplate != null) {
                putString("${config.prefixKey}launch_url_$appWidgetId", urlTemplate.template)
                remove("${config.prefixKey}launch_app_$appWidgetId")
            }

            putBoolean("${config.prefixKey}theme_$appWidgetId", isDarkTheme)
            apply()
        }

        // Update the appropriate widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetSize = when (widgetType) {
            WidgetType.NORMAL -> BaseStockWidgetProvider.WidgetSize.NORMAL
            WidgetType.SMALL -> BaseStockWidgetProvider.WidgetSize.SMALL
        }
        BaseStockWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId, widgetSize)
    }

    private fun finishWithSuccess() {
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    private fun getWidgetConfig(widgetType: WidgetType): WidgetConfigData {
        return when (widgetType) {
            WidgetType.NORMAL -> WidgetConfigData(
                prefixKey = "",
                defaultSymbol = "AAPL",
                title = "Configure 2×1 Widget"
            )
            WidgetType.SMALL -> WidgetConfigData(
                prefixKey = "small_",
                defaultSymbol = "SPY",
                title = "Configure 1×1 Widget"
            )
        }
    }

    private data class WidgetConfigData(
        val prefixKey: String,
        val defaultSymbol: String,
        val title: String
    )
}

// Concrete implementations - these are what the widget info XMLs reference
class WidgetConfigActivity : BaseWidgetConfigActivity() {
    override val widgetType = WidgetType.NORMAL
}

class SmallWidgetConfigActivity : BaseWidgetConfigActivity() {
    override val widgetType = WidgetType.SMALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    appWidgetId: Int,
    widgetType: BaseWidgetConfigActivity.WidgetType,
    onSave: (String, AppInfo?, Boolean, UrlTemplate?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)

    // Get widget-specific configuration
    val config = when (widgetType) {
        BaseWidgetConfigActivity.WidgetType.NORMAL -> WidgetConfigData(
            prefixKey = "",
            defaultSymbol = "AAPL",
            title = "Configure 2×1 Widget"
        )
        BaseWidgetConfigActivity.WidgetType.SMALL -> WidgetConfigData(
            prefixKey = "small_",
            defaultSymbol = "SPY",
            title = "Configure 1×1 Widget"
        )
    }

    // Predefined URL templates for popular finance sites
    val urlTemplates = listOf(
        UrlTemplate("Yahoo Finance", "https://finance.yahoo.com/quote/{SYMBOL}"),
        UrlTemplate("TradingView", "https://www.tradingview.com/symbols/{SYMBOL}/"),
        UrlTemplate("Finviz", "https://finviz.com/quote.ashx?t={SYMBOL}"),
        UrlTemplate("MarketWatch", "https://www.marketwatch.com/investing/stock/{SYMBOL}"),
        UrlTemplate("Seeking Alpha", "https://seekingalpha.com/symbol/{SYMBOL}"),
        UrlTemplate("Bloomberg", "https://www.bloomberg.com/quote/{SYMBOL}:US"),
        UrlTemplate("Google Finance", "https://www.google.com/finance/quote/{SYMBOL}")
    )

    // Load existing settings
    val existingSymbol = prefs.getString("${config.prefixKey}symbol_$appWidgetId", config.defaultSymbol) ?: config.defaultSymbol
    val existingLaunchApp = prefs.getString("${config.prefixKey}launch_app_$appWidgetId", null)
    val existingLaunchUrl = prefs.getString("${config.prefixKey}launch_url_$appWidgetId", null)
    val existingTheme = prefs.getBoolean("${config.prefixKey}theme_$appWidgetId", true)

    var symbol by remember { mutableStateOf(existingSymbol) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedUrlTemplate by remember { mutableStateOf<UrlTemplate?>(null) }
    var isDarkTheme by remember { mutableStateOf(existingTheme) }
    var isUrlMode by remember { mutableStateOf(existingLaunchUrl != null) }

    // Selection memory variables
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
            lastSelectedApp = selectedApp
        }

        if (existingLaunchUrl != null) {
            selectedUrlTemplate = urlTemplates.find { it.template == existingLaunchUrl }
                ?: urlTemplates.firstOrNull()
            lastSelectedUrlTemplate = selectedUrlTemplate
        }

        // Default to app mode if neither exists
        if (existingLaunchApp == null && existingLaunchUrl == null) {
            selectedApp = availableApps.firstOrNull()
            lastSelectedApp = selectedApp
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
            text = config.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Stock Symbol Input
        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Stock Symbol") },
            placeholder = { Text("e.g., ${config.defaultSymbol}, SPY, TSLA") },
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
                                    text = "Opens ${template.buildUrl(symbol.ifBlank { config.defaultSymbol })}",
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
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(availableApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedApp = app
                                    lastSelectedApp = app
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
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(urlTemplates) { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUrlTemplate = template
                                    lastSelectedUrlTemplate = template
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
                                    text = template.buildUrl(symbol.ifBlank { config.defaultSymbol }),
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

private data class WidgetConfigData(
    val prefixKey: String,
    val defaultSymbol: String,
    val title: String
)