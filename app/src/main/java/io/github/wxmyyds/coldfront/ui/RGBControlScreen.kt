package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * RGB 灯效页(MD3E 重设计):
 * - 动态预览卡:呼吸/炫彩带动画,常亮带光晕
 * - 灯效选择即点即发;颜色 = 预设色板 + 彩色轨道 RGB 滑条,显式应用
 * - 初始值回读自设备当前灯效
 */
@Composable
fun RGBControlScreen(vm: CoolerViewModel, onConnect: () -> Unit = {}) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()

    var effect by remember { mutableStateOf(LightEffect.ALWAYS_BRIGHT) }
    var r by remember { mutableIntStateOf(0) }
    var g by remember { mutableIntStateOf(80) }
    var b by remember { mutableIntStateOf(200) }
    var applied by remember { mutableStateOf(false) }

    // 首次收到设备灯效回读时同步本地选择
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(state.rgb) {
        if (!initialized) {
            state.rgb?.let { cfg ->
                effect = cfg.effect
                r = cfg.red
                g = cfg.green
                b = cfg.blue
                initialized = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // MD3E 弹性顶栏
        MediumFlexibleTopAppBar(title = { Text(strings.rgbTitle) })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

        when {
            !state.isConnected -> NotConnectedCard(strings, onConnect)
            state.deviceType?.supportsRgb == false -> NotSupportedCard(strings)
            else -> {
                // ── 动态预览卡 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightPreview(effect, r, g, b, Modifier.weight(1f).fillMaxSize())
                        Spacer(Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val deviceImage = when (state.deviceType) {
                                CoolerDeviceType.JACKET_8_PRO -> io.github.wxmyyds.coldfront.R.drawable.img_cooler_8pro
                                CoolerDeviceType.JACKET_4 -> io.github.wxmyyds.coldfront.R.drawable.img_cooler_4pro
                                CoolerDeviceType.JACKET_6, CoolerDeviceType.JACKET_6_PRO ->
                                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_6pro
                                CoolerDeviceType.JACKET_1 -> io.github.wxmyyds.coldfront.R.drawable.img_cooler_dual
                                CoolerDeviceType.JACKET_2 -> io.github.wxmyyds.coldfront.R.drawable.img_cooler_turbo
                                CoolerDeviceType.JACKET_3 -> io.github.wxmyyds.coldfront.R.drawable.img_cooler_gen3
                                CoolerDeviceType.JACKET_5 -> io.github.wxmyyds.coldfront.R.drawable.img_cooler_5pro
                                else -> 0
                            }
                            if (deviceImage != 0) {
                                Image(
                                    painter = painterResource(deviceImage),
                                    contentDescription = null,
                                    modifier = Modifier.size(96.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AcUnit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(96.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                effectLabel(effect, strings),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                // ── 灯效选择(即点即发) ──
                EffectChips(
                    effect = effect,
                    onEffect = { e ->
                        effect = e
                        vm.setRGB(RGBConfig(e, r, g, b))
                        applied = true
                    },
                )

                // ── 颜色(仅单色系灯效需要) ──
                if (effect == LightEffect.BREATH_SINGLE || effect == LightEffect.ALWAYS_BRIGHT) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(strings.rgbPalette, style = MaterialTheme.typography.titleSmall)
                            PaletteRow(r, g, b) { pr, pg, pb ->
                                r = pr; g = pg; b = pb
                            }

                            Text(strings.rgbCustomColor, style = MaterialTheme.typography.titleSmall)
                            ColorSlider(strings.rgbRed, r, Color.Red) { r = it }
                            ColorSlider(strings.rgbGreen, g, Color.Green) { g = it }
                            ColorSlider(strings.rgbBlue, b, Color.Blue) { b = it }
                        }
                    }

                    Button(
                        onClick = {
                            vm.setRGB(RGBConfig(effect, r, g, b))
                            applied = true
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(r / 255f, g / 255f, b / 255f),
                        ),
                    ) {
                        if (applied) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.rgbSynced)
                        } else {
                            Text(strings.rgbApply)
                        }
                    }
                }
            }
        }
        }
    }
}

// ───────────────────────── 动态预览 ─────────────────────────

