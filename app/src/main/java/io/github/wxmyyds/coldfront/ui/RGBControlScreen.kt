package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

@Composable
fun RGBControlScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()

    var effect by remember { mutableStateOf(LightEffect.ALWAYS_BRIGHT) }
    var r by remember { mutableIntStateOf(0) }
    var g by remember { mutableIntStateOf(80) }
    var b by remember { mutableIntStateOf(200) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(strings.rgbTitle, style = MaterialTheme.typography.headlineMedium)

        when {
            !state.isConnected ->
                Text(strings.homeDisconnected, style = MaterialTheme.typography.bodyMedium)
            state.deviceType?.supportsRgb == false ->
                Text(strings.rgbNotSupported, style = MaterialTheme.typography.bodyMedium)
            else -> {
                ColorPreview(effect, r, g, b)

                // 灯效选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LightEffect.entries.forEach { e ->
                        FilterChip(
                            selected = effect == e,
                            onClick = { effect = e },
                            label = { Text(effectLabel(e, strings)) },
                        )
                    }
                }

                if (effect != LightEffect.COLORFUL && effect != LightEffect.OFF) {
                    ColorSlider(strings.rgbRed, r) { r = it }
                    ColorSlider(strings.rgbGreen, g) { g = it }
                    ColorSlider(strings.rgbBlue, b) { b = it }
                }

                Button(
                    onClick = { vm.setRGB(RGBConfig(effect, r, g, b)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(strings.rgbApply) }
            }
        }
    }
}

@Composable
private fun ColorPreview(effect: LightEffect, r: Int, g: Int, b: Int) {
    Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (effect == LightEffect.COLORFUL) {
                val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta)
                val w = size.width / colors.size
                colors.forEachIndexed { i, col ->
                    drawRect(col, topLeft = Offset(w * i, 0f), size = Size(w, size.height))
                }
            } else if (effect == LightEffect.OFF) {
                drawRect(Color.Black)
            } else {
                drawRect(Color(r / 255f, g / 255f, b / 255f))
            }
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
        )
    }
}

private fun effectLabel(e: LightEffect, s: AppStrings): String = when (e) {
    LightEffect.COLORFUL -> s.rgbEffect
    LightEffect.BREATH_FULLCOLOR -> e.labelEn
    LightEffect.BREATH_SINGLE -> e.labelEn
    LightEffect.ALWAYS_BRIGHT -> e.labelEn
    LightEffect.OFF -> s.rgbOff
}
