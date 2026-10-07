package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.R
import io.github.wxmyyds.coldfront.domain.CoolerBleConstants
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.ui.component.AppMotion
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.PageScaffold
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.component.staticStandaloneRowShapes
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.theme.EmphasizedTypography
import io.github.wxmyyds.coldfront.ui.theme.optionContainerColor
import kotlin.math.roundToInt

/**
 * 首页 —— 按 MD3E 七条表达性战术设计:
 *
 * - 战术 2/4(色彩层次 + 容器分组):温度英雄卡用 primaryContainer(制冷中)
 *   ↔ surfaceBright(待机)之间过渡,与选项容器同一档明度，不制造孤立的深色块。
 * - 战术 3(排印引导):温度用 displayLarge + SemiBold 强调。
 * - 战术 5(流体动效):颜色过渡走 MaterialTheme.motionScheme 的 effects spec。
 * - 电源和模式控制沿用 SegmentedSwitchRow 的原生开关与分组形态。
 */
private enum class HomeContentState { Connected, Connecting, Failed, Idle }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()
    val contentState = when (state.connection) {
        ConnectionState.CONNECTED -> HomeContentState.Connected
        ConnectionState.CONNECTING, ConnectionState.DISCOVERING -> HomeContentState.Connecting
        ConnectionState.FAILED -> HomeContentState.Failed
        else -> HomeContentState.Idle
    }
    val connectedScrollState = rememberScrollState()
    val statusScrollState = rememberScrollState()
    val motionScheme = MaterialTheme.motionScheme

    PageScaffold(title = strings.homeTitle) { inner ->
        AnimatedContent(
            targetState = contentState,
            transitionSpec = {
                AppMotion.contentChange(motionScheme).using(
                    SizeTransform(clip = false) { _, _ ->
                        motionScheme.defaultSpatialSpec<IntSize>()
                    },
                )
            },
            label = "homeConnectionContent",
        ) { target ->
            if (target == HomeContentState.Connected) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(connectedScrollState)
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
                        .verticalScroll(statusScrollState)
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (target) {
                        HomeContentState.Connecting -> ConnectingContent(strings)
                        HomeContentState.Failed -> FailedContent(strings, onAddDevice)
                        HomeContentState.Idle -> NotConnectedContent(strings, onAddDevice)
                        HomeContentState.Connected -> Unit
                    }
                }
            }
        }
    }
}

