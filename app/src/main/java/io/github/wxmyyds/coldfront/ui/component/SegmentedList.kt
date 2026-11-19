@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.flow.collect
import io.github.wxmyyds.coldfront.R
import io.github.wxmyyds.coldfront.ui.theme.optionContainerColor
/**
 * ColdFront 的 MD3E 分段选项列表实现。
 *
 * 组件使用 androidx 原生原语（[SegmentedListItem] / [ListItemDefaults.segmentedShapes] /
 * [ListItemDefaults.segmentedColors]），并遵循 Material 3 Expressive 列表规格：
 *
 * - 分组外角 16dp、内角 4dp，由 segmentedShapes(index, count) 生成。只有可见项不少于 2
 *   个的分组才启用按压形变，pressed/focused 使用 large 16dp，并按主题 motionScheme 补间。
 *   单项分组和独立单行保持 large 16dp，不做形变，只显示涟漪与状态层；
 * - 行间保留分段缝隙；
 * - 行容器使用 surfaceBright，页面使用 surfaceContainer，通过明度形成层级；
 * - 下拉行 trailing 直接显示当前值（bodyMedium + onSurfaceVariant，单行省略），不显示
 *   下三角图标。整行仍可点击，菜单逻辑不变；
 * - 组标题使用 titleSmall + primary；
 * - 整行可点击，勾选或选中时提供 VirtualKey 触感；
 * - 尾随 Switch 只显示状态（onCheckedChange = null），图标色由组件按 SwitchTokens 注入。
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
 * LazyColumn 无法通过 CompositionLocal 跨 item 传递形状，因此需显式传入
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
 * 行配色：容器使用 surfaceBright，正文使用 onSurface，其余槽位使用 onSurfaceVariant。
 *
 * 页面底色是 surfaceContainer（见 theme 的 pageLayerScheme），行容器亮一档，形成明度层级。
 * 选中或开启状态由行内控件（Switch、下拉勾）表达，因此 selected* 保持常态值。
 */
@Composable
fun segmentedRowColors(): ListItemColors {
    val scheme = MaterialTheme.colorScheme
    val container = optionContainerColor(scheme)
    return ListItemDefaults.segmentedColors(
        containerColor = container,
        contentColor = scheme.onSurface,
        leadingContentColor = scheme.onSurfaceVariant,
        trailingContentColor = scheme.onSurfaceVariant,
        overlineContentColor = scheme.onSurfaceVariant,
        supportingContentColor = scheme.onSurfaceVariant,
        disabledContainerColor = container,
        selectedContainerColor = container,
        selectedContentColor = scheme.onSurface,
        selectedLeadingContentColor = scheme.onSurfaceVariant,
        selectedTrailingContentColor = scheme.onSurfaceVariant,
        selectedOverlineContentColor = scheme.onSurfaceVariant,
        selectedSupportingContentColor = scheme.onSurfaceVariant,
    )
}

/**
 * 独立单行或单项分组使用四角 large 16dp。pressed、focused 和 hovered 状态也保持 16dp。
 * 单项分组不做按压缩放，只显示涟漪与状态层。
 */
@Composable
fun staticStandaloneRowShapes(): ListItemShapes = ListItemDefaults.shapes(
    shape = MaterialTheme.shapes.large,
    selectedShape = MaterialTheme.shapes.large,
    pressedShape = MaterialTheme.shapes.large,
    focusedShape = MaterialTheme.shapes.large,
    hoveredShape = MaterialTheme.shapes.large,
)

/**
 * 分组中第 [index] 行（共 [count] 行）：外角 16dp，内角 4dp。
 *
 * [count] 必须是分组中实际可见且可交互的项数。[SegmentedGroup] 会过滤 visible=false 的行；
 * 在 LazyColumn 中手动调用时，由调用方保证这一点：
 * - count <= 1（含独立单行）时使用 [staticStandaloneRowShapes]，不做按压形变；
 * - count >= 2 时，pressed / focused 使用 large 16dp，hovered 使用 medium 12dp。
 *   [SegmentedListItem] 按主题 motionScheme 在这些形状间补间，按下时内角从 4dp 弹到
 *   16dp，形成 M3E 列表的按压形变。
 * 判断只看分组实际可见项数，不看页面、名称或 ID。
 *
 * selectedShape 保持基准形状。选中或开启状态由行内 Switch、下拉勾选表达，因此容器静止时
 * 不额外变形，避免开启行长期保持 16dp 圆角，与相邻行的 4dp 内角错位。
 */