@Composable
private fun LightPreview(effect: LightEffect, r: Int, g: Int, b: Int, modifier: Modifier = Modifier) {
    val color = Color(r / 255f, g / 255f, b / 255f)
    val transition = rememberInfiniteTransition(label = "light")
    val breathAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val hueShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "hue",
    )

    Canvas(modifier = modifier.clip(MaterialTheme.shapes.large)) {
        when (effect) {
            LightEffect.OFF -> drawRect(Color(0xFF101418))
            LightEffect.COLORFUL -> {
                // 彩虹流动渐变
                val hues = FloatArray(8) { ((it / 8f) + hueShift) % 1f }
                val colors = hues.map { h -> Color(android.graphics.Color.HSVToColor(floatArrayOf(h * 360f, 0.9f, 1f))) }
                drawRect(Brush.horizontalGradient(colors))
            }
            LightEffect.BREATH_FULLCOLOR -> {
                val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta)
                drawRect(Brush.horizontalGradient(colors), alpha = breathAlpha)
            }
            LightEffect.BREATH_SINGLE -> {
                drawRect(Color(0xFF101418))
                drawRect(color.copy(alpha = breathAlpha))
                glow(color, breathAlpha)
            }
            LightEffect.ALWAYS_BRIGHT -> {
                drawRect(color.copy(alpha = 0.2f))
                drawRect(color)
                glow(color, 1f)
            }
        }
    }
}

/** 中心光晕(LED 灯珠观感) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.glow(color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.9f * alpha), color.copy(alpha = 0.0f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.minDimension * 0.7f,
        ),
        radius = size.minDimension * 0.7f,
        center = Offset(size.width / 2f, size.height / 2f),
        alpha = alpha,
    )
}

// ───────────────────────── 灯效选择 ─────────────────────────

@Composable
private fun EffectChips(effect: LightEffect, onEffect: (LightEffect) -> Unit) {
    val strings = LocalStrings.current
    val entries = listOf(
        LightEffect.ALWAYS_BRIGHT to Icons.Filled.LightMode,
        LightEffect.BREATH_SINGLE to Icons.Filled.Waves,
        LightEffect.COLORFUL to Icons.Filled.Palette,
        LightEffect.BREATH_FULLCOLOR to Icons.Filled.Gradient,
        LightEffect.OFF to Icons.Filled.AcUnit,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(3).forEach { rowEntries ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowEntries.forEach { (e, icon) ->
                    FilterChip(
                        selected = effect == e,
                        onClick = { onEffect(e) },
                        label = { Text(effectLabel(e, strings)) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowEntries.size < 3) Spacer(Modifier.weight((3 - rowEntries.size).toFloat()))
            }
        }
    }
}

private fun effectLabel(e: LightEffect, s: io.github.wxmyyds.coldfront.ui.i18n.AppStrings): String =
    if (s.langIsZh) e.labelZh else e.labelEn

// ───────────────────────── 颜色选择 ─────────────────────────

private data class PresetColor(val label: String, val color: Color)

private val PRESETS = listOf(
    PresetColor("白", Color(0xFFFFFFFF)),
    PresetColor("红", Color(0xFFFF3B30)),
    PresetColor("橙", Color(0xFFFF9500)),
    PresetColor("黄", Color(0xFFFFD60A)),
    PresetColor("绿", Color(0xFF30D158)),
    PresetColor("青", Color(0xFF64D2FF)),
    PresetColor("蓝", Color(0xFF0A84FF)),
    PresetColor("紫", Color(0xFFBF5AF2)),
    PresetColor("粉", Color(0xFFFF375F)),
)

@Composable
private fun PaletteRow(r: Int, g: Int, b: Int, onPick: (Int, Int, Int) -> Unit) {
    val current = Color(r / 255f, g / 255f, b / 255f).toArgb()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PRESETS.forEach { preset ->
            val selected = preset.color.toArgb() == current
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(preset.color)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable {
                        onPick(
                            (preset.color.red * 255).toInt(),
                            (preset.color.green * 255).toInt(),
                            (preset.color.blue * 255).toInt(),
                        )
                    },
            )
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, trackColor: Color, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(trackColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                activeTrackColor = trackColor,
                thumbColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.25f),
            ),
        )
    }
}

// ───────────────────────── 空状态 ─────────────────────────

@Composable
private fun NotConnectedCard(strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(strings.rgbConnectFirst, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onConnect) { Text(strings.rgbGoConnect) }
        }
    }
}

@Composable
private fun NotSupportedCard(strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(strings.rgbNotSupported, style = MaterialTheme.typography.titleMedium)
        }
    }
}
