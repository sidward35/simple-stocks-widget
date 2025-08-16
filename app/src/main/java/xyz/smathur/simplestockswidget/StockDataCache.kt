package xyz.smathur.simplestockswidget

import android.content.Context

data class StockData(
    val symbol: String,
    val price: Double,
    val change: Double,
    val percentChange: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

object StockDataCache {
    private val cache = mutableMapOf<String, StockData>()

    fun getStockData(symbol: String): StockData {
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

    fun updateStockData(symbol: String, data: StockData) {
        android.util.Log.d("StockWidget", "Caching $symbol: $${data.price} (${data.change})")
        cache[symbol] = data
    }

    fun hasRealData(symbol: String): Boolean {
        val data = cache[symbol] ?: return false
        return data.lastUpdated > 0L // Real data has timestamp > 0
    }

    fun getAllSymbols(): Set<String> {
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
    fun getCacheStatus(): String {
        return cache.entries.joinToString("\n") { (symbol, data) ->
            val status = if (data.lastUpdated == 0L) "DEFAULT" else "CACHED"
            val time = if (data.lastUpdated == 0L) "never" else
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(data.lastUpdated))
            "$symbol: $status ($time) $${data.price}"
        }
    }
}