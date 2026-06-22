package com.codex.app.utils

import android.content.Context
import com.codex.app.R

object ThemeManager {
    private const val PREFS = "codex_theme"
    private const val KEY = "theme_id"

    enum class Palette(val id: String, val labelRes: Int, val styleRes: Int) {
        MIDNIGHT("midnight", R.string.theme_midnight, R.style.Theme_Codex_Midnight),
        OCEAN("ocean", R.string.theme_ocean, R.style.Theme_Codex_Ocean),
        EMBER("ember", R.string.theme_ember, R.style.Theme_Codex_Ember),
        FOREST("forest", R.string.theme_forest, R.style.Theme_Codex_Forest),
        AURORA("aurora", R.string.theme_aurora, R.style.Theme_Codex_Aurora);

        companion object {
            fun fromId(id: String?): Palette =
                entries.find { it.id == id } ?: MIDNIGHT
        }
    }

    fun getPalette(context: Context): Palette {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, Palette.MIDNIGHT.id)
        return Palette.fromId(id)
    }

    fun getThemeStyle(context: Context): Int = getPalette(context).styleRes

    fun savePalette(context: Context, palette: Palette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, palette.id)
            .apply()
    }
}