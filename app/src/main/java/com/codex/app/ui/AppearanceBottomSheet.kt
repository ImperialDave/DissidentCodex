package com.codex.app.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.codex.app.R
import com.codex.app.databinding.BottomSheetAppearanceBinding
import com.codex.app.databinding.ItemThemeSwatchBinding
import com.codex.app.utils.ThemeManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AppearanceBottomSheet : BottomSheetDialogFragment() {

    var onApplied: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = BottomSheetAppearanceBinding.inflate(inflater, container, false)
        val ctx = requireContext()
        val currentPalette = ThemeManager.getPalette(ctx)
        val currentMode = ThemeManager.getColorMode(ctx)

        binding.colorModeToggle.check(
            if (currentMode == ThemeManager.ColorMode.LIGHT) R.id.modeLightBtn else R.id.modeDarkBtn
        )
        binding.colorModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.modeLightBtn) {
                ThemeManager.ColorMode.LIGHT
            } else {
                ThemeManager.ColorMode.DARK
            }
            if (mode != ThemeManager.getColorMode(ctx)) {
                ThemeManager.saveColorMode(ctx, mode)
                onApplied?.invoke()
            }
        }

        populateRow(binding.originalThemeRow, ThemeManager.Family.ORIGINAL, currentPalette)
        populateRow(binding.calmThemeRow, ThemeManager.Family.CALM, currentPalette)

        return binding.root
    }

    private fun populateRow(
        row: LinearLayout,
        family: ThemeManager.Family,
        currentPalette: ThemeManager.Palette
    ) {
        row.removeAllViews()
        val ctx = requireContext()
        ThemeManager.Palette.entries.filter { it.family == family }.forEach { palette ->
            val item = ItemThemeSwatchBinding.inflate(layoutInflater, row, false)
            val start = ContextCompat.getColor(ctx, palette.swatchStartRes)
            val end = ContextCompat.getColor(ctx, palette.swatchEndRes)
            val circle = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(start, end)
            ).apply {
                shape = GradientDrawable.OVAL
                val stroke = if (palette == currentPalette) 3 else 1
                val strokeColor = if (palette == currentPalette) {
                    ContextCompat.getColor(ctx, palette.swatchStartRes)
                } else {
                    ContextCompat.getColor(ctx, R.color.on_surface_variant)
                }
                setStroke(stroke, strokeColor)
            }
            item.swatchCircle.background = circle
            item.swatchLabel.text = getString(palette.labelRes)
            item.swatchLabel.setTextColor(
                if (palette == currentPalette) {
                    ContextCompat.getColor(ctx, palette.swatchStartRes)
                } else {
                    ContextCompat.getColor(ctx, android.R.color.darker_gray)
                }
            )
            item.root.setOnClickListener {
                if (palette != ThemeManager.getPalette(ctx)) {
                    ThemeManager.savePalette(ctx, palette)
                    onApplied?.invoke()
                }
            }
            row.addView(item.root)
        }
    }
}