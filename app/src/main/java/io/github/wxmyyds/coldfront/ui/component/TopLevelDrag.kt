package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal fun topLevelDragReversed(direction: LayoutDirection): Boolean = direction == LayoutDirection.Rtl

/** Delta and velocity are logical coordinates, both normalized by draggable.reverseDirection. */
internal fun topLevelPositionAfterDrag(position: Float, delta: Float, width: Float, lastPage: Int): Float =
    (position - delta / width.coerceAtLeast(1f)).coerceIn(0f, lastPage.toFloat())

internal fun topLevelTargetAfterDrag(position: Float, velocity: Float, lastPage: Int): Int =
    (if (abs(velocity) > 700f) {
        (position - sign(velocity)).roundToInt()
    } else {
        position.roundToInt()
    }).coerceIn(0, lastPage)
