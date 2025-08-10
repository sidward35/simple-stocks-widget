package xyz.smathur.simplestockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
            val color = if (stockData.change >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            views.setTextColor(R.id.change_text, color)
            views.setTextColor(R.id.percent_text, color)

            // Set up click intent to open settings
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("widgetId", appWidgetId)
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}