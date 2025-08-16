package xyz.smathur.simplestockswidget

import android.content.Context
import org.json.JSONObject

data class StockData(
    val symbol: String,
    val price: Double,
    val change: Double,
    val percentChange: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

object StockDataCache {
    private val cache = mutableMapOf<String, StockData>()
    private const val CACHE_PREFS = "stock_cache"

    // Load cache from persistent storage on first access
    private fun ensureCacheLoaded(context: Context) {
        if (cache.isEmpty()) {
            loadCacheFromStorage(context)
        }
    }

    fun getStockData(symbol: String, context: Context? = null): StockData {
        context?.let { ensureCacheLoaded(it) }

        return cache[symbol] ?: run {
            android.util.Log.w("StockWidget", "No cached data for $symbol, using defaults")
            StockData(
                symbol = symbol,
                price = 150.0, // Default placeholder values
                change = 2.45,
                percentChange = 1.65,
                lastUpdated = 0L // 0 indicates default/placeholder data
            )
        }
    }

    fun updateStockData(symbol: String, data: StockData, context: Context? = null) {
        android.util.Log.d("StockWidget", "Caching $symbol: $${data.price} (${data.change})")
        cache[symbol] = data

        // Persist to storage immediately
        context?.let { saveCacheToStorage(it) }
    }

    fun hasRealData(symbol: String, context: Context? = null): Boolean {
        context?.let { ensureCacheLoaded(it) }
        val data = cache[symbol] ?: return false
        return data.lastUpdated > 0L // Real data has timestamp > 0
    }

    fun getAllSymbols(context: Context? = null): Set<String> {
        context?.let { ensureCacheLoaded(it) }
        return cache.keys.toSet()
    }

    fun getActiveSymbols(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val symbols = mutableSetOf<String>()

        // Get symbols from all widgets
        prefs.all.keys.forEach { key ->
            when {
                key.startsWith("symbol_") -> {
                    prefs.getString(key, null)?.let { symbols.add(it) }
                }
                key.startsWith("small_symbol_") -> {
                    prefs.getString(key, null)?.let { symbols.add(it) }
                }
            }
        }

        return symbols
    }

    // Helper method for debugging
    fun getCacheStatus(context: Context? = null): String {
        context?.let { ensureCacheLoaded(it) }
        return cache.entries.joinToString("\n") { (symbol, data) ->
            val status = if (data.lastUpdated == 0L) "DEFAULT" else "CACHED"
            val time = if (data.lastUpdated == 0L) "never" else
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(data.lastUpdated))
            "$symbol: $status ($time) $${data.price}"
        }
    }

    // Persist cache to SharedPreferences as JSON
    private fun saveCacheToStorage(context: Context) {
        try {
            val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val cacheJson = JSONObject()

            cache.forEach { (symbol, data) ->
                val stockJson = JSONObject().apply {
                    put("symbol", data.symbol)
                    put("price", data.price)
                    put("change", data.change)
                    put("percentChange", data.percentChange)
                    put("lastUpdated", data.lastUpdated)
                }
                cacheJson.put(symbol, stockJson)
            }

            with(prefs.edit()) {
                putString("cache_data", cacheJson.toString())
                putLong("cache_saved_at", System.currentTimeMillis())
                apply()
            }

            android.util.Log.d("StockWidget", "Cache saved to storage: ${cache.size} symbols")
        } catch (e: Exception) {
            android.util.Log.e("StockWidget", "Failed to save cache", e)
        }
    }

    // Load cache from SharedPreferences
    private fun loadCacheFromStorage(context: Context) {
        try {
            val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val cacheJsonString = prefs.getString("cache_data", null)
            val cacheSavedAt = prefs.getLong("cache_saved_at", 0L)

            if (cacheJsonString != null) {
                val cacheJson = JSONObject(cacheJsonString)
                val loadedCount = cacheJson.keys().asSequence().count()

                cacheJson.keys().forEach { symbol ->
                    val stockJson = cacheJson.getJSONObject(symbol)
                    val stockData = StockData(
                        symbol = stockJson.getString("symbol"),
                        price = stockJson.getDouble("price"),
                        change = stockJson.getDouble("change"),
                        percentChange = stockJson.getDouble("percentChange"),
                        lastUpdated = stockJson.getLong("lastUpdated")
                    )
                    cache[symbol] = stockData
                }

                val cacheAge = (System.currentTimeMillis() - cacheSavedAt) / (1000 * 60) // minutes
                android.util.Log.d("StockWidget", "Cache loaded from storage: $loadedCount symbols (${cacheAge}min old)")
            } else {
                android.util.Log.d("StockWidget", "No cached data found in storage")
            }
        } catch (e: Exception) {
            android.util.Log.e("StockWidget", "Failed to load cache", e)
            cache.clear() // Clear corrupted cache
        }
    }

    // Clean up cache for deleted symbols
    fun cleanupUnusedSymbols(context: Context, activeSymbols: Set<String>) {
        ensureCacheLoaded(context)
        val symbolsToRemove = cache.keys - activeSymbols

        if (symbolsToRemove.isNotEmpty()) {
            android.util.Log.d("StockWidget", "Removing unused cached symbols: $symbolsToRemove")
            symbolsToRemove.forEach { cache.remove(it) }
            saveCacheToStorage(context)
        }
    }

    // Force refresh cache from storage (for debugging)
    fun reloadFromStorage(context: Context) {
        cache.clear()
        loadCacheFromStorage(context)
    }
}