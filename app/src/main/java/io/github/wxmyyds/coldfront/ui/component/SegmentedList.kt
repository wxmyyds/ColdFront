@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * - 分组外角 16dp、内角 4dp，由 segmentedShapes(index, count) 生成；选中/按下不做圆角形变
 *   （在紧凑分组里会把整组撑散），反馈交给状态层与涟漪；
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
 *         SegmentedDropdownRow(title = …, options = …, selected = …, onSelect = …)
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

/**
 * 行配色：容器 surfaceBright，正文 onSurface，其余槽位 onSurfaceVariant；
 * 禁用态保持容器色，只压内容。
 *
 * selected* 一律等于常态值：分段组里选中/开启已由行内控件（trailing 的 Switch、
 * 下拉菜单里的勾）与涟漪表达，再叠一层 selectedContainerColor 会让该行从组里
 * “跳出来”，破坏分组的整体感。
 */
@Composable
fun segmentedRowColors(): ListItemColors {
    val scheme = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = scheme.surfaceBright,
        contentColor = scheme.onSurface,
        leadingContentColor = scheme.onSurfaceVariant,
        trailingContentColor = scheme.onSurfaceVariant,
        overlineContentColor = scheme.onSurfaceVariant,
        supportingContentColor = scheme.onSurfaceVariant,
        disabledContainerColor = scheme.surfaceBright,
        selectedContainerColor = scheme.surfaceBright,
        selectedContentColor = scheme.onSurface,
        selectedLeadingContentColor = scheme.onSurfaceVariant,
        selectedTrailingContentColor = scheme.onSurfaceVariant,
        selectedOverlineContentColor = scheme.onSurfaceVariant,
        selectedSupportingContentColor = scheme.onSurfaceVariant,
    )
}

/** 独立单行：四角 16dp（segmentedShapes(0,1) 的基形是 4dp，单独一行会显得方）。 */
@Composable
fun standaloneRowShapes(): ListItemShapes = ListItemDefaults.shapes(
    shape = MaterialTheme.shapes.large,
    selectedShape = MaterialTheme.shapes.large,
    pressedShape = MaterialTheme.shapes.large,
    focusedShape = MaterialTheme.shapes.large,
    hoveredShape = MaterialTheme.shapes.large,
)

/**
 * 分组中第 [index] 行（共 [count] 行）：外角 16dp、内角 4dp。
 *
 * selected / pressed / focused / hovered 形状全部压回基准形状：默认 token 会让这些状态
 * 四角变成 16dp（ItemSelected/PressedContainerExpressiveShape = CornerLarge），在紧凑分组里
 * 会把整组“撑散”、与相邻行的 4dp 内角对不上。选中态改由行内控件表达，
 * 按下反馈交给涟漪/状态层（它们仍会被裁到基准形状）。
 */
@Composable
fun segmentedRowShapes(index: Int, count: Int): ListItemShapes {
    if (count <= 1) return standaloneRowShapes()
    val base = ListItemDefaults.segmentedShapes(index = index, count = count)
    return ListItemDefaults.shapes(
        shape = base.shape,
        selectedShape = base.shape,
        pressedShape = base.shape,
        focusedShape = base.shape,
        hoveredShape = base.shape,
    )
}

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

/**
 * 下拉选择行：整行点击弹菜单，trailing 显示当前值 + 下拉箭头，菜单里选中项打勾。
 *
 * 选项多到把页面拉长时用它替掉单选行组（一行 + 一个菜单）。
 * 菜单用稳定的 [DropdownMenu] / [DropdownMenuItem]，不依赖 alpha 线的
 * SelectableDropdownMenuItem；M3E 下它们自动套用表达性菜单样式。
 */
@Composable
fun <T> SegmentedDropdownRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    shapes: ListItemShapes? = null,
    colors: ListItemColors = segmentedRowColors(),
    leadingContent: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it == selected }?.let(optionLabel).orEmpty()

    Box(modifier = modifier) {
        SegmentedRow(
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = { expanded = true },
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedLabel, style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
