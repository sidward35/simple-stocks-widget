package xyz.smathur.simplestockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews

// Base widget provider that handles both sizes
abstract class BaseStockWidgetProvider : AppWidgetProvider() {

    enum class WidgetSize {
        NORMAL, SMALL
    }

    abstract val widgetSize: WidgetSize

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, widgetSize)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Start WorkManager (only for normal widgets to avoid duplicate scheduling)
        if (widgetSize == WidgetSize.NORMAL) {
            android.util.Log.d("StockWidget", "Scheduling WorkManager from onEnabled")
            StockUpdateWorker.scheduleUpdate(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Stop WorkManager (only for normal widgets)
        if (widgetSize == WidgetSize.NORMAL) {
            android.util.Log.d("StockWidget", "Canceling WorkManager from onDisabled")
            StockUpdateWorker.cancelUpdate(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Clean up preferences for deleted widgets
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val config = getWidgetConfig(widgetSize)

        with(prefs.edit()) {
            appWidgetIds.forEach { widgetId ->
                remove("${config.prefixKey}symbol_$widgetId")
                remove("${config.prefixKey}launch_app_$widgetId")
                remove("${config.prefixKey}launch_url_$widgetId")
                remove("${config.prefixKey}theme_$widgetId")
            }
            apply()
        }

        android.util.Log.d("StockWidget", "Cleaned up preferences for deleted widgets: ${appWidgetIds.contentToString()}")
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            widgetSize: WidgetSize
        ) {
            val config = getWidgetConfig(widgetSize)
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

            val symbol = prefs.getString("${config.prefixKey}symbol_$appWidgetId", config.defaultSymbol) ?: config.defaultSymbol
            val launchApp = prefs.getString("${config.prefixKey}launch_app_$appWidgetId", null)
            val launchUrl = prefs.getString("${config.prefixKey}launch_url_$appWidgetId", null)

            // Get stock data from cache (pass context for persistence)
            val stockData = StockDataCache.getStockData(symbol, context)

            val views = RemoteViews(context.packageName, config.layoutRes)

            when (widgetSize) {
                WidgetSize.NORMAL -> {
                    // 2x1 widget layout
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

                    // Set colors
                    val changeColor = if (stockData.change >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                    views.setTextColor(R.id.change_text, changeColor)
                    views.setTextColor(R.id.percent_text, changeColor)
                }

                WidgetSize.SMALL -> {
                    // 1x1 widget layout
                    views.setTextViewText(R.id.small_symbol_text, stockData.symbol)
                    views.setTextViewText(R.id.small_price_text, "$${String.format("%.0f", stockData.price)}")

                    val percentText = if (stockData.percentChange >= 0) {
                        "+${String.format("%.1f", stockData.percentChange)}%"
                    } else {
                        "${String.format("%.1f", stockData.percentChange)}%"
                    }
                    views.setTextViewText(R.id.small_percent_text, percentText)

                    // Set colors
                    val changeColor = if (stockData.change >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                    views.setTextColor(R.id.small_percent_text, changeColor)
                }
            }

            // Apply theme
            WidgetStyleHelper.applyWidgetTheme(context, views, appWidgetId, widgetSize == WidgetSize.SMALL)

            // Set up click intent
            val pendingIntent = createClickIntent(context, appWidgetId, symbol, launchApp, launchUrl, widgetSize)
            views.setOnClickPendingIntent(config.containerViewId, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createClickIntent(
            context: Context,
            appWidgetId: Int,
            symbol: String,
            launchApp: String?,
            launchUrl: String?,
            widgetSize: WidgetSize
        ): PendingIntent {
            val intentId = if (widgetSize == WidgetSize.SMALL) appWidgetId + 1000 else appWidgetId

            return when {
                launchUrl != null -> {
                    // Create URL intent
                    val url = launchUrl.replace("{SYMBOL}", symbol)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    PendingIntent.getActivity(
                        context,
                        intentId,
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
                            context, intentId, launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    } else {
                        createFallbackIntent(context, intentId, widgetSize)
                    }
                }

                else -> {
                    createFallbackIntent(context, intentId, widgetSize)
                }
            }
        }

        private fun createFallbackIntent(context: Context, intentId: Int, widgetSize: WidgetSize): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                when (widgetSize) {
                    WidgetSize.NORMAL -> putExtra("widgetId", intentId)
                    WidgetSize.SMALL -> putExtra("smallWidgetId", intentId - 1000)
                }
            }
            return PendingIntent.getActivity(
                context, intentId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getWidgetConfig(widgetSize: WidgetSize): WidgetConfig {
            return when (widgetSize) {
                WidgetSize.NORMAL -> WidgetConfig(
                    layoutRes = R.layout.widget_2x1_layout,
                    containerViewId = R.id.widget_container,
                    prefixKey = "",
                    defaultSymbol = "AAPL"
                )
                WidgetSize.SMALL -> WidgetConfig(
                    layoutRes = R.layout.widget_1x1_layout,
                    containerViewId = R.id.small_widget_container,
                    prefixKey = "small_",
                    defaultSymbol = "SPY"
                )
            }
        }
    }

    private data class WidgetConfig(
        val layoutRes: Int,
        val containerViewId: Int,
        val prefixKey: String,
        val defaultSymbol: String
    )
}

// Concrete implementations - these are what the manifest references
class StockWidgetProvider : BaseStockWidgetProvider() {
    override val widgetSize = WidgetSize.NORMAL
}

class SmallStockWidgetProvider : BaseStockWidgetProvider() {
    override val widgetSize = WidgetSize.SMALL
}