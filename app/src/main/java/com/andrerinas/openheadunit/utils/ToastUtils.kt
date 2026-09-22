package com.andrerinas.openheadunit.utils

import android.content.Context
import android.widget.Toast
import com.andrerinas.openheadunit.App

object ToastUtils {
    @JvmStatic
    fun showToast(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT, force: Boolean = false) {
        if (force || isToastEnabled(context)) {
            Toast.makeText(context, text, duration).show()
        }
    }

    @JvmStatic
    fun showToast(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT, force: Boolean = false) {
        if (force || isToastEnabled(context)) {
            Toast.makeText(context, resId, duration).show()
        }
    }

    private fun isToastEnabled(context: Context): Boolean {
        return try {
            App.provide(context).settings.showToastMessages
        } catch (_: Exception) {
            true
        }
    }
}