// ───────────────────── 已连接 ─────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedContent(vm: CoolerViewModel, state: CoolerLiveState) {
    val strings = LocalStrings.current

    if (state.telemetryDegraded) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.telemetryDegradedTitle, style = MaterialTheme.typography.titleMedium)
                Text(strings.telemetryDegradedHint, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { vm.reconnectTelemetry(state) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(strings.serviceReconnect) }
            }
        }
    }

    TempHero(state)

    // 官方 Jacket8ProActivityV3.N4 只在限档变化时提示一次(重复上报不重复提示):
    // 同一限档值只弹一次；限档变化,或解除限档后再次受限,才重新提示。
    // 重连会换 connectionSessionId,随之重置已确认记录。
    var acknowledgedLimit by remember(state.connectionSessionId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.connectionSessionId, state.powerLimited) {
        if (!state.powerLimited) acknowledgedLimit = null
    }
    val limit = state.fanLimit
    if (state.powerLimited && limit != null && acknowledgedLimit != limit) {
        AlertDialog(
            onDismissRequest = { acknowledgedLimit = limit },
            title = { Text(strings.homePowerLimitedTitle) },
            text = { Text(strings.homePowerLimitedHint) },
            confirmButton = {
                TextButton(onClick = { acknowledgedLimit = limit }) {
                    Text(strings.homePowerLimitedConfirm)
                }
            },
        )
    }

    if (state.capabilities.hasCoolingSwitch && state.hasConfirmedConfiguration(CoolerBleConstants.COOLING_SWITCH_UUID)) {
        SegmentedSwitchRow(
            title = strings.homeCoolingSwitch,
            summary = strings.homeCoolingSwitchDesc,
            checked = state.coolingOn,
            enabled = state.isConnected && state.capabilities.coolingControl,
            shapes = staticStandaloneRowShapes(),
            onCheckedChange = vm::setCooling,
            leadingContent = { RowIcon(R.drawable.materialsymbols_ic_ac_unit_rounded_filled) },
        )
    }

    // 按设备能力显示带说明的开关:分段选项列表(SegmentedGroup)——
    // 外角 16dp / 内角 4dp / 缝隙 / 触感反馈全部由组件统一处理
    SegmentedGroup {
        item(key = "smart", visible = state.hasConfirmedConfiguration(CoolerBleConstants.AUTO_MODE_CONTROL_UUID) && state.capabilities.smartControl && state.deviceType?.supportsAutoMode == true) {
            SegmentedSwitchRow(
                title = strings.homeSmart,
                summary = strings.homeSmartDesc,
                checked = state.smartOn,
                // 官方 Jacket8ProActivityV3.m6:破坏神开启时温控开关被禁用(并强制取消选中)。
                enabled = state.isConnected && state.coolingAllowsControl && !state.boostOn,
                onCheckedChange = { vm.setSmart(it) },
                leadingContent = { RowIcon(R.drawable.materialsymbols_ic_auto_mode_rounded_filled) },
            )
        }
        item(key = "boost", visible = state.hasConfirmedConfiguration(CoolerBleConstants.BOOST_CONTROL_UUID) && state.capabilities.boostControl) {
            SegmentedSwitchRow(
                title = strings.homeBoost,
                summary = strings.homeBoostDesc,
                checked = state.boostOn,
                // 官方 Jacket8ProActivityV3:破坏神与智能温控互斥(温控开启时禁用),
                // 且供电功率不足时(u5)直接否决开启破坏神的请求。
                enabled = state.isConnected && state.coolingAllowsControl &&
                    !state.smartOn && !state.powerLimited,
                onCheckedChange = { vm.setBoost(it) },
                leadingContent = { RowIcon(R.drawable.materialsymbols_ic_bolt_rounded_filled) },
            )
        }
        item(key = "overcold", visible = state.hasConfirmedConfiguration(CoolerBleConstants.PROTECTION_UUID) && state.capabilities.protectionControl) {
            SegmentedSwitchRow(
                title = strings.homeOvercold,
                summary = strings.homeOvercoldDesc,
                checked = state.overcoldOn,
                onCheckedChange = { vm.setOvercoldProtection(it) },
                leadingContent = { RowIcon(R.drawable.materialsymbols_ic_shield_rounded_filled) },
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
    // 制冷中 → primaryContainer；待机 → surfaceBright，与选项容器同档
    val container by animateColorAsState(
        targetValue = if (state.coolingOn) scheme.primaryContainer
        else optionContainerColor(scheme),
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
            // 单位与数值同行同字号(仅字重不同)后,固定开销是这行宽度的主要变数:
            // 图标 16→14、内边距 10→8、图标间隔 6→4、胶囊间 8→6,固定开销
            // 142dp→110dp,360dp 屏(可用 280dp)三个胶囊整体约 247dp,余量约 33dp。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetricPill(R.drawable.materialsymbols_ic_speed_rounded_filled, "${state.fanRpm ?: "--"}", "RPM", onContainer)
                MetricPill(R.drawable.materialsymbols_ic_bolt_rounded_filled, "${state.powerW ?: "--"}", "W", onContainer)
                state.rssi?.let { rssi ->
                    MetricPill(R.drawable.materialsymbols_ic_bluetooth_connected_rounded_filled, "$rssi", "dBm", onContainer)
                }
            }
        }
    }
}

