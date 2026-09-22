package com.andrerinas.openheadunit.app

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.LocaleHelper

/**
 * Base for the projection activity. Does NOT extend [BaseActivity] to avoid
 * [BaseActivity.onResume] calling [recreate] on night-mode or theme changes,
 * which would destroy the video surface mid-session.
 */
abstract class SurfaceActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
