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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSliderState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.ui.component.SegmentedContainer
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedRadioRow
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * 灯效预览与 R/G/B 轨道用的是“设备真实发出的光”，属内容而非 UI 表面：
 * 跟着主题走会让光晕在浅色底下完全看不见，因此这里刻意不走 colorScheme。
 * 除此之外页面不得出现硬编码颜色。
 */
private val PreviewStage = Color.Black
private val PreviewStageContent = Color.White
private val PreviewLedOff = Color(0xFF101418)

/**
 * RGB 灯效页(MD3E):
 * - 动态预览卡:呼吸/炫彩带动画,常亮带光晕
 * - 灯效选择即点即发;颜色 = 预设色板 + 彩色轨道 RGB 滑条,显式应用
 * - 初始值回读自设备当前灯效
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(strings.rgbTitle) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
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
                    colors = CardDefaults.cardColors(containerColor = PreviewStage),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightPreview(effect, r, g, b, Modifier.weight(1f).fillMaxSize())
                        Spacer(Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CoolerArt(
                                state.deviceType,
                                modifier = Modifier.size(96.dp),
                                iconTint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                effectLabel(effect, strings),
                                style = MaterialTheme.typography.labelLarge,
                                color = PreviewStageContent,
                            )
                        }
                    }
                }

                // ── 灯效选择(即点即发) ──
                EffectRows(
                    effect = effect,
                    onEffect = { e ->
                        effect = e
                        vm.setRGB(RGBConfig(e, r, g, b))
                        applied = true
                    },
                )

                // ── 颜色(仅单色系灯效需要) ──
                if (effect == LightEffect.BREATH_SINGLE || effect == LightEffect.ALWAYS_BRIGHT) {
                    val applyColor = Color(r / 255f, g / 255f, b / 255f)

                    SegmentedGroup(title = strings.rgbPalette) {
                        item(key = "palette") {
                            SegmentedContainer {
                                PaletteRow(r, g, b) { pr, pg, pb ->
                                    r = pr; g = pg; b = pb
                                }
                            }
                        }
                    }

                    SegmentedGroup(title = strings.rgbCustomColor) {
                        item(key = "sliders") {
                            SegmentedContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ColorSlider(strings.rgbRed, r, Color.Red) { r = it }
                                    ColorSlider(strings.rgbGreen, g, Color.Green) { g = it }
                                    ColorSlider(strings.rgbBlue, b, Color.Blue) { b = it }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            vm.setRGB(RGBConfig(effect, r, g, b))
                            applied = true
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = applyColor,
                            // 底色是用户选的实际 RGB，contentColor 必须按亮度算：
                            // 默认 onPrimary 近白，选白/黄预设时会白字压白底。
                            // 0.179 是黑/白文字对比度相等的相对亮度分界点。
                            contentColor = if (applyColor.luminance() > 0.179f) {
                                Color.Black
                            } else {
                                Color.White
                            },
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
            LightEffect.OFF -> drawRect(PreviewLedOff)
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
                drawRect(PreviewLedOff)
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

/**
 * 灯效单选:MD3E 单选行组。
 * 替掉原来 5 个无标签的图标 ToggleButton——单选应用 RadioButton 行,
 * 图标退到 trailing 做补充识别,文字标签才是主语义。
 */
@Composable
private fun EffectRows(effect: LightEffect, onEffect: (LightEffect) -> Unit) {
    val strings = LocalStrings.current
    val entries = listOf(
        LightEffect.ALWAYS_BRIGHT to Icons.Filled.LightMode,
        LightEffect.BREATH_SINGLE to Icons.Filled.Waves,
        LightEffect.COLORFUL to Icons.Filled.Palette,
        LightEffect.BREATH_FULLCOLOR to Icons.Filled.Gradient,
        LightEffect.OFF to Icons.Filled.AcUnit,
    )
    SegmentedGroup(title = strings.rgbEffect) {
        entries.forEach { (e, icon) ->
            item(key = e.name) {
                SegmentedRadioRow(
                    title = effectLabel(e, strings),
                    selected = effect == e,
                    onClick = { onEffect(e) },
                    trailingContent = {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

private fun effectLabel(e: LightEffect, s: io.github.wxmyyds.coldfront.ui.i18n.AppStrings): String =
    if (s.langIsZh) e.labelZh else e.labelEn

// ───────────────────────── 颜色选择 ─────────────────────────

private data class PresetColor(val labelZh: String, val labelEn: String, val color: Color)

private val PRESETS = listOf(
    PresetColor("白", "White", Color(0xFFFFFFFF)),
    PresetColor("红", "Red", Color(0xFFFF3B30)),
    PresetColor("橙", "Orange", Color(0xFFFF9500)),
    PresetColor("黄", "Yellow", Color(0xFFFFD60A)),
    PresetColor("绿", "Green", Color(0xFF30D158)),
    PresetColor("青", "Cyan", Color(0xFF64D2FF)),
    PresetColor("蓝", "Blue", Color(0xFF0A84FF)),
    PresetColor("紫", "Purple", Color(0xFFBF5AF2)),
    PresetColor("粉", "Pink", Color(0xFFFF375F)),
)

/** 预设色名跟灯效名同一套双语惯例（langIsZh），不硬编码中文 */
private fun presetLabel(
    p: PresetColor,
    s: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
): String = if (s.langIsZh) p.labelZh else p.labelEn

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteRow(r: Int, g: Int, b: Int, onPick: (Int, Int, Int) -> Unit) {
    val strings = LocalStrings.current
    val current = Color(r / 255f, g / 255f, b / 255f).toArgb()
    // 9 个色块横排会超出手机宽度被挤扇（旧写法 9×38dp + 8×10dp = 422dp），改 FlowRow 自动折行；
    // minimumInteractiveComponentSize() 把触控目标撑到 48dp（视觉仍 40dp），
    // selectable 给出 Role.RadioButton 与选中态语义 + 状态层，替掉裸 clickable。
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PRESETS.forEach { preset ->
            val selected = preset.color.toArgb() == current
            val label = presetLabel(preset, strings)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(preset.color)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            onPick(
                                (preset.color.red * 255).toInt(),
                                (preset.color.green * 255).toInt(),
                                (preset.color.blue * 255).toInt(),
                            )
                        },
                    )
                    .semantics { contentDescription = label },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorSlider(label: String, value: Int, trackColor: Color, onChange: (Int) -> Unit) {
    // alpha28 起 Slider(value=…) 无状态重载已弃用 → 走 SliderState。
    // 拖动期间以滑条为准;松手后再把外部值(点色板预设)同步回来。
    val sliderState = rememberSliderState(value = value.toFloat(), trackRange = 0f..255f)
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) sliderState.value = value.toFloat() }
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
            state = sliderState,
            onValueChange = {
                dragging = true
                onChange(it.toInt())
            },
            onValueChangeFinished = { dragging = false },
            colors = SliderDefaults.colors(
                // 三条轨道就是 R/G/B 通道本色（领域内容）；未激活段不再用 copy(alpha)，
                // 改为向容器色插值，深浅色下都保持可见且不产生非规范色。
                activeTrackColor = trackColor,
                thumbColor = trackColor,
                inactiveTrackColor = lerp(
                    trackColor,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    0.75f,
                ),
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
            Button(onClick = onConnect, shapes = ButtonDefaults.shapes()) { Text(strings.rgbGoConnect) }
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
