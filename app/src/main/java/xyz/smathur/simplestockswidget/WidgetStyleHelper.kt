package xyz.smathur.simplestockswidget

import android.content.Context
import android.widget.RemoteViews

object WidgetStyleHelper {

    fun applyWidgetTheme(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        isSmallWidget: Boolean = false
    ) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        val themeKey = if (isSmallWidget) "small_theme_$appWidgetId" else "theme_$appWidgetId"
        val isDarkTheme = prefs.getBoolean(themeKey, true) // Default to dark

        val containerId = if (isSmallWidget) R.id.small_widget_container else R.id.widget_container

        try {
            // Choose base theme drawable (keeps rounded corners and stroke)
            val backgroundDrawableId = if (isDarkTheme) {
                R.drawable.widget_background_dark
            } else {
                R.drawable.widget_background_light
            }

            views.setInt(containerId, "setBackgroundResource", backgroundDrawableId)

        } catch (e: Exception) {
            android.util.Log.e("WidgetStyleHelper", "Error applying theme: ${e.message}", e)
            // Fallback to default background
            views.setInt(containerId, "setBackgroundResource", R.drawable.widget_background)
        }

        // Set text colors based on theme
        val textColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        if (isSmallWidget) {
            views.setTextColor(R.id.small_symbol_text, textColor)
            views.setTextColor(R.id.small_price_text, textColor)
        } else {
            views.setTextColor(R.id.symbol_text, textColor)
            views.setTextColor(R.id.price_text, textColor)
        }
    }
}