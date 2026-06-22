package com.codex.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.codex.app.utils.ThemeManager

abstract class BaseThemedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
    }
}