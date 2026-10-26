package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * MD3 字号阶梯，共 15 个 baseline 样式。
 * MD3E 另有 15 个 emphasized 样式，详见 [EmphasizedTypography]。
 */
val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

/**
 * MD3E 的 emphasized 字号阶梯，共 15 级。
 *
 * Compose 的 [Typography] 只有 15 个 baseline 样式，因此 emphasized 样式需单独定义。
 * 规范建议将其用于本身已通过字重表达强调的文字，例如标题、选中态、关键数值和主要操作
 * （type-scale-tokens.md：Use the emphasized styles on text that already uses weight）。
 *
 * baseline 的字重提高一档（Normal 400 → Bold 700，Medium 500 → SemiBold 600），
 * 字号、行高和字距保持不变。这样两类样式混排时，基线节奏和换行位置一致。
 *
 * 使用 `EmphasizedTypography.headlineSmall` 等样式，替代分散设置
 * `style = MaterialTheme.typography.headlineSmall` 和 `fontWeight = FontWeight.SemiBold`。
 * 字重定义集中在这里。
 */
object EmphasizedTypography {
    // baseline Normal(400) → Bold(700)
    val displayLarge: TextStyle = AppTypography.displayLarge.copy(fontWeight = FontWeight.Bold)
    val displayMedium: TextStyle = AppTypography.displayMedium.copy(fontWeight = FontWeight.Bold)
    val displaySmall: TextStyle = AppTypography.displaySmall.copy(fontWeight = FontWeight.Bold)
    val headlineLarge: TextStyle = AppTypography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    val headlineMedium: TextStyle = AppTypography.headlineMedium.copy(fontWeight = FontWeight.Bold)
    val headlineSmall: TextStyle = AppTypography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    val bodyLarge: TextStyle = AppTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    val bodyMedium: TextStyle = AppTypography.bodyMedium.copy(fontWeight = FontWeight.Bold)
    val bodySmall: TextStyle = AppTypography.bodySmall.copy(fontWeight = FontWeight.Bold)

    // baseline Medium(500) → SemiBold(600)
    val titleLarge: TextStyle = AppTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    val titleMedium: TextStyle = AppTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val titleSmall: TextStyle = AppTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    val labelLarge: TextStyle = AppTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val labelMedium: TextStyle = AppTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val labelSmall: TextStyle = AppTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
}
