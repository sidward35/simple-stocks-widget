package xyz.smathur.simplestockswidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

class StockUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val forceUpdate = inputData.getBoolean("force_update", false)
            updateStockData(applicationContext, forceUpdate)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("StockWidget", "Worker failed", e)
            Result.retry()
        }
    }

    private suspend fun updateStockData(context: Context, forceUpdate: Boolean = false) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Track update attempt
        with(prefs.edit()) {
            putLong("last_update_attempt", System.currentTimeMillis())
            apply()
        }

        android.util.Log.d("StockWidget", "Update triggered - forceUpdate: $forceUpdate, marketHours: ${isMarketHours(context)}")

        // Only check market hours for automatic updates, not forced ones
        if (!forceUpdate && !isMarketHours(context)) {
            android.util.Log.d("StockWidget", "Outside market hours - skipping update")
            return
        }

        // Clean up old widget preferences first
        cleanupOldWidgetPrefs(context)

        val activeSymbols = StockDataCache.getActiveSymbols(context)
        android.util.Log.d("StockWidget", "Active symbols: $activeSymbols")

        if (activeSymbols.isEmpty()) {
            android.util.Log.d("StockWidget", "No active symbols found")
            return
        }

        val apiKey = prefs.getString("finnhub_api_key", "") ?: ""
        android.util.Log.d("StockWidget", "Using API key: ${if (apiKey.isEmpty()) "EMPTY" else "SET"}")

        if (apiKey.isEmpty()) {
            android.util.Log.d("StockWidget", "Using mock data - no API key")
            updateWithMockData(context, activeSymbols)
            return
        }

        var anySuccess = false
        val failedSymbols = mutableListOf<String>()

        activeSymbols.forEach { symbol ->
            try {
                android.util.Log.d("StockWidget", "Fetching data for: $symbol")
                val stockData = fetchStockData(symbol, apiKey)
                StockDataCache.updateStockData(symbol, stockData, applicationContext)
                android.util.Log.d("StockWidget", "✅ Updated $symbol: ${stockData.price}")
                anySuccess = true
            } catch (e: Exception) {
                android.util.Log.e("StockWidget", "❌ Error fetching $symbol, keeping cached data", e)
                failedSymbols.add(symbol)
                // Don't update cache - preserve existing data
            }
        }

        // Only update widgets if we had at least one successful API call
        // OR if all symbols failed but we have cached data
        val shouldUpdateWidgets = anySuccess ||
                activeSymbols.all { StockDataCache.hasRealData(it) }

        if (shouldUpdateWidgets) {
            android.util.Log.d("StockWidget", "Updating widgets - success: $anySuccess, failed: ${failedSymbols.size}")
            updateAllWidgets(context)

            // Track widget update
            with(prefs.edit()) {
                putLong("last_widget_update", System.currentTimeMillis())
                apply()
            }

            // Track successful update only if we had API success
            if (anySuccess) {
                with(prefs.edit()) {
                    putLong("last_successful_update", System.currentTimeMillis())
                    apply()
                }
            }
        } else {
            android.util.Log.w("StockWidget", "No updates - all API calls failed and no cached data")
        }
    }

    private suspend fun fetchStockData(symbol: String, apiKey: String): StockData {
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

        // Validate data
        if (currentPrice <= 0) {
            throw Exception("Invalid price data: $currentPrice")
        }

        return StockData(
            symbol = symbol,
            price = currentPrice,
            change = change,
            percentChange = percentChange
        )
    }

    private suspend fun updateWithMockData(context: Context, symbols: Set<String>) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

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

            StockDataCache.updateStockData(symbol, stockData, context)
        }

        updateAllWidgets(context)

        // Track updates for mock data too
        with(prefs.edit()) {
            putLong("last_widget_update", System.currentTimeMillis())
            putLong("last_successful_update", System.currentTimeMillis())
            apply()
        }
    }

    private suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update 2x1 widgets
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, StockWidgetProvider::class.java)
        )
        for (widgetId in widgetIds) {
            BaseStockWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId, BaseStockWidgetProvider.WidgetSize.NORMAL)
        }

        // Update 1x1 widgets
        val smallWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SmallStockWidgetProvider::class.java)
        )
        for (widgetId in smallWidgetIds) {
            BaseStockWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId, BaseStockWidgetProvider.WidgetSize.SMALL)
        }
    }

    private fun cleanupOldWidgetPrefs(context: Context) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Get current widget IDs
        val currentNormalIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, StockWidgetProvider::class.java)
        ).toSet()

        val currentSmallIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SmallStockWidgetProvider::class.java)
        ).toSet()

        // Find and remove stale preferences
        val keysToRemove = mutableSetOf<String>()
        prefs.all.keys.forEach { key ->
            when {
                key.startsWith("symbol_") -> {
                    val widgetId = key.removePrefix("symbol_").toIntOrNull()
                    if (widgetId != null && widgetId !in currentNormalIds) {
                        keysToRemove.add(key)
                        keysToRemove.add("launch_app_$widgetId")
                        keysToRemove.add("launch_url_$widgetId")
                        keysToRemove.add("theme_$widgetId")
                    }
                }
                key.startsWith("small_symbol_") -> {
                    val widgetId = key.removePrefix("small_symbol_").toIntOrNull()
                    if (widgetId != null && widgetId !in currentSmallIds) {
                        keysToRemove.add(key)
                        keysToRemove.add("small_launch_app_$widgetId")
                        keysToRemove.add("small_launch_url_$widgetId")
                        keysToRemove.add("small_theme_$widgetId")
                    }
                }
            }
        }

        if (keysToRemove.isNotEmpty()) {
            android.util.Log.d("StockWidget", "Cleaning up ${keysToRemove.size} stale widget preferences")
            with(prefs.edit()) {
                keysToRemove.forEach { remove(it) }
                apply()
            }
        }
    }

    private fun isMarketHours(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val intervalMinutes = prefs.getInt("update_interval", 15).toLong() + 1 // update interval plus buffer

        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/New_York"))
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

        // Skip weekends (Saturday = 7, Sunday = 1)
        if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
            return false
        }

        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        // Market hours: 9:30 AM (570 minutes) to 4:00 PM (960 minutes) ET
        val marketOpenMinutes = 9 * 60 + 30                 // 9:30 AM = 570 minutes
        val marketCloseMinutes = 16 * 60 + intervalMinutes  // 4:00 PM = 960 minutes + buffer for update interval

        return currentMinutes in marketOpenMinutes..marketCloseMinutes
    }

    companion object {
        private const val WORK_NAME = "stock_update_work"

        fun scheduleUpdate(context: Context) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val intervalMinutes = prefs.getInt("update_interval", 15).toLong()

            android.util.Log.d("StockWidget", "Scheduling WorkManager with ${intervalMinutes}min interval")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Market hours work - frequent updates
            val marketHoursWork = PeriodicWorkRequestBuilder<StockUpdateWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag("market_hours")
                .build()

            // Off-hours work - less frequent updates (every hour)
            val offHoursWork = PeriodicWorkRequestBuilder<StockUpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag("off_hours")
                .build()

            val workManager = WorkManager.getInstance(context)

            // Cancel existing work
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelAllWorkByTag("market_hours")
            workManager.cancelAllWorkByTag("off_hours")

            // Schedule new work
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                marketHoursWork
            )

            android.util.Log.d("StockWidget", "WorkManager scheduled successfully")
        }

        fun cancelUpdate(context: Context) {
            android.util.Log.d("StockWidget", "Canceling WorkManager")
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelAllWorkByTag("market_hours")
            workManager.cancelAllWorkByTag("off_hours")
        }

        fun triggerImmediateUpdate(context: Context) {
            val workData = workDataOf("force_update" to true)
            val immediateWork = OneTimeWorkRequestBuilder<StockUpdateWorker>()
                .setInputData(workData)
                .build()

            WorkManager.getInstance(context).enqueue(immediateWork)
            android.util.Log.d("StockWidget", "Triggered immediate update")
        }
    }
}