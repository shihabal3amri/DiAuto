package com.andrerinas.openheadunit.utils

import android.content.IntentFilter
import com.andrerinas.openheadunit.contract.KeyIntent

object IntentFilters {
    val keyEvent = IntentFilter(KeyIntent.action)
}