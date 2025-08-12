package xyz.smathur.simplestockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class SmallStockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val symbol = prefs.getString("small_symbol_$appWidgetId", "SPY") ?: "SPY"
            val launchApp = prefs.getString("small_launch_app_$appWidgetId", context.packageName) ?: context.packageName

            // Get stock data from cache
            val stockData = StockDataCache.getStockData(symbol)

            val views = RemoteViews(context.packageName, R.layout.widget_1x1_layout)

            // Update widget content - prioritize symbol and price for 1x1
            views.setTextViewText(R.id.small_symbol_text, stockData.symbol)
            views.setTextViewText(R.id.small_price_text, "$${String.format("%.0f", stockData.price)}")

            val percentText = if (stockData.percentChange >= 0) {
                "+${String.format("%.1f", stockData.percentChange)}%"
            } else {
                "${String.format("%.1f", stockData.percentChange)}%"
            }
            views.setTextViewText(R.id.small_percent_text, percentText)

            // Set colors
            val color = if (stockData.change >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            views.setTextColor(R.id.small_percent_text, color)

            // Set up click intent to launch selected app
            val pendingIntent = createLaunchIntent(context, appWidgetId, launchApp)
            views.setOnClickPendingIntent(R.id.small_widget_container, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createLaunchIntent(context: Context, appWidgetId: Int, packageName: String): PendingIntent {
            return if (packageName == context.packageName) {
                // Launch our own main activity (default behavior)
                val intent = Intent(context, MainActivity::class.java)
                intent.putExtra("smallWidgetId", appWidgetId)
                PendingIntent.getActivity(
                    context, appWidgetId + 1000, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                // Launch selected app
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    PendingIntent.getActivity(
                        context, appWidgetId + 1000, launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    // Fallback to main activity if selected app not found
                    val intent = Intent(context, MainActivity::class.java)
                    intent.putExtra("smallWidgetId", appWidgetId)
                    PendingIntent.getActivity(
                        context, appWidgetId + 1000, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            }
        }
    }
}