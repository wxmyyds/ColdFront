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
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
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

/** 首页内容状态，用于切换已连接、连接中、失败和空闲界面。 */
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
                // 短窗口仍保持最小高度，内容超出时允许向下滚动。
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

    // 同一限档只提示一次。限档变化或解除后再次受限时会重新提示；connectionSessionId 变化（新连接）会重置确认记录，同会话重组时保留。
    var savedNotice by rememberSaveable(stateSaver = PowerLimitNoticeStateSaver) {
        mutableStateOf(PowerLimitNoticeState(state.connectionSessionId))
    }
    val limit = state.fanLimit
    val notice = savedNotice.normalized(state.connectionSessionId, state.powerLimited, limit)
    // Validate before rendering; persist resets without mutating state during composition.
    LaunchedEffect(state.connectionSessionId, state.powerLimited, limit) {
        savedNotice = savedNotice.normalized(state.connectionSessionId, state.powerLimited, limit)
    }
    if (notice.shouldShow(state.powerLimited, limit)) {
        AlertDialog(
            onDismissRequest = { savedNotice = notice.copy(acknowledgedLimit = limit) },
            title = { Text(strings.homePowerLimitedTitle) },
            text = { Text(strings.homePowerLimitedHint) },
            confirmButton = {
                TextButton(onClick = { savedNotice = notice.copy(acknowledgedLimit = limit) }) {
                    Text(strings.homePowerLimitedConfirm)
                }
            },
        )
    }

    if (state.capabilities.hasCoolingSwitch && state.hasConfirmedConfiguration(CoolerBleConstants.COOLING_SWITCH_UUID)) {
        // 6 Pro's Hall register is decoded independently, but its official UI visibility is
        // version/layout dependent. Do not invent a new homepage setting for it here.
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

    // 按设备能力显示分段开关。组件统一处理分组形状、间距和触感反馈。
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

/**
 * 温度卡：制冷时使用 primaryContainer，待机时使用 surfaceBright；温度采用 displayLarge 和 SemiBold，
 * 颜色过渡使用主题 motionScheme 的 effects spec。
 */
@Composable
private fun TempHero(state: CoolerLiveState) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    // 制冷时使用 primaryContainer，待机时使用与选项相同的 surfaceBright。
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
            // 固定开销从 142dp 降到 110dp：图标从 16dp 缩到 14dp，内边距从 10dp 缩到 8dp，
            // 图标间距从 6dp 缩到 4dp，胶囊间距从 8dp 缩到 6dp。360dp 屏幕可用宽度为 280dp，
            // 三个胶囊约占 247dp，余量约 33dp。
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
 * 单个指标胶囊：图标、数值和单位位于同一容器。数值与单位均为 12sp，仅字重不同，二者间距 2dp。
 * 保持同字号可利用行内空白，避免三个胶囊挤在左侧；单独缩小单位字号会在行尾留下空白，
 * 因此通过降低固定开销和换行策略适配。
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
    // 仅底色使用透明度，文字和图标保持完整对比度。
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
                    // tnum 让数值位数变化时保持稳定宽度。
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

/** 制冷档位：8 Pro/6 Pro+ 使用 1–9 档，6 Pro 使用 40–80 raw。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LevelSection(vm: CoolerViewModel, state: CoolerLiveState, strings: AppStrings) {
    val isProPlus = state.deviceType == CoolerDeviceType.JACKET_6_PRO_PLUS
    val isGear = state.deviceType?.generation == 8 || isProPlus
    val isJacket6Pro = state.deviceType == CoolerDeviceType.JACKET_6_PRO
    val displayedPercent = state.pendingFanPercent ?: state.fanPercent
    val displayedRaw = state.pendingFanPercent
        ?.let { CoolerBleConstants.percentageToRaw(it, CoolerDeviceType.JACKET_6_PRO) }
        ?: state.fanRaw?.takeIf { isJacket6Pro }
        ?: CoolerBleConstants.percentageToRaw(displayedPercent, CoolerDeviceType.JACKET_6_PRO)
    val displayedGear = state.fanGear ?: 1
    // 官方将 maxSelectableProgress 设为限档值，超出后会弹回并提示；这里保留完整 1–9 档轨道，只钳制可选上限。
    val maxGear = state.fanGearLimit ?: 9

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
            // 尺寸动画使用主题 spec，避免在业务代码中固定 spring。
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
                // 档位随遥测回读更新时直接刷新数值，不重新启动动效。
                Text(
                    when {
                        isGear -> strings.homeLevelGear.format(displayedGear)
                        isJacket6Pro -> strings.homeLevelRaw.format(displayedRaw)
                        else -> strings.homeLevelPercent.format(displayedPercent)
                    },
                    style = EmphasizedTypography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // 拖动时显示滑条值，松手后再与设备回读同步。
            var dragging by remember { mutableStateOf(false) }
            val gear = displayedGear.toFloat()
            val percent = displayedPercent.toFloat()
            val gearSlider = rememberSliderState(value = gear, steps = 7, trackRange = 1f..9f)
            val percentSlider = rememberSliderState(value = percent, trackRange = 0f..100f)
            val rawSlider = rememberSliderState(value = displayedRaw.toFloat(), steps = 39, trackRange = 40f..80f)
            LaunchedEffect(gear, dragging) { if (!dragging) gearSlider.value = gear }
            LaunchedEffect(percent, dragging) { if (!dragging) percentSlider.value = percent }
            LaunchedEffect(displayedRaw, dragging) { if (!dragging) rawSlider.value = displayedRaw.toFloat() }
            if (isJacket6Pro) {
                Slider(
                    state = rawSlider,
                    modifier = Modifier.semantics {
                        contentDescription = strings.homeLevel
                        stateDescription = strings.homeLevelRaw.format(rawSlider.value.roundToInt())
                    },
                    onValueChange = {
                        dragging = true
                        rawSlider.value = it
                        vm.setFanRaw(it.roundToInt())
                    },
                    onValueChangeFinished = { dragging = false },
                    enabled = !state.smartOn && !state.boostOn,
                )
            } else if (isGear) {
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
                        vm.setFanSpeed(
                            if (isProPlus) {
                                CoolerBleConstants.gearToPercentage6ProPlus(capped.roundToInt())
                            } else {
                                CoolerBleConstants.gearToPercentage8Pro(capped.roundToInt())
                            },
                        )
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
        // 使用圆形底色图标，避免空状态变成整块卡片。
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
