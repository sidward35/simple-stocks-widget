package xyz.smathur.simplestockswidget

data class UrlTemplate(
    val name: String,
    val template: String, // Use {SYMBOL} as placeholder
    val icon: String? = null
) {
    fun buildUrl(symbol: String): String = template.replace("{SYMBOL}", symbol)
}