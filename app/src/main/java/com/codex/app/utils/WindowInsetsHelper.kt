package com.codex.app.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object WindowInsetsHelper {

    private const val EXTRA_TOP_DP = 12
    private const val EXTRA_BOTTOM_DP = 32
    private const val EXTRA_LIST_BOTTOM_DP = 16

    fun applyChatSafeArea(
        toolbar: View,
        messageBar: View,
        messagesList: View? = null
    ) {
        val density = toolbar.resources.displayMetrics.density
        val extraTop = (EXTRA_TOP_DP * density).toInt()
        val extraBottom = (EXTRA_BOTTOM_DP * density).toInt()
        val initialToolbarPaddingTop = toolbar.paddingTop
        val initialBarPaddingBottom = messageBar.paddingBottom
        val initialListPaddingBottom = messagesList?.paddingBottom ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(toolbar.rootView) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)

            toolbar.setPadding(
                toolbar.paddingLeft,
                initialToolbarPaddingTop + systemBars.top + extraTop,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )
            messageBar.setPadding(
                messageBar.paddingLeft,
                messageBar.paddingTop,
                messageBar.paddingRight,
                initialBarPaddingBottom + bottomInset + extraBottom
            )
            messagesList?.setPadding(
                messagesList.paddingLeft,
                messagesList.paddingTop,
                messagesList.paddingRight,
                initialListPaddingBottom + (EXTRA_LIST_BOTTOM_DP * density).toInt()
            )
            insets
        }
        ViewCompat.requestApplyInsets(toolbar.rootView)
    }

    fun applyCommentSafeArea(
        toolbar: View,
        commentBar: View,
        scrollView: View? = null
    ) {
        val density = toolbar.resources.displayMetrics.density
        val extraTop = (EXTRA_TOP_DP * density).toInt()
        val extraBottom = (EXTRA_BOTTOM_DP * density).toInt()
        val initialToolbarPaddingTop = toolbar.paddingTop
        val initialBarPaddingBottom = commentBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(toolbar.rootView) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)

            toolbar.setPadding(
                toolbar.paddingLeft,
                initialToolbarPaddingTop + systemBars.top + extraTop,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )
            commentBar.setPadding(
                commentBar.paddingLeft,
                commentBar.paddingTop,
                commentBar.paddingRight,
                initialBarPaddingBottom + bottomInset + extraBottom
            )
            scrollView?.setPadding(
                scrollView.paddingLeft,
                scrollView.paddingTop,
                scrollView.paddingRight,
                (12 * density).toInt()
            )
            insets
        }
        ViewCompat.requestApplyInsets(toolbar.rootView)
    }

    fun applyTopSafeArea(view: View, extraTopDp: Int = 12) {
        val density = view.resources.displayMetrics.density
        val extraTop = (extraTopDp * density).toInt()
        val initialTop = view.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                initialTop + systemBars.top + extraTop,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun applyBottomSafeArea(view: View, extraBottomDp: Int = 32) {
        val density = view.resources.displayMetrics.density
        val extraBottom = (extraBottomDp * density).toInt()
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                initialBottom + systemBars.bottom + extraBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}