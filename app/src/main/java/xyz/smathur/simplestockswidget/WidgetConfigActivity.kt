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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import xyz.smathur.simplestockswidget.ui.theme.SimpleStocksWidgetTheme

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
                        onSave = { symbol, selectedApp, isDarkTheme ->
                            saveWidgetConfig(symbol, selectedApp, isDarkTheme)
                            finishWithSuccess()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun saveWidgetConfig(symbol: String, selectedApp: AppInfo, isDarkTheme: Boolean) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("symbol_$appWidgetId", symbol)
            putString("launch_app_$appWidgetId", selectedApp.packageName)
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
    onSave: (String, AppInfo, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)

    // Load existing settings if they exist
    val existingSymbol = prefs.getString("symbol_$appWidgetId", "AAPL") ?: "AAPL"
    val existingLaunchApp = prefs.getString("launch_app_$appWidgetId", context.packageName) ?: context.packageName
    val existingTheme = prefs.getBoolean("theme_$appWidgetId", true) // Default to dark

    var symbol by remember { mutableStateOf(existingSymbol) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var isDarkTheme by remember { mutableStateOf(existingTheme) }
    var showAppSelector by remember { mutableStateOf(false) }
    var availableApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Load available apps and set current selection
    LaunchedEffect(Unit) {
        availableApps = AppUtils.getInstalledApps(context)
        selectedApp = availableApps.find { it.packageName == existingLaunchApp }
            ?: availableApps.firstOrNull()
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

        // App Selection
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
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                    selectedApp?.let { app ->
                        onSave(symbol, app, isDarkTheme)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = symbol.isNotBlank() && selectedApp != null
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
}