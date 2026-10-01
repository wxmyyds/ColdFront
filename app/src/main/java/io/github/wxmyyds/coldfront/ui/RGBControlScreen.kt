package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.domain.RgbWriteStatus
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.SegmentedContainer
import io.github.wxmyyds.coldfront.ui.component.SegmentedDropdownRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * 预览和通道色标保留设备灯光的真实色；RGB 滑条的轨道/滑块同样取通道色，
 * 用颜色本身传达「这条轨道调的是哪个通道」——属数据可视化语义，不是主题角色色。
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
    val writeState by vm.rgbWriteState.collectAsStateWithLifecycle()

    // Save the draft across rotation/tab restoration, but validate the connection identity
    // before rendering so a restored draft can never leak to another device/session.
    var savedEditor by rememberSaveable(stateSaver = RgbEditorSaver) {
        mutableStateOf(RgbEditorState.fromDevice(state))
    }
    val editor = savedEditor.receive(state, writeState)
    val draftConfig = editor.config
    val effect = draftConfig.effect
    val r = draftConfig.red
    val g = draftConfig.green
    val b = draftConfig.blue
    val currentWrite = editor.currentWrite(writeState)
    val currentWriteStatus = currentWrite?.status
    val applied = currentWriteStatus == RgbWriteStatus.SENT &&
        currentWrite?.requestId == editor.explicitApplyRequestId

    LaunchedEffect(state, writeState) {
        savedEditor = savedEditor.receive(state, writeState)
    }
    val sendConfig: (RGBConfig, Boolean) -> Unit = { config, explicitApply ->
        savedEditor = editor.edit(config)
        vm.setRGB(config)
        // setRGB publishes WRITING synchronously; success still requires its GATT completion.
        vm.rgbWriteState.value?.takeIf { it.config == config }?.let {
            savedEditor = savedEditor.submit(it, explicitApply)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(strings.rgbTitle) },
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
                    // extraLarge(28dp) 是全应用唯一的强调形状（战术 1：故意打破周围形状语言），
                    // 其余卡片走默认 medium(12dp)、列表行 large(16dp)，不再出现第三种圆角。
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = PreviewStage),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoolerArt(
                                    state.deviceType,
                                    modifier = Modifier.size(64.dp),
                                    iconTint = PreviewStageContent,
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    effectLabel(effect, strings),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PreviewStageContent,
                                )
                            }
                            LightPreview(effect, r, g, b, Modifier.fillMaxWidth().height(112.dp))
                        }
                    }

                    // ── 灯效选择(即点即发) ──
                    // 选「呼吸」时沿用当前的呼吸变体；从别的灯效切过来默认全彩。
                    val breathVariant =
                        if (effect == LightEffect.BREATH_SINGLE) LightEffect.BREATH_SINGLE
                        else LightEffect.BREATH_FULLCOLOR
                    val applyEffect: (LightEffect) -> Unit = { e ->
                        sendConfig(draftConfig.copy(effect = e), false)
                    }
                    SegmentedGroup {
                        item(key = "effect") {
                            SegmentedDropdownRow(
                                title = strings.rgbEffect,
                                options = LightEffect.selectable,
                                // 下拉里呼吸只有一个入口（BREATH_FULLCOLOR），具体变体由下一行决定
                                selected = effect.uiEffect,
                                onSelect = { e ->
                                    applyEffect(
                                        if (e == LightEffect.BREATH_FULLCOLOR) breathVariant else e
                                    )
                                },
                                optionLabel = { effectLabel(it, strings) },
                                leadingContent = { RowIcon(effectIcon(effect)) },
                            )
                        }
                        // 「呼吸」底下挂一个子选项：单色（0x03，带颜色字节） / 全彩（0x02，不带）
                        item(
                            key = "breathMode",
                            visible = effect.uiEffect == LightEffect.BREATH_FULLCOLOR,
                        ) {
                            SegmentedDropdownRow(
                                title = strings.rgbBreathMode,
                                options = listOf(
                                    LightEffect.BREATH_SINGLE,
                                    LightEffect.BREATH_FULLCOLOR,
                                ),
                                selected = effect,
                                onSelect = applyEffect,
                                optionLabel = {
                                    if (it == LightEffect.BREATH_SINGLE) strings.rgbBreathSingle
                                    else strings.rgbBreathFull
                                },
                            )
                        }
                    }
                    // ── 颜色(单色呼吸与常亮才带颜色字节；全彩呼吸/炫彩/关闭按协议置零) ──
                    if (effect == LightEffect.BREATH_SINGLE || effect == LightEffect.ALWAYS_BRIGHT) {
                        SegmentedGroup(title = strings.rgbPalette) {
                            item(key = "palette") {
                                SegmentedContainer {
                                    PaletteRow(r, g, b) { pr, pg, pb ->
                                        savedEditor = editor.edit(draftConfig.copy(red = pr, green = pg, blue = pb))
                                    }
                                }
                            }
                        }

                        SegmentedGroup(title = strings.rgbCustomColor) {
                            item(key = "sliders") {
                                SegmentedContainer {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ColorSlider(strings.rgbRed, r, Color.Red) {
                                            savedEditor = editor.edit(draftConfig.copy(red = it))
                                        }
                                        ColorSlider(strings.rgbGreen, g, Color.Green) {
                                            savedEditor = editor.edit(draftConfig.copy(green = it))
                                        }
                                        ColorSlider(strings.rgbBlue, b, Color.Blue) {
                                            savedEditor = editor.edit(draftConfig.copy(blue = it))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 即使当前灯效没有颜色面板，也保留写入失败的重试入口。
                    Button(
                        onClick = { sendConfig(draftConfig, true) },
                        enabled = currentWriteStatus != RgbWriteStatus.WRITING,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        when (currentWriteStatus) {
                            RgbWriteStatus.WRITING -> Text(strings.rgbWriting)
                            RgbWriteStatus.SENT -> {
                                if (applied) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(strings.rgbSent)
                            }
                            RgbWriteStatus.FAILED -> Text(strings.rgbWriteFailed)
                            null -> Text(strings.rgbApply)
                        }
                    }
                }
            }
        }
    }
}

