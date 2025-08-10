package xyz.smathur.simplestockswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                        onSave = { symbol ->
                            saveWidgetConfig(symbol)
                            finishWithSuccess()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun saveWidgetConfig(symbol: String) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("symbol_$appWidgetId", symbol)
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
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var symbol by remember { mutableStateOf("SPY") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Configure Widget",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

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
                onClick = { onSave(symbol) },
                modifier = Modifier.weight(1f),
                enabled = symbol.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}