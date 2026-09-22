package com.andrerinas.openheadunit.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet

import android.view.View

class OverlayTouchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }
}