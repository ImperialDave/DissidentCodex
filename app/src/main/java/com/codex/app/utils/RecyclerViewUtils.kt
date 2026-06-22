package com.codex.app.utils

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** RecyclerView inside ScrollView needs this or items render with zero height. */
fun RecyclerView.setupInsideScrollView() {
    isNestedScrollingEnabled = false
    setHasFixedSize(false)
    layoutManager = object : LinearLayoutManager(context) {
        override fun onMeasure(
            recycler: RecyclerView.Recycler,
            state: RecyclerView.State,
            widthSpec: Int,
            heightSpec: Int
        ) {
            val mode = View.MeasureSpec.getMode(heightSpec)
            if (mode == View.MeasureSpec.UNSPECIFIED || mode == View.MeasureSpec.AT_MOST) {
                val expanded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                super.onMeasure(recycler, state, widthSpec, expanded)
            } else {
                super.onMeasure(recycler, state, widthSpec, heightSpec)
            }
        }
    }
}