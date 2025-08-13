package xyz.smathur.simplestockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews

class StockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Start the update service
        StockUpdateService.scheduleUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Stop the update service
        StockUpdateService.cancelUpdate(context)
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val symbol = prefs.getString("symbol_$appWidgetId", "AAPL") ?: "AAPL"
            val launchApp = prefs.getString("launch_app_$appWidgetId", null)
            val launchUrl = prefs.getString("launch_url_$appWidgetId", null)

            // Get stock data from cache or default values
            val stockData = StockDataCache.getStockData(symbol)

            val views = RemoteViews(context.packageName, R.layout.widget_2x1_layout)

            // Update widget content
            views.setTextViewText(R.id.symbol_text, stockData.symbol)
            views.setTextViewText(R.id.price_text, "$${String.format("%.2f", stockData.price)}")

            val changeText = if (stockData.change >= 0) {
                "+$${String.format("%.2f", stockData.change)}"
            } else {
                "-$${String.format("%.2f", Math.abs(stockData.change))}"
            }
            views.setTextViewText(R.id.change_text, changeText)

            val percentText = if (stockData.percentChange >= 0) {
                "+${String.format("%.1f", stockData.percentChange)}%"
            } else {
                "${String.format("%.1f", stockData.percentChange)}%"
            }
            views.setTextViewText(R.id.percent_text, percentText)

            // Set colors based on positive/negative change
            val changeColor = if (stockData.change >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            views.setTextColor(R.id.change_text, changeColor)
            views.setTextColor(R.id.percent_text, changeColor)

            // Apply theme (no opacity parameter needed)
            WidgetStyleHelper.applyWidgetTheme(context, views, appWidgetId, false)

            // Set up click intent - either app or URL
            val pendingIntent = createClickIntent(context, appWidgetId, symbol, launchApp, launchUrl)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createClickIntent(
            context: Context,
            appWidgetId: Int,
            symbol: String,
            launchApp: String?,
            launchUrl: String?
        ): PendingIntent {
            return when {
                launchUrl != null -> {
                    // Create URL intent - replace {SYMBOL} placeholder with actual symbol
                    val url = launchUrl.replace("{SYMBOL}", symbol)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                launchApp != null -> {
                    // Launch selected app
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(launchApp)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        PendingIntent.getActivity(
                            context, appWidgetId, launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    } else {
                        // Fallback to main activity if selected app not found
                        createFallbackIntent(context, appWidgetId)
                    }
                }

                else -> {
                    // Default fallback behavior - launch our main activity
                    createFallbackIntent(context, appWidgetId)
                }
            }
        }

        private fun createFallbackIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("widgetId", appWidgetId)
            }
            return PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}