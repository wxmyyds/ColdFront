package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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

fun Modifier.glassTopBarEffect(state: HazeState?, enabled: Boolean): Modifier =
    glassEffect(state, enabled).windowInsetsPadding(WindowInsets.statusBars)

@Composable
fun Modifier.glassEffect(state: HazeState?, enabled: Boolean): Modifier {
    if (!enabled || state == null) return this
    val surface = MaterialTheme.colorScheme.surface
    val tint = surface.copy(alpha = 0.58f)
    return hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = surface,
            tints = listOf(HazeTint(tint)),
            blurRadius = 32.dp,
            fallbackTint = HazeTint(surface),
        ),
    )
}
