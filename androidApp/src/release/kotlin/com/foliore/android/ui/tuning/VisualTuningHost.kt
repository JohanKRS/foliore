package com.foliore.android.ui.tuning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VisualTuningHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVisualTuning provides VisualTuningValues.Default) {
        content()
    }
}
