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
        return cache[symbol] ?: StockData(
            symbol = symbol,
            price = 150.0, // Default placeholder values
            change = 2.45,
            percentChange = 1.65
        )
    }

    fun updateStockData(symbol: String, data: StockData) {
        cache[symbol] = data
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
}