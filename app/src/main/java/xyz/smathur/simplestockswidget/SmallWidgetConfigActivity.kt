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
import androidx.compose.ui.unit.dp
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
                        onSave = { symbol ->
                            saveSmallWidgetConfig(symbol)
                            finishWithSuccess()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun saveSmallWidgetConfig(symbol: String) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("small_symbol_$appWidgetId", symbol)
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
            text = "Configure 1×1 Widget",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Stock Symbol") },
            placeholder = { Text("e.g., SPY, QQQ, TSLA") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

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
                onClick = { onSave(symbol) },
                modifier = Modifier.weight(1f),
                enabled = symbol.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}