@Composable
fun segmentedRowShapes(index: Int, count: Int): ListItemShapes {
    if (!segmentedGroupPressMorph(count)) return staticStandaloneRowShapes()
    val base = ListItemDefaults.segmentedShapes(index = index, count = count)
    return ListItemDefaults.shapes(
        shape = base.shape,
        selectedShape = base.shape,
        pressedShape = MaterialTheme.shapes.large,
        focusedShape = MaterialTheme.shapes.large,
        hoveredShape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun currentRowShapes(explicit: ListItemShapes?): ListItemShapes =
    explicit ?: LocalSegmentedShapes.current ?: staticStandaloneRowShapes()

/**
 * 按压形变是否启用，只由分组中实际可见的可交互项数决定。
 *
 * - 1 项（含独立单行）时返回 false，不做缩放形变，只显示涟漪和状态层；
 * - 2 项及以上时返回 true，启用 M3E 按压形变。
 *
 * 这是纯函数，可单独测试。调用方统一使用此判断，不按页面、名称或 ID 另行分支。
 * [SegmentedGroup] 只接收可交互行和静态配置面板；状态卡、空态、按钮不计入分组，
 * 因此这里的项数就是可交互选项数，无需再按 onClick 是否为空过滤。
 */
internal fun segmentedGroupPressMorph(itemCount: Int): Boolean = itemCount >= 2

/** 行首图标：24dp + onSurfaceVariant（M3E 列表 leading icon 规格），传 drawable 资源 ID。 */
@Composable
fun RowIcon(@DrawableRes resId: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
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
     * 加入一行。[visible] 为 false 时，该行不计入分组数量。外角 16dp 会自动应用到相邻的
     * 可见行，调用方无需重新计算 index/count。
     */
    fun item(key: Any? = null, visible: Boolean = true, content: @Composable () -> Unit) {
        entries.add(Entry(key ?: entries.size, visible, content))
    }
}

/**
 * 一组选项行。[title] 非空时，会在组上方显示 titleSmall + primary 的小标题。
 * 行数变化时，整组高度会按 motionScheme 的空间动画过渡。
 *
 * 形状和按压规则由 [SegmentedGroupScope] 中实际可见的项数决定：1 项使用静态形状，
 * 不做按压形变；2 项及以上使用统一的表达性按压形状。调用方无需按页面或名称另行判断。
 *
 * [SegmentedGroup] 只接收可交互行和 [SegmentedContainer] 这类静态配置面板。状态卡、
 * 空态和按钮不计入分组，因此这里的项数就是可交互选项数，无需按 onClick 是否为空过滤。
 */
@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: SegmentedGroupScope.() -> Unit,
) {
    val entries = SegmentedGroupScope().apply(content).entries
    val visibleCount = entries.count { it.visible }
    if (visibleCount == 0) return
    val motionScheme = MaterialTheme.motionScheme

    Column(modifier = modifier) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column {
            entries.forEachIndexed { index, entry ->
                val visibleIndex = entries.take(index).count { it.visible }
                val topSpacing by animateDpAsState(
                    targetValue = if (entry.visible && visibleIndex > 0) SegmentedRowGap else 0.dp,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "segmentedRowSpacing",
                )
                val topRadius by animateDpAsState(
                    targetValue = if (visibleIndex == 0) 16.dp else 4.dp,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "segmentedRowTopRadius",
                )
                val bottomRadius by animateDpAsState(
                    targetValue = if (visibleIndex == visibleCount - 1) 16.dp else 4.dp,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "segmentedRowBottomRadius",
                )
                val rowShape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius,
                )
                val morphEnabled = segmentedGroupPressMorph(visibleCount)
                val rowShapes = ListItemDefaults.shapes(
                    shape = rowShape,
                    selectedShape = rowShape,
                    pressedShape = if (morphEnabled) MaterialTheme.shapes.large else rowShape,
                    focusedShape = if (morphEnabled) MaterialTheme.shapes.large else rowShape,
                    hoveredShape = if (morphEnabled) MaterialTheme.shapes.medium else rowShape,
                )
                key(entry.key) {
                    AnimatedVisibility(
                        visible = entry.visible,
                        enter = expandVertically(
                            animationSpec = motionScheme.defaultSpatialSpec<IntSize>(),
                        ) + fadeIn(
                            animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                        ),
                        exit = shrinkVertically(
                            animationSpec = motionScheme.defaultSpatialSpec<IntSize>(),
                        ) + fadeOut(
                            animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                        ),
                    ) {
                        Column {
                            Spacer(Modifier.height(topSpacing))
                            CompositionLocalProvider(LocalSegmentedShapes provides rowShapes) {
                                entry.content()
                            }
                        }
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
    containerColor: Color = optionContainerColor(MaterialTheme.colorScheme),
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
 * 普通行。[onClick] 为 null 时只显示内容；传入回调后整行可点，并提供状态层、涟漪、语义和触感反馈。
 * [summary] 可快速设置次要文字；需要多段内容时使用 [supportingContent]。
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
    interactionSource: MutableInteractionSource? = null,
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
            // 行高超过断点时，默认 verticalAlignment 会改为顶对齐。长文案折行后，行首图标和
            // 尾随开关会贴顶；这里显式设为垂直居中，符合列表规格。
            verticalAlignment = Alignment.CenterVertically,
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
            verticalAlignment = Alignment.CenterVertically,
            interactionSource = interactionSource,
        ) {
            Text(title)
        }
    }
}

/**
 * 开关行。点击整行即可切换状态，checked 重载会提供 toggle 语义和状态层。
 * 尾随 Switch 只显示状态，图标色由组件注入，无需手动设置 tint。
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
            // 开关切换使用 ToggleOn/ToggleOff 专用触感，反馈比通用 VirtualKey 更准确
            haptic.performHapticFeedback(
                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
            onCheckedChange(it)
        },
        shapes = currentRowShapes(shapes),
        modifier = modifier.fillMaxWidth().semantics { role = Role.Switch },
        enabled = enabled,
        colors = colors,
        leadingContent = leadingContent,
        supportingContent = supporting,
        // 长文案折行后，行高可能超过断点，默认对齐会使开关贴近标题。这里始终垂直居中。
        verticalAlignment = Alignment.CenterVertically,
        trailingContent = {
            Switch(
                checked = checked,
                // Switch 只作状态指示，交互由整行处理。enabled 与行同步，避免行置灰时开关仍显示可用态配色
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            if (checked) R.drawable.ms_check_fill1_24
                            else R.drawable.materialsymbols_ic_close_rounded_filled
                        ),
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
 * 下拉选择行。点击整行打开菜单，trailing 显示当前值（[SegmentedTrailingValue]）；
 * supporting 只放可选的 [summary] 说明。
 *
 * 菜单使用 MD3E 表达性组件栈（[DropdownMenuPopup] + [DropdownMenuGroup] +
 * [SelectableDropdownMenuItem]），并参考 KernelSU 的设置下拉交互：选项使用连续分段形状，
 * 彼此贴合且没有间隙；选中项通过展开动画显示勾选图标。菜单锚定在手指按下的位置展开，
 * 选项从按压点生长出来。
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
    val canExpand = enabled && options.isNotEmpty()
    var expanded by remember(canExpand) { mutableStateOf(false) }
    val anchor = remember(canExpand) { DropdownPressAnchor() }
    val interactionSource = remember(canExpand) { MutableInteractionSource() }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val selectedLabel = dropdownSelectedLabel(options, selected, optionLabel)

    LaunchedEffect(interactionSource, anchor) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> anchor.press(interaction)
                is PressInteraction.Release -> anchor.release(interaction.press)
                is PressInteraction.Cancel -> anchor.cancel(interaction.press)
            }
        }
    }
    val dismiss = {
        expanded = false
        anchor.clearPending()
    }

    Box(modifier = modifier) {
        SegmentedRow(
            title = title,
            modifier = Modifier
                .onSizeChanged { rowSize = it }
                // Observe only input origin. Native Release, not the down event, validates a click.
                // No consuming gesture detector or overlay: scrolling/ripples remain native.
                .pointerInput(anchor) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        anchor.beginPointer()
                    }
                }
                .onPreviewKeyEvent {
                    anchor.clearPending()
                    false
                },
            enabled = canExpand,
            onClick = {
                if (canExpand) {
                    anchor.click()
                    expanded = true
                }
            },
            interactionSource = interactionSource,
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            supportingContent = summary?.let { text -> { Text(text) } },
            trailingContent = { SegmentedTrailingValue(selectedLabel) },
        )
        // Pointer positions are physical pixels. Do not mirror either the zero-size anchor's
        // alignment or its offset in RTL; the native popup still handles direction/screen bounds.
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { anchor.position ?: dropdownFallbackAnchor(rowSize, layoutDirection) }
                .size(0.dp),
        ) {
            SingleChoiceDropdownMenu(
                expanded = expanded && canExpand,
                onDismissRequest = dismiss,
                options = options,
                selected = selected,
                onSelect = onSelect,
                optionLabel = optionLabel,
                haptic = haptic,
            )
        }
    }
}

/**
 * 列表项 trailing 标准当前值：bodySmall + primary，层级弱于标题但保持可读；
 * 单行省略、右对齐，垂直居中由行内 verticalAlignment 保证；颜色取当前 ColorScheme，
 * 浅色/深色/动态取色自动适配。整行仍是点击区域，不要单独给它加点击。
 */
@Composable
fun SegmentedTrailingValue(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        // 只定最小可读宽度、不定最大宽度：标题与 trailing 的空间分配交给
        // ListItem 内部 Row（标题 weight=1、trailing 不挤标题），此处不参与分栏。
        modifier = modifier.padding(start = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 下拉行显示值：优先取选项表中的当前选中；选中值不在表内时回退到对选中值本身的
 * 文案映射，保证行内永远显示实际生效状态，不出现空白。
 */
internal fun <T> dropdownSelectedLabel(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
): String = options.firstOrNull { it == selected }?.let(optionLabel) ?: optionLabel(selected)

/** A click and its matching native release may arrive in either coroutine order. */
internal class DropdownPressAnchor {
    var position: IntOffset? by mutableStateOf(null)
        private set
    private var pointer = false
    private var pressed: PressInteraction.Press? = null
    private var releasedPosition: IntOffset? = null
    private var awaitingRelease = false

    fun beginPointer() {
        clearPending()
        pointer = true
    }

    fun press(press: PressInteraction.Press) {
        if (pointer) pressed = press
    }

    fun release(press: PressInteraction.Press) {
        if (!pointer || pressed !== press) return
        pressed = null
        val point = press.pressPosition
        if (!point.x.isFinite() || !point.y.isFinite()) {
            clearPending()
            return
        }
        if (awaitingRelease) {
            position = point.round()
            clearPending()
        } else {
            releasedPosition = point.round()
        }
    }

    fun cancel(press: PressInteraction.Press) {
        if (pressed === press) clearPending()
    }

    fun click() {
        position = releasedPosition
        if (releasedPosition != null) clearPending() else awaitingRelease = pointer
    }

    // Keep the last anchor during the popup's exit animation, but never reuse it on the next click.
    fun clearPending() {
        pointer = false
        pressed = null
        releasedPosition = null
        awaitingRelease = false
    }
}

internal fun dropdownFallbackAnchor(size: IntSize, direction: LayoutDirection): IntOffset =
    IntOffset(if (direction == LayoutDirection.Rtl) size.width else 0, size.height)

/**
 * MD3E 单选下拉菜单，参考 KernelSU 的设置菜单项设计：
 *
 * - 选项连成一体：组容器负责外圆角，项用分段形状（首尾圆角、中间贴合），无间隙；
 * - 选中项：默认 selectable 配色（容器轻微着色 + 文字转强调色），勾选图标带
 *   横向展开 + 淡入动画，作为选中强调；
 * - 按压反馈、涟漪、文字垂直居中、左右内边距与行高全部由 M3 组件默认值提供，
 *   并自动适配本项目的浅色/深色/动态取色 ColorScheme。
 */
@Composable
private fun <T> SingleChoiceDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    haptic: HapticFeedback,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 1),
            // 长菜单可滚动，避免选项溢出屏幕
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            options.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    selected = option == selected,
                    onClick = {
                        onDismissRequest()
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onSelect(option)
                    },
                    text = { Text(optionLabel(option)) },
                    // 连续分段形状：首尾项外圆角，中间项内角贴合
                    shapes = MenuDefaults.itemShape(index, options.size),
                    // 选中勾选图标：组件自带 expandHorizontally + fadeIn 动画
                    selectedLeadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ms_check_fill1_24),
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
            }
        }
    }
}
