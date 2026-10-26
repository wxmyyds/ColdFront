package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3E 的圆角刻度为 0/4/8/12/16/20/28/32/48/full。
 * Compose 的 Shapes 只有 5 档，本项目使用 extraSmall 4、small 8、medium 12、large 16、extraLarge 28dp。
 * 默认按钮的 full 胶囊形状由 ButtonDefaults.shapes() 提供。
 *
 * extraLarge 取 28dp，因为设计侧 corner radius scale 将其定义为 28dp；Compose 官方模板和
 * 示例则使用 24dp，目前两种取值并存且没有统一。本项目只在 RGB 灯效预览卡等需要区别于
 * 周围形状的强调元素上使用 28dp。普通卡片使用 medium（12dp），列表行使用 large（16dp）。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
