package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()
    val connected = state.connection == ConnectionState.CONNECTED

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(strings.homeTitle) },
                actions = {
                    FilledTonalIconButton(onClick = onAddDevice) {
                        Icon(Icons.Filled.Add, contentDescription = strings.devicesAdd)
                    }
                },
            )
        },
        floatingActionButton = {
            if (connected) {
                // ── hero moment:散热开关形变 FAB ──
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.connection) {
                ConnectionState.CONNECTED -> ConnectedContent(vm, state)
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING ->
                    ConnectingContent(strings)
                ConnectionState.FAILED -> FailedContent(strings, onAddDevice)
                else -> NotConnectedContent(strings, onAddDevice)
            }
            if (connected) Spacer(Modifier.height(72.dp)) // 给 FAB 让位
        }
    }
}

// ───────────────────── 已连接 ─────────────────────

@Composable
private fun ConnectedContent(vm: CoolerViewModel, state: CoolerLiveState) {
    val strings = LocalStrings.current

    TempHero(state)

    // 三个带说明的开关:官方 ListItem + 色调图标容器
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column {
            ControlItem(
                icon = Icons.Filled.AutoMode,
                title = strings.homeSmart,
                desc = strings.homeSmartDesc,
                checked = state.smartOn,
                enabled = state.coolingOn,
                onChange = { vm.setSmart(it) },
            )
            ControlItem(
                icon = Icons.Filled.Bolt,
                title = strings.homeBoost,
                desc = strings.homeBoostDesc,
                checked = state.boostOn,
                enabled = state.coolingOn,
                onChange = { vm.setBoost(it) },
            )
            ControlItem(
                icon = Icons.Filled.Shield,
                title = strings.homeOvercold,
                desc = strings.homeOvercoldDesc,
                checked = state.overcoldOn,
                enabled = true,
                onChange = { vm.setOvercoldProtection(it) },
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
        shape = MaterialTheme.shapes.extraLarge,
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
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer.copy(alpha = 0.72f),
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
                        color = onContainer.copy(alpha = 0.72f),
                    )
                    Text(
                        if (state.coolingOn) strings.homeConnected else strings.homeDisconnected,
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
    Surface(shape = CircleShape, color = content.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content.copy(alpha = 0.8f),
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

/** 开关行:官方 ListItem(表达性列表)+ 色调图标容器 */
@Composable
private fun ControlItem(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** 制冷档位:强调数字 + 滑条(8 Pro 为 1–8 离散档) */
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
        shape = MaterialTheme.shapes.extraLarge,
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
            if (isGear) {
                Slider(
                    value = levelToGear(state.fanPercent).toFloat(),
                    onValueChange = { vm.setFanSpeed(gearToLevel(it.roundToInt())) },
                    valueRange = 1f..8f,
                    steps = 6,
                    enabled = !state.smartOn,
                )
            } else {
                Slider(
                    value = state.fanPercent.toFloat(),
                    onValueChange = { vm.setFanSpeed(it.toInt()) },
                    valueRange = 0f..100f,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(strings.homeConnecting, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FailedContent(strings: AppStrings, onAddDevice: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(strings.homeConnectionFailed, style = MaterialTheme.typography.titleLarge)
            Text(
                strings.homeRetryHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddDevice, shapes = ButtonDefaults.shapes()) {
                Text(strings.homeGoScan)
            }
        }
    }
}

@Composable
private fun NotConnectedContent(strings: AppStrings, onAddDevice: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(strings.homeNotConnected, style = MaterialTheme.typography.headlineSmall)
            Text(
                strings.homeNoDeviceHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddDevice, shapes = ButtonDefaults.shapes()) {
                Text(strings.homeGoScan)
            }
        }
    }
}

// ───────────────────── 工具 ─────────────────────

/** 8 Pro:百分比 → 1..8 档 */
private fun levelToGear(percent: Int): Int = ((percent + 12) / 13f).toInt().coerceIn(1, 8)

/** 8 Pro:档位 → 百分比(中心值) */
private fun gearToLevel(gear: Int): Int = ((gear - 0.5f) / 8f * 100f).roundToInt()
