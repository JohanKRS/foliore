package com.foliore.android

import android.app.Activity
import android.os.Build
import android.view.WindowManager

internal fun Activity.allowDisplayCutout() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

    window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
