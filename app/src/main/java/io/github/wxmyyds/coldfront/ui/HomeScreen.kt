package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import kotlin.math.roundToInt

/**
 * 首页 —— 按 MD3E 七条表达性战术设计:
 *
 * - 战术 7(hero moment,全产品仅此一个):散热开关做成会形变的
 *   [ToggleFloatingActionButton],按下时图标 + 形状 + 颜色一起弹簧过渡。
 * - 战术 2/4(色彩层次 + 容器分组):温度英雄卡用 primaryContainer(制冷中)
 *   ↔ surfaceContainerHighest(待机)之间过渡,最亮表面留给最重要信息。
 * - 战术 3(排印引导):温度用 displayLarge + SemiBold 强调。
 * - 战术 5(流体动效):颜色过渡走 MaterialTheme.motionScheme 的 effects spec。
 * - 开关行用官方 [ListItem](表达性列表),带色调图标容器。
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
                title = { Text(strings.homeTitle) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (connected) {
                // ── hero moment:散热开关形变 FAB ──
                // 已知偏差(有意保留):规范把 ToggleFloatingActionButton 定位为
                // FloatingActionButtonMenu 的载体 + 页面唯一最重要操作,纯 on/off 属 Switch 职责。
                // 这里作为 MD3E 战术 7 的 hero moment 保留:全应用仅此一个 FAB,
                // 位于底部 trailing(Scaffold 默认 16dp 边距),且不与其他主操作竞争。
                ToggleFloatingActionButton(
                    checked = state.coolingOn,
                    onCheckedChange = { vm.setCooling(it) },
                ) {
                    // 形变:图标随 checkedProgress 在中点切换,颜色/尺寸由 animateIcon 弹簧过渡
                    val icon by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Filled.AcUnit
                            else Icons.Filled.PowerSettingsNew
                        }
                    }
                    Icon(
                        painter = rememberVectorPainter(icon),
                        contentDescription = strings.homeCoolingSwitch,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        },
    ) { inner ->
        if (connected) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectedContent(vm, state)
                Spacer(Modifier.height(72.dp)) // 给 FAB 让位
            }
        } else {
            // 未连接/连接中/失败:内容垂直居中,不贴顶
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 24.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoolerArt(
                    state.deviceType,
                    modifier = Modifier.size(64.dp),
                    iconTint = onContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        strings.homeTemp,
                        // 次要文字不再用 onContainer.copy(alpha = 0.72f)——对角色色做透明度
                        // 会拉低小字号的对比度；层级改由字号表达（labelMedium vs displayLarge）。
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer,
                    )
                    Text(
                        state.temperatureText,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        state.deviceName ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer,
                    )
                    Text(
                        // 此处必为已连接;制冷开/关由卡片容器颜色 + 形变 FAB 表达
                        strings.homeConnected,
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPill(Icons.Filled.Speed, "${state.fanRpm ?: "--"} RPM", onContainer)
                MetricPill(Icons.Filled.Bolt, "${state.powerW ?: "--"} W", onContainer)
                MetricPill(Icons.Filled.Bluetooth, strings.homeSignal, onContainer)
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
            .animateContentSize(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(strings.homeLevel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isGear) strings.homeLevelGear.format(levelToGear(state.fanPercent))
                    else strings.homeLevelPercent.format(state.fanPercent),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
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
            LaunchedEffect(gear) { if (!dragging) gearSlider.value = gear }
            LaunchedEffect(percent) { if (!dragging) percentSlider.value = percent }
            if (isGear) {
                Slider(
                    state = gearSlider,
                    onValueChange = {
                        dragging = true
                        vm.setFanSpeed(gearToLevel(it.roundToInt()))
                    },
                    onValueChangeFinished = { dragging = false },
                    enabled = !state.smartOn,
                )
            } else {
                Slider(
                    state = percentSlider,
                    onValueChange = {
                        dragging = true
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
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
