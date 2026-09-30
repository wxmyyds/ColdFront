package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3E 圆角刻度(官方 corner radius scale:0/4/8/12/16/20/28/32/48/full)。
 * Compose 的 Shapes 只有 5 档,按官方刻度取值:
 * extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28。
 * 组件默认按钮为 full(胶囊),由 ButtonDefaults.shapes() 提供形变。
 *
 * extraLarge 取 28 是有意选择：设计侧 corner radius scale 的 extra large = 28dp，
 * 而 Compose 官方模板/示例给的是 24dp（两者并存，官方未统一）。本项目按设计侧取 28，
 * 仅用于 RGB 灯效预览卡这类需要“跳出周围形状语言”的强调元素（战术 1）；
 * 普通卡片一律走组件默认 medium(12dp)，列表行走 large(16dp)。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
