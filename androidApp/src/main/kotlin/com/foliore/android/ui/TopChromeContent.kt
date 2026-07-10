package com.foliore.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foliore.android.ui.theme.CormorantGaramondFamily
import com.foliore.android.ui.tuning.LocalVisualTuning

typealias TopChromeContent = @Composable (Modifier) -> Unit

val TopChromeHeaderHeight = 74.dp
val TopChromeDividerHalfHeight = 4.dp

@Composable
fun FolioreTopLevelHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tuning = LocalVisualTuning.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(tuning.topChromeHeaderHeight.dp)
            .padding(
                start = tuning.libraryHeaderStartPadding.dp,
                end = tuning.libraryHeaderEndPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.Light,
                fontSize = 38.sp,
                lineHeight = 42.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}