/**
 * 单个指标胶囊:图标 + 数值 + 单位,同处一行同一胶囊容器。
 *
 * 数值与单位同字号(12sp),只用字重拉开主次(数值 SemiBold / 单位 Medium)、间距 2dp:
 * 胶囊宽度因此吃满行内空白,不会三个小胶囊挤在左侧、右边缘留出一大块。
 *
 * 注意:数值与单位仍是两个 Text——单位字号一降就会在行尾留下过宽的空白,
 * 降字号不是这行腾宽度的手段(真正的手段是固定开销与换行策略)。
 */
@Composable
private fun MetricPill(@DrawableRes resId: Int, value: String, unit: String, content: Color) {
    MetricPillContent(
        icon = {
            Icon(
                painterResource(resId),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
        },
        value = value,
        unit = unit,
        content = content,
    )
}

@Composable
private fun MetricPillContent(icon: @Composable () -> Unit, value: String, unit: String, content: Color) {
    // 胶囊底是对 content 的装饰性淡色叠加:英雄卡容器会在 primaryContainer 与
    // surfaceBright 之间过渡,没有单一角色色能同时适配,故保留 alpha 写法。
    // 但文字与图标不再做 alpha(那直接影响对比度),一律用全强度 content。
    Surface(
        shape = CircleShape,
        color = content.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    value,
                    // tnum:等宽数字,数值位数变化时字形宽度不抖。
                    style = EmphasizedTypography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 制冷档位:强调数字 + 滑条(8 Pro 为 1–8 离散档) */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LevelSection(vm: CoolerViewModel, state: CoolerLiveState, strings: AppStrings) {
    val isGear = state.deviceType?.generation == 8
    val displayedPercent = state.pendingFanPercent ?: state.fanPercent
    val displayedGear = state.fanGear ?: 1
    // 官方把滑条的 maxSelectableProgress 设为功耗限档,超出的拖动会被弹回并提示
    // (`LimitedSeekBar.a.onProgressChanged`)。这里同样保留整条 1–8 轨道,只把可选上限钳住。
    val maxGear = state.fanGearLimit ?: 8

    if (state.isConnected && state.hasConfirmedConfiguration(CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID) && state.capabilities.fanControl && state.coolingAllowsControl && state.smartOn) {
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
                    painterResource(R.drawable.materialsymbols_ic_auto_mode_rounded_filled),
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
    if (!state.manualLevelEnabled) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 尺寸/展开类动画统一从主题取 spec，不在业务代码里硬编码 spring
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()),
        shape = MaterialTheme.shapes.large,
        // 与其他选项行使用同一档容器色；Card 默认会用 surfaceContainerHigh，
        // 两者不一致会让档位区域在页面里显得深一块。
        colors = CardDefaults.cardColors(containerColor = optionContainerColor(MaterialTheme.colorScheme)),
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
                Icon(painterResource(R.drawable.materialsymbols_ic_speed_rounded_filled), contentDescription = null)
                Text(strings.homeLevel, style = MaterialTheme.typography.titleMedium)
                // 设备档位可能随实时回读更新，保持数值即时，避免每次遥测都重启动效。
                Text(
                    if (isGear) strings.homeLevelGear.format(displayedGear)
                    else strings.homeLevelPercent.format(displayedPercent),
                    style = EmphasizedTypography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // MD3E:Slider 走 SliderState(alpha28 起 Slider(value=…) 无状态重载已弃用)。
            // 拖动期间以滑条为准,松手后再由设备回读值同步,避免两边互相打架。
            var dragging by remember { mutableStateOf(false) }
            val gear = displayedGear.toFloat()
            val percent = displayedPercent.toFloat()
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
                        val capped = it.coerceAtMost(maxGear.toFloat())
                        gearSlider.value = capped
                        vm.setFanSpeed(CoolerBleConstants.gearToPercentage8Pro(capped.roundToInt()))
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
            painterResource(R.drawable.materialsymbols_ic_ac_unit_rounded_filled),
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
            color = optionContainerColor(MaterialTheme.colorScheme),
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.materialsymbols_ic_ac_unit_rounded_filled),
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
