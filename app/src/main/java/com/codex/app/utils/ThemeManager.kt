package com.codex.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.codex.app.R

object ThemeManager {
    private const val PREFS = "codex_theme"
    private const val KEY_THEME = "theme_id"
    private const val KEY_COLOR_MODE = "codex_color_mode"

    enum class Family { ORIGINAL, CALM }

    enum class ColorMode(val id: String) {
        DARK("dark"),
        LIGHT("light");

        companion object {
            fun fromId(id: String?): ColorMode =
                entries.find { it.id == id } ?: DARK
        }
    }

    enum class Palette(
        val id: String,
        val labelRes: Int,
        val darkStyleRes: Int,
        val lightStyleRes: Int,
        val family: Family,
        val swatchStartRes: Int,
        val swatchEndRes: Int
    ) {
        MIDNIGHT(
            "midnight", R.string.theme_midnight,
            R.style.Theme_Codex_Midnight, R.style.Theme_Codex_Midnight_Light,
            Family.ORIGINAL, R.color.midnight_accent, R.color.midnight_accent_light
        ),
        OCEAN(
            "ocean", R.string.theme_ocean,
            R.style.Theme_Codex_Ocean, R.style.Theme_Codex_Ocean_Light,
            Family.ORIGINAL, R.color.ocean_accent, R.color.ocean_accent_light
        ),
        EMBER(
            "ember", R.string.theme_ember,
            R.style.Theme_Codex_Ember, R.style.Theme_Codex_Ember_Light,
            Family.ORIGINAL, R.color.ember_accent, R.color.ember_accent_light
        ),
        FOREST(
            "forest", R.string.theme_forest,
            R.style.Theme_Codex_Forest, R.style.Theme_Codex_Forest_Light,
            Family.ORIGINAL, R.color.forest_accent, R.color.forest_accent_light
        ),
        AURORA(
            "aurora", R.string.theme_aurora,
            R.style.Theme_Codex_Aurora, R.style.Theme_Codex_Aurora_Light,
            Family.ORIGINAL, R.color.aurora_accent, R.color.aurora_accent_light
        ),
        SAGE(
            "sage", R.string.theme_sage,
            R.style.Theme_Codex_Sage, R.style.Theme_Codex_Sage_Light,
            Family.CALM, R.color.sage_accent, R.color.sage_accent_light
        ),
        DUSK(
            "dusk", R.string.theme_dusk,
            R.style.Theme_Codex_Dusk, R.style.Theme_Codex_Dusk_Light,
            Family.CALM, R.color.dusk_accent, R.color.dusk_accent_light
        ),
        SAND(
            "sand", R.string.theme_sand,
            R.style.Theme_Codex_Sand, R.style.Theme_Codex_Sand_Light,
            Family.CALM, R.color.sand_accent, R.color.sand_accent_light
        ),
        MIST(
            "mist", R.string.theme_mist,
            R.style.Theme_Codex_Mist, R.style.Theme_Codex_Mist_Light,
            Family.CALM, R.color.mist_accent, R.color.mist_accent_light
        ),
        LINEN(
            "linen", R.string.theme_linen,
            R.style.Theme_Codex_Linen, R.style.Theme_Codex_Linen_Light,
            Family.CALM, R.color.linen_accent, R.color.linen_accent_light
        );

        companion object {
            fun fromId(id: String?): Palette =
                entries.find { it.id == id } ?: MIDNIGHT
        }
    }

    fun init(context: Context) {
        // User-selected palette + brightness; ignore system night mode.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    fun getPalette(context: Context): Palette {
        val id = prefs(context).getString(KEY_THEME, Palette.MIDNIGHT.id)
        return Palette.fromId(id)
    }

    fun getColorMode(context: Context): ColorMode {
        val id = prefs(context).getString(KEY_COLOR_MODE, ColorMode.DARK.id)
        return ColorMode.fromId(id)
    }

    fun getThemeStyle(context: Context): Int {
        val palette = getPalette(context)
        return if (getColorMode(context) == ColorMode.LIGHT) palette.lightStyleRes else palette.darkStyleRes
    }

    fun savePalette(context: Context, palette: Palette) {
        prefs(context).edit().putString(KEY_THEME, palette.id).apply()
    }

    fun saveColorMode(context: Context, mode: ColorMode) {
        prefs(context).edit().putString(KEY_COLOR_MODE, mode.id).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}