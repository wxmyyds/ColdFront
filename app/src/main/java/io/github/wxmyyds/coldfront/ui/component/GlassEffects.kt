package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }
val LocalInterfaceBlur = compositionLocalOf { false }

@Composable
fun rememberGlassHazeState(): HazeState = rememberHazeState()

fun Modifier.glassSource(state: HazeState?): Modifier =
    if (state == null) this else hazeSource(state)

@Composable
fun Modifier.glassEffect(state: HazeState?, enabled: Boolean): Modifier {
    if (!enabled || state == null) return this
    val surface = MaterialTheme.colorScheme.surface
    val tint = surface.copy(alpha = 0.78f)
    return hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = surface,
            tints = listOf(HazeTint(tint)),
            blurRadius = 18.dp,
            fallbackTint = HazeTint(surface),
        ),
    )
}
