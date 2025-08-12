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

class SmallWidgetConfigActivity : ComponentActivity() {
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
                    SmallWidgetConfigScreen(
                        onSave = { symbol, selectedApp ->
                            saveSmallWidgetConfig(symbol, selectedApp)
                            finishWithSuccess()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun saveSmallWidgetConfig(symbol: String, selectedApp: AppInfo) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("small_symbol_$appWidgetId", symbol)
            putString("small_launch_app_$appWidgetId", selectedApp.packageName)
            apply()
        }

        val appWidgetManager = AppWidgetManager.getInstance(this)
        SmallStockWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
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
fun SmallWidgetConfigScreen(
    onSave: (String, AppInfo) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var symbol by remember { mutableStateOf("SPY") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAppSelector by remember { mutableStateOf(false) }
    var availableApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Load available apps
    LaunchedEffect(Unit) {
        availableApps = AppUtils.getInstalledApps(context)
        // Set default selection to first app (Simple Stocks Widget)
        selectedApp = availableApps.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Configure 1×1 Widget",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Stock Symbol Input
        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Stock Symbol") },
            placeholder = { Text("e.g., SPY, AAPL, QQQ") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
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

                // Selected app display
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

        Text(
            text = "Small widget shows: Symbol, Price, % Change\nKeep symbols short for best fit",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

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
                        onSave(symbol, app)
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