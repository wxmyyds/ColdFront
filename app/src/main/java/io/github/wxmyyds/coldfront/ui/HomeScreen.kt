package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.LocalGlassHazeState
import io.github.wxmyyds.coldfront.ui.component.LocalInterfaceBlur
import io.github.wxmyyds.coldfront.ui.component.glassEffect
import io.github.wxmyyds.coldfront.ui.component.glassSource
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.component.staticStandaloneRowShapes
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.theme.EmphasizedTypography
import kotlin.math.roundToInt

/**
 * 首页 —— 按 MD3E 七条表达性战术设计:
 *
 * - 战术 2/4(色彩层次 + 容器分组):温度英雄卡用 primaryContainer(制冷中)
 *   ↔ surfaceContainerHighest(待机)之间过渡,最亮表面留给最重要信息。
 * - 战术 3(排印引导):温度用 displayLarge + SemiBold 强调。
 * - 战术 5(流体动效):颜色过渡走 MaterialTheme.motionScheme 的 effects spec。
 * - 电源和模式控制沿用 SegmentedSwitchRow 的原生开关与分组形态。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()
    val connected = state.connection == ConnectionState.CONNECTED
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // MD3E 小顶栏:标题用组件默认的 TitleLarge(旧写法 headlineSmall 24sp 超出小顶栏规格),
            // 容器色不再强制透明——规范要求滚动后容器变为 surfaceContainer。
            TopAppBar(
                modifier = Modifier.glassEffect(LocalGlassHazeState.current, LocalInterfaceBlur.current),
                title = { Text(strings.homeTitle) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (LocalInterfaceBlur.current) 0.62f else 1f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (LocalInterfaceBlur.current) 0.62f else 1f),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        if (connected) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .glassSource(LocalGlassHazeState.current)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectedContent(vm, state)
            }
        } else {
            // 保留视口最小高度以正常居中，短窗口允许内容向下展开并滚动。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .glassSource(LocalGlassHazeState.current)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (state.connection) {
                    ConnectionState.CONNECTING, ConnectionState.DISCOVERING ->
                        ConnectingContent(strings)
                    ConnectionState.FAILED -> FailedContent(strings, onAddDevice)
                    else -> NotConnectedContent(strings, onAddDevice)
                }
            }
        }
    }
}

// ───────────────────── 已连接 ─────────────────────

@Composable
private fun ConnectedContent(vm: CoolerViewModel, state: CoolerLiveState) {
    val strings = LocalStrings.current

    TempHero(state)

    SegmentedSwitchRow(
        title = strings.homeCoolingSwitch,
        summary = strings.homeCoolingSwitchDesc,
        checked = state.coolingOn,
        shapes = staticStandaloneRowShapes(),
        onCheckedChange = vm::setCooling,
        leadingContent = { RowIcon(Icons.Filled.AcUnit) },
    )

    // 三个带说明的开关:分段选项列表(SegmentedGroup)——
    // 外角 16dp / 内角 4dp / 缝隙 / 触感反馈全部由组件统一处理
    SegmentedGroup {
        item(key = "smart") {
            SegmentedSwitchRow(
                title = strings.homeSmart,
                summary = strings.homeSmartDesc,
                checked = state.smartOn,
                enabled = state.coolingOn,
                onCheckedChange = { vm.setSmart(it) },
                leadingContent = { RowIcon(Icons.Filled.AutoMode) },
            )
        }
        item(key = "boost") {
            SegmentedSwitchRow(
                title = strings.homeBoost,
                summary = strings.homeBoostDesc,
                checked = state.boostOn,
                enabled = state.coolingOn,
                onCheckedChange = { vm.setBoost(it) },
                leadingContent = { RowIcon(Icons.Filled.Bolt) },
            )
        }
        item(key = "overcold") {
            SegmentedSwitchRow(
                title = strings.homeOvercold,
                summary = strings.homeOvercoldDesc,
                checked = state.overcoldOn,
                onCheckedChange = { vm.setOvercoldProtection(it) },
                leadingContent = { RowIcon(Icons.Filled.Shield) },
            )
        }
    }

    LevelSection(vm, state, strings)
}

/** 温度英雄卡:表面容器分层 + 强调排印 + 指标胶囊 */
@Composable
private fun TempHero(state: CoolerLiveState) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    // 制冷中 → primaryContainer;待机 → surfaceContainerHighest(战术 2/4)
    val container by animateColorAsState(
        targetValue = if (state.coolingOn) scheme.primaryContainer
        else scheme.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>(),
        label = "heroContainer",
    )
    val onContainer = if (state.coolingOn) scheme.onPrimaryContainer else scheme.onSurface

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoolerArt(
                    state.deviceType,
                    modifier = Modifier.size(64.dp),
                    iconTint = onContainer,
                )
                Column {
                    Text(
                        state.deviceName ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer,
                    )
                    Text(
                        strings.homeConnected,
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    strings.homeTemp,
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer,
                )
                Text(
                    state.temperatureText,
                    style = EmphasizedTypography.displayLarge,
                    color = onContainer,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricPill(Icons.Filled.Speed, "${state.fanRpm ?: "--"} RPM", onContainer)
                MetricPill(Icons.Filled.Bolt, "${state.powerW ?: "--"} W", onContainer)
                state.rssi?.let { rssi ->
                    MetricPill(Icons.Filled.Bluetooth, "$rssi dBm", onContainer)
                }
            }
        }
    }
}

