@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * MD3E 分段选项列表 —— ColdFront 自有实现。
 *
 * 全部基于 androidx 原生原语（[SegmentedListItem] / [ListItemDefaults.segmentedShapes] /
 * [ListItemDefaults.segmentedColors]），观感对齐 Material 3 Expressive 列表规格：
 *
 * - 分组外角 16dp、内角 4dp，由 segmentedShapes(index, count) 生成，含按下/选中形变；
 * - 行间留分段缝隙；
 * - 行容器用 surfaceBright，页面用 background —— 靠明度分层，不靠阴影；
 * - 组标题 titleSmall + primary；
 * - 整行可点，勾选/选中带 VirtualKey 触感；
 * - 尾随 Switch 只做视觉指示（onCheckedChange = null），图标色由组件按 SwitchTokens 注入。
 *
 * 用法：
 * ```
 * SegmentedGroup(title = strings.settingsTheme) {
 *     item(key = "dynamic") {
 *         SegmentedSwitchRow(title = …, checked = …, onCheckedChange = …)
 *     }
 *     item(key = "dark-system") {
 *         SegmentedRadioRow(title = …, selected = …, onClick = …)
 *     }
 * }
 * ```
 * LazyColumn 里没法用 CompositionLocal 跨 item 传形状，改为显式传
 * `shapes = segmentedRowShapes(index, count)`。
 */

/**
 * 行间分段缝隙。alpha28 尚未公开 ListItemDefaults.SegmentedGap，此处取等值 2dp；
 * LazyColumn 里无法用 [SegmentedGroup]，直接用这个常量当 spacedBy 就能跟分组对齐。
 */
val SegmentedRowGap = 2.dp

/** 父级注入本行在分组中的形状，子行因此不需要知道 index/count。 */
val LocalSegmentedShapes = compositionLocalOf<ListItemShapes?> { null }

/** 行配色：容器 surfaceBright，次要文字 onSurfaceVariant；禁用态保持容器色，只压内容。 */
@Composable
fun segmentedRowColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** 独立单行：四角 16dp（segmentedShapes(0,1) 的基形是 4dp，单独一行会显得方）。 */
@Composable
fun standaloneRowShapes(): ListItemShapes =
    ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)

/** 分组中第 [index] 行（共 [count] 行）：外角 16dp、内角 4dp。 */
@Composable
fun segmentedRowShapes(index: Int, count: Int): ListItemShapes =
    if (count <= 1) standaloneRowShapes()
    else ListItemDefaults.segmentedShapes(index = index, count = count)

@Composable
private fun currentRowShapes(explicit: ListItemShapes?): ListItemShapes =
    explicit ?: LocalSegmentedShapes.current ?: standaloneRowShapes()

/** 行首图标：24dp + onSurfaceVariant（M3E 列表 leading icon 规格）。 */
@Composable
fun RowIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(24.dp),
    )
}

// ───────────────────── 分组容器 ─────────────────────

@DslMarker
annotation class SegmentedGroupDsl

@SegmentedGroupDsl
class SegmentedGroupScope {
    internal class Entry(
        val key: Any?,
        val visible: Boolean,
        val content: @Composable () -> Unit,
    )

    internal val entries = mutableListOf<Entry>()

    /**
     * 加入一行。[visible] 为 false 的行不参与分组计数——
     * 外角 16dp 会自动落到相邻的可见行上，不需要调用方重算 index/count。
     */
    fun item(key: Any? = null, visible: Boolean = true, content: @Composable () -> Unit) {
        entries.add(Entry(key ?: entries.size, visible, content))
    }
}

/**
 * 一组选项行。[title] 非空时在组上方渲染 titleSmall + primary 的小标题。
 * 行的增减会让整组高度以 motionScheme 的空间动画过渡。
 */
@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: SegmentedGroupScope.() -> Unit,
) {
    val visible = SegmentedGroupScope().apply(content).entries.filter { it.visible }
    if (visible.isEmpty()) return

    Column(modifier = modifier) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier.animateContentSize(
                MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>(),
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentedRowGap),
        ) {
            visible.forEachIndexed { index, entry ->
                key(entry.key) {
                    CompositionLocalProvider(
                        LocalSegmentedShapes provides segmentedRowShapes(index, visible.size),
                    ) {
                        entry.content()
                    }
                }
            }
        }
    }
}

/**
 * 非行内容的分组容器（色板、滑条、自定义面板），与行共用形状与容器色，
 * 因此可以直接混在 [SegmentedGroup] 里而不破坏圆角节奏。
 */
@Composable
fun SegmentedContainer(
    modifier: Modifier = Modifier,
    shapes: ListItemShapes? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = currentRowShapes(shapes).shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

// ───────────────────── 行 ─────────────────────

/**
 * 普通行：[onClick] 为 null 时纯展示，非 null 时整行可点（自带状态层/涟漪/语义 + 触感）。
 * [summary] 是次要文字的便捷写法，需要多段内容时改传 [supportingContent]。
 */
@Composable
fun SegmentedRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    overline: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    shapes: ListItemShapes? = null,
    colors: ListItemColors = segmentedRowColors(),
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val rowShapes = currentRowShapes(shapes)
    val supporting: (@Composable () -> Unit)? =
        supportingContent ?: summary?.let { text -> { Text(text) } }
    val overlineSlot: (@Composable () -> Unit)? = overline?.let { text -> { Text(text) } }

    if (onClick == null) {
        SegmentedListItem(
            shapes = rowShapes,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            colors = colors,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineSlot,
            supportingContent = supporting,
        ) {
            Text(title)
        }
    } else {
        SegmentedListItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            },
            shapes = rowShapes,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            colors = colors,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineSlot,
            supportingContent = supporting,
        ) {
            Text(title)
        }
    }
}

/**
 * 开关行：整行点击即切换（checked 重载提供 toggle 语义与状态层），
 * 尾随 Switch 是纯指示器，图标色由组件注入，不要手写 tint。
 */
@Composable
fun SegmentedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    shapes: ListItemShapes? = null,
    colors: ListItemColors = segmentedRowColors(),
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val supporting: (@Composable () -> Unit)? =
        supportingContent ?: summary?.let { text -> { Text(text) } }

    SegmentedListItem(
        checked = checked,
        onCheckedChange = {
            // 开关用专用的 ToggleOn/ToggleOff 触感，比通用 VirtualKey 反馈更准
            haptic.performHapticFeedback(
                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
            onCheckedChange(it)
        },
        shapes = currentRowShapes(shapes),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        leadingContent = leadingContent,
        supportingContent = supporting,
        trailingContent = {
            Switch(
                checked = checked,
                // 指示器：交互交给整行；enabled 与行一致，否则行置灰时开关仍是可用态配色
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    ) {
        Text(title)
    }
}

/** 单选行：leading 为 RadioButton（非交互，点击由整行承担），选中时形状形变到 16dp。 */
@Composable
fun SegmentedRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    shapes: ListItemShapes? = null,
    colors: ListItemColors = segmentedRowColors(),
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val supporting: (@Composable () -> Unit)? =
        supportingContent ?: summary?.let { text -> { Text(text) } }

    SegmentedListItem(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        shapes = currentRowShapes(shapes),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        leadingContent = {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        },
        trailingContent = trailingContent,
        supportingContent = supporting,
    ) {
        Text(title)
    }
}
