package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import kotlin.math.roundToInt

/**
 * 首页 —— 冷却驾驶舱(MD3E):
 * - 温度仪表盘:动画弧线(冷蓝→热红渐变)+ 巨型数字
 * - 悬浮工具栏 [HorizontalFloatingToolbar](MD3E 签名组件):散热/智能/破坏神快速开关
 * - 制冷档位大卡 + 过冷保护开关卡
 */
@Composable
fun HomeScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MediumFlexibleTopAppBar(
            title = { Text(strings.homeTitle) },
            actions = {
                androidx.compose.material3.IconButton(onClick = onAddDevice) {
                    Icon(Icons.Filled.Add, contentDescription = strings.devicesAdd)
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.connection) {
                ConnectionState.CONNECTED -> ConnectedCockpit(vm, state)
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING ->
                    ConnectingCard(strings)
                ConnectionState.FAILED -> FailedCard(strings, onAddDevice)
                else -> NotConnectedHero(strings, onAddDevice)
            }
        }
    }
}

// ───────────────────── 连接态:驾驶舱 ─────────────────────

@Composable
private fun ConnectedCockpit(vm: CoolerViewModel, state: CoolerLiveState) {
    val strings = LocalStrings.current

    // ── 温度仪表盘英雄卡 ──
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1116)),
    ) {
        Column(
            Modifier.padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TempGauge(state)
            Spacer(Modifier.height(20.dp))
            InfoChipRow(state)
        }
    }

    // ── MD3E 悬浮工具栏:三个高频开关 ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        QuickToolbar(vm, state, strings)
    }

    // ── 制冷档位 ──
    LevelCard(vm, state, strings)

    // ── 过冷保护(带说明文字) ──
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(strings.homeOvercold, style = MaterialTheme.typography.titleSmall)
                Text(
                    strings.homeOvercoldDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.overcoldOn,
                onCheckedChange = { vm.setOvercoldProtection(it) },
            )
        }
    }
}

/** 温度仪表盘:环 + 弧(弹簧动画)+ 中心大数字 */
@Composable
private fun TempGauge(state: CoolerLiveState) {
    val strings = LocalStrings.current
    val temp = state.temperatureC
    // -10..50°C → 0..1
    val target = (((temp ?: 20f) + 10f) / 60f).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "gauge",
    )

    Box(
        modifier = Modifier.size(236.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            // 底环
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // 温度弧:冷蓝 → 青 → 热红,随温度增长
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF29B6F6),
                        Color(0xFF4DD0E1),
                        Color(0xFFFF7043),
                        Color(0xFFFF5252),
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.temperatureText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                strings.homeTemp,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/** 指标胶囊行:产品图 + RPM / 功率 / 信号 */
@Composable
private fun InfoChipRow(state: CoolerLiveState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoolerArt(state.deviceType, modifier = Modifier.size(44.dp))
        InfoChip(Icons.Filled.Speed, "${state.fanRpm ?: "--"} RPM")
        InfoChip(Icons.Filled.Bolt, "${state.powerW ?: "--"} W")
        InfoChip(Icons.Filled.AcUnit, "${state.rssi} dBm")
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

/** MD3E 悬浮工具栏:散热 / 智能温控 / 破坏神 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickToolbar(vm: CoolerViewModel, state: CoolerLiveState, strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier.align(Alignment.CenterVertically),
    ) {
        IconToggleButton(
            checked = state.coolingOn,
            onCheckedChange = { vm.setCooling(it) },
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = strings.homeCoolingSwitch)
        }
        IconToggleButton(
            checked = state.smartOn,
            onCheckedChange = { vm.setSmart(it) },
        ) {
            Icon(Icons.Filled.AutoMode, contentDescription = strings.homeSmart)
        }
        IconToggleButton(
            checked = state.boostOn,
            onCheckedChange = { vm.setBoost(it) },
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = strings.homeBoost)
        }
    }
}

/** 制冷档位大卡:大数字 + 档位滑条(8 Pro 离散 8 档) */
@Composable
private fun LevelCard(vm: CoolerViewModel, state: CoolerLiveState, strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings) {
    val type = state.deviceType
    val isGear = type?.generation == 8
    val enabled = state.coolingOn && !state.smartOn

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(20.dp),
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
                    style = MaterialTheme.typography.headlineMedium,
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
                    enabled = enabled,
                )
            } else {
                Slider(
                    value = state.fanPercent.toFloat(),
                    onValueChange = { vm.setFanSpeed(it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = enabled,
                )
            }
            if (state.coolingOn && state.smartOn) {
                Text(
                    strings.homeSmartActiveLevel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ───────────────────── 非连接态 ─────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectingCard(strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoadingIndicator(Modifier.size(40.dp))
            Text(strings.homeConnecting, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FailedCard(
    strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
    onAddDevice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(strings.homeConnectionFailed, style = MaterialTheme.typography.titleMedium)
            Text(
                strings.homeRetryHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddDevice,
                shapes = ButtonDefaults.shapes(),
            ) { Text(strings.homeGoScan) }
        }
    }
}

@Composable
private fun NotConnectedHero(
    strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
    onAddDevice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1116)),
    ) {
        Column(
            Modifier.padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(112.dp),
                tint = Color(0xFF64D2FF),
            )
            Text(
                strings.homeNotConnected,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Button(
                onClick = onAddDevice,
                shapes = ButtonDefaults.shapes(),
            ) { Text(strings.homeGoScan) }
        }
    }
}

// ───────────────────── 工具 ─────────────────────

/** 8 Pro:百分比 → 1..8 档 */
private fun levelToGear(percent: Int): Int = ((percent + 12) / 13f).toInt().coerceIn(1, 8)

/** 8 Pro:档位 → 百分比(中心值) */
private fun gearToLevel(gear: Int): Int = ((gear - 0.5f) / 8f * 100f).roundToInt()
