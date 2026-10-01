package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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

@Composable
fun Modifier.glassSource(state: HazeState?): Modifier {
    if (state == null) return this
    val surface = MaterialTheme.colorScheme.background
    return background(surface).hazeSource(state)
}

@Composable
fun Modifier.glassTopBarEffect(state: HazeState?, enabled: Boolean): Modifier {
    val surface = MaterialTheme.colorScheme.background
    val backdrop = if (enabled) {
        glassEffect(state, enabled = true)
    } else {
        background(surface)
    }
    return backdrop
}

@Composable
fun Modifier.glassEffect(state: HazeState?, enabled: Boolean): Modifier {
    if (!enabled || state == null) return this
    val surface = MaterialTheme.colorScheme.background
    // 与 InstallerX 的 25px 小半径 + 同色高透明度 blend 同量级：内容从栏下滑过时,
    // 栏体仍基本呈现页面背景色（#EFECF6 / #191920），不会因强模糊把彩色内容"泛"进栏体产生色差。
    val tint = surface.copy(alpha = 0.92f)
    return this.hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = surface,
            tints = listOf(HazeTint(tint)),
            blurRadius = 10.dp,
            fallbackTint = HazeTint(surface),
        ),
    )
}
