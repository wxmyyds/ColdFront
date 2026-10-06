package io.github.wxmyyds.coldfront.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FallbackScreenCornerRadius = 28.dp

/** Uses the device's reported bottom-left display corner, with a fallback for flat screens. */
@Composable
internal fun rememberScreenCornerRadius(): Dp {
    val density = LocalDensity.current.density
    val insets = LocalView.current.rootWindowInsets
    val systemRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
    } else {
        null
    }
    val radiusPx = systemRadius ?: (FallbackScreenCornerRadius.value * density).toInt()
    return (radiusPx / density).dp
}