private val RgbEditorSaver = listSaver<RgbEditorState, Any>(
    save = {
        listOf(
            it.address.orEmpty(), it.sessionId, it.connected, it.config.effect.name,
            it.config.red, it.config.green, it.config.blue, it.dirty,
            it.submittedRequestId ?: -1L, it.explicitApplyRequestId ?: -1L,
        )
    },
    restore = {
        RgbEditorState(
            address = (it[0] as String).ifEmpty { null },
            sessionId = it[1] as Long,
            connected = it[2] as Boolean,
            config = RGBConfig(LightEffect.valueOf(it[3] as String), it[4] as Int, it[5] as Int, it[6] as Int),
            dirty = it[7] as Boolean,
            submittedRequestId = (it[8] as Long).takeIf { id -> id >= 0 },
            explicitApplyRequestId = (it[9] as Long).takeIf { id -> id >= 0 },
        )
    },
)

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

/** 灯效图标：行首跟着当前灯效走，菜单里不重复放图标。 */
private fun effectIcon(e: LightEffect): ImageVector = when (e) {
    LightEffect.ALWAYS_BRIGHT -> Icons.Filled.LightMode
    // 合并后的「呼吸」（包括设备回报的单色呼吸）统一用波纹图标
    LightEffect.BREATH_FULLCOLOR, LightEffect.BREATH_SINGLE -> Icons.Filled.Waves
    LightEffect.COLORFUL -> Icons.Filled.Palette
    LightEffect.OFF -> Icons.Filled.AcUnit
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
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    LaunchedEffect(value, dragging) { if (!dragging) sliderState.value = value.toFloat() }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            state = sliderState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            onValueChange = {
                dragging = true
                sliderState.value = it
                onChange(it.toInt())
            },
            onValueChangeFinished = { dragging = false },
            // 轨道与滑块取通道色：颜色即语义（红轨调红、绿轨调绿）
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
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
