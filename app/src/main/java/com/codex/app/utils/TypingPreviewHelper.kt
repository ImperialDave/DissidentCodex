package com.codex.app.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.codex.app.R

object TypingPreviewHelper {

    fun setup(
        input: EditText,
        preview: TextView,
        onScrollIntoView: (() -> Unit)? = null
    ) {
        applyThemedInputBackground(input)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                if (text.isBlank()) {
                    preview.visibility = View.GONE
                    preview.text = ""
                } else {
                    preview.visibility = View.VISIBLE
                    preview.text = text
                }
                onScrollIntoView?.invoke()
            }
        }
        input.addTextChangedListener(watcher)
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onScrollIntoView?.invoke()
        }
    }

    fun clearPreview(input: EditText, preview: TextView) {
        input.text?.clear()
        preview.visibility = View.GONE
        preview.text = ""
    }

    fun applyThemedInputBackground(input: EditText) {
        val context = input.context
        val surfaceColor = resolveThemeColor(context, R.attr.codexSurface)
        val strokeColor = resolveThemeColor(context, R.attr.codexOnSurfaceVariant)
        val density = context.resources.displayMetrics.density
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(surfaceColor)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), strokeColor)
        }
        input.background = drawable
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attr, typedValue, true)) {
            return ContextCompat.getColor(context, R.color.surface)
        }
        return when (typedValue.type) {
            TypedValue.TYPE_REFERENCE -> ContextCompat.getColor(context, typedValue.resourceId)
            else -> typedValue.data
        }
    }
}