@Composable
private fun MetricPill(icon: ImageVector, text: String, content: Color) {
    // 胶囊底是对 content 的装饰性淡色叠加：英雄卡容器会在 primaryContainer 与
    // surfaceContainerHighest 之间过渡，没有单一角色色能同时适配，故保留 alpha 写法。
    // 但文字与图标不再做 alpha（那直接影响对比度），一律用全强度 content。
    Surface(shape = CircleShape, color = content.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

/** 制冷档位:强调数字 + 滑条(8 Pro 为 1–8 离散档) */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LevelSection(vm: CoolerViewModel, state: CoolerLiveState, strings: AppStrings) {
    val type = state.deviceType
    val isGear = type?.generation == 8

    if (state.coolingOn && state.smartOn) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.AutoMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    strings.homeSmartActiveLevel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        return
    }
    if (!state.coolingOn) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 尺寸/展开类动画统一从主题取 spec，不在业务代码里硬编码 spring
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Speed, contentDescription = null)
                Text(strings.homeLevel, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isGear) strings.homeLevelGear.format(levelToGear(state.fanPercent))
                    else strings.homeLevelPercent.format(state.fanPercent),
                    style = EmphasizedTypography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // MD3E:Slider 走 SliderState(alpha28 起 Slider(value=…) 无状态重载已弃用)。
            // 拖动期间以滑条为准,松手后再由设备回读值同步,避免两边互相打架。
            var dragging by remember { mutableStateOf(false) }
            val gear = levelToGear(state.fanPercent).toFloat()
            val percent = state.fanPercent.toFloat()
            val gearSlider = rememberSliderState(value = gear, steps = 6, trackRange = 1f..8f)
            val percentSlider = rememberSliderState(value = percent, trackRange = 0f..100f)
            LaunchedEffect(gear, dragging) { if (!dragging) gearSlider.value = gear }
            LaunchedEffect(percent, dragging) { if (!dragging) percentSlider.value = percent }
            if (isGear) {
                Slider(
                    state = gearSlider,
                    modifier = Modifier.semantics {
                        contentDescription = strings.homeLevel
                        stateDescription = strings.homeLevelGear.format(gearSlider.value.roundToInt())
                    },
                    onValueChange = {
                        dragging = true
                        gearSlider.value = it
                        vm.setFanSpeed(gearToLevel(it.roundToInt()))
                    },
                    onValueChangeFinished = { dragging = false },
                    enabled = !state.smartOn,
                )
            } else {
                Slider(
                    state = percentSlider,
                    modifier = Modifier.semantics {
                        contentDescription = strings.homeLevel
                        stateDescription = strings.homeLevelPercent.format(percentSlider.value.toInt())
                    },
                    onValueChange = {
                        dragging = true
                        percentSlider.value = it
                        vm.setFanSpeed(it.toInt())
                    },
                    onValueChangeFinished = { dragging = false },
                    enabled = !state.smartOn,
                )
            }
        }
    }
}

// ───────────────────── 非连接态 ─────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectingContent(strings: AppStrings) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator(Modifier.size(40.dp))
        Text(strings.homeConnecting, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun FailedContent(strings: AppStrings, onAddDevice: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.AcUnit,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            strings.homeConnectionFailed,
            style = EmphasizedTypography.headlineSmall,
        )
        Text(
            strings.homeRetryHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAddDevice, shapes = ButtonDefaults.shapes()) {
            Text(strings.homeGoScan)
        }
    }
}

@Composable
private fun NotConnectedContent(strings: AppStrings, onAddDevice: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 色调圆底图标章(参考图的克制风格,不用整块灰卡)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AcUnit,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            strings.homeNotConnected,
            style = EmphasizedTypography.headlineSmall,
        )
        Text(
            strings.homeNoDeviceHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAddDevice, shapes = ButtonDefaults.shapes()) {
            Text(strings.homeGoScan)
        }
    }
}

// ───────────────────── 工具 ─────────────────────

/** 8 Pro:百分比 → 1..8 档 */
private fun levelToGear(percent: Int): Int = ((percent + 12) / 13f).toInt().coerceIn(1, 8)

/** 8 Pro:档位 → 百分比(中心值) */
private fun gearToLevel(gear: Int): Int = ((gear - 0.5f) / 8f * 100f).roundToInt()
