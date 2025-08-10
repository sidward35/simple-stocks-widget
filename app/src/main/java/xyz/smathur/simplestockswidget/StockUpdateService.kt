package xyz.smathur.simplestockswidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL
import org.json.JSONObject

class StockUpdateService : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_STOCKS) {
            val forceUpdate = intent.getBooleanExtra("force_update", false)
            updateStockData(context, forceUpdate)
        }
    }

    private fun updateStockData(context: Context, forceUpdate: Boolean = false) {
        // Only check market hours for automatic updates, not forced ones
        if (!forceUpdate && !isMarketHours()) {
            android.util.Log.d("StockWidget", "Outside market hours - skipping update")
            return
        }

        val activeSymbols = StockDataCache.getActiveSymbols(context)
        android.util.Log.d("StockWidget", "Active symbols: $activeSymbols")

        if (activeSymbols.isEmpty()) {
            android.util.Log.d("StockWidget", "No active symbols found")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val apiKey = prefs.getString("finnhub_api_key", "") ?: ""

                android.util.Log.d("StockWidget", "Using API key: ${if (apiKey.isEmpty()) "EMPTY" else "SET"}")

                if (apiKey.isEmpty()) {
                    android.util.Log.d("StockWidget", "Using mock data - no API key")
                    // Use mock data if no API key
                    updateWithMockData(context, activeSymbols)
                    return@launch
                }

                activeSymbols.forEach { symbol ->
                    try {
                        android.util.Log.d("StockWidget", "Fetching data for: $symbol")
                        val stockData = fetchStockData(symbol, apiKey)
                        StockDataCache.updateStockData(symbol, stockData)
                        android.util.Log.d("StockWidget", "Updated $symbol: ${stockData.price}")
                    } catch (e: Exception) {
                        android.util.Log.e("StockWidget", "Error fetching $symbol", e)
                    }
                }

                // Update all widgets
                updateAllWidgets(context)

            } catch (e: Exception) {
                android.util.Log.e("StockWidget", "Update error", e)
            }
        }
    }

    private fun fetchStockData(symbol: String, apiKey: String): StockData {
        val url = "https://finnhub.io/api/v1/quote?symbol=$symbol"
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("X-Finnhub-Token", apiKey)
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            throw Exception("HTTP $responseCode")
        }

        val response = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(response)

        if (json.has("error")) {
            throw Exception("API Error: ${json.getString("error")}")
        }

        val currentPrice = json.getDouble("c")
        val change = json.getDouble("d")
        val percentChange = json.getDouble("dp")

        return StockData(
            symbol = symbol,
            price = currentPrice,
            change = change,
            percentChange = percentChange
        )
    }

    private fun updateWithMockData(context: Context, symbols: Set<String>) {
        // Generate realistic mock data for demo purposes
        symbols.forEach { symbol ->
            val basePrice = when {
                symbol.contains("SPY") -> 450.0
                symbol.contains("AAPL") -> 175.0
                symbol.contains("TSLA") -> 250.0
                symbol.contains("NVDA") -> 800.0
                else -> 100.0 + (Math.random() * 400)
            }

            val change = (Math.random() - 0.5) * 10 // Random change between -5 and +5
            val percentChange = (change / basePrice) * 100

            val stockData = StockData(
                symbol = symbol,
                price = basePrice + change,
                change = change,
                percentChange = percentChange
            )

            StockDataCache.updateStockData(symbol, stockData)
        }

        updateAllWidgets(context)
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update 2x1 widgets
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, StockWidgetProvider::class.java)
        )
        for (widgetId in widgetIds) {
            StockWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
        }

        // Update 1x1 widgets
        val smallWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SmallStockWidgetProvider::class.java)
        )
        for (widgetId in smallWidgetIds) {
            SmallStockWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        private const val ACTION_UPDATE_STOCKS = "xyz.smathur.simplestockswidget.UPDATE_STOCKS"
        private const val UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes

        fun scheduleUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, StockUpdateService::class.java)
            intent.action = ACTION_UPDATE_STOCKS

            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL,
                UPDATE_INTERVAL,
                pendingIntent
            )
        }

        fun cancelUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, StockUpdateService::class.java)
            intent.action = ACTION_UPDATE_STOCKS

            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
        }

        private fun isMarketHours(): Boolean {
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

            // Skip weekends (Saturday = 7, Sunday = 1)
            if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
                return false
            }

            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val currentMinutes = hour * 60 + minute

            // Market hours: 6:30 AM (390 minutes) to 1:00 PM (780 minutes) PT
            val marketOpenMinutes = 6 * 60 + 30  // 6:30 AM = 390 minutes
            val marketCloseMinutes = 13 * 60     // 1:00 PM = 780 minutes

            return currentMinutes in marketOpenMinutes..marketCloseMinutes
        }
    }
}