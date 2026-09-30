package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * M3 字号阶梯（15 级 baseline）。
 * MD3E 的完整 type scale 是 15 baseline + 15 emphasized，见 [EmphasizedTypography]。
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
 * MD3E 的 emphasized 字号阶梯（15 级）。
 *
 * Compose 的 [Typography] 只有 baseline 的 15 个槽位，emphasized 得自己定义。规范用法：
 * 标题、选中态、关键数值、主要操作——即「本来就该加重的文字」
 * （type-scale-tokens.md：Use the emphasized styles on text that already uses weight）。
 *
 * 取值规则：比 baseline 高一档字重（Normal 400 → Bold 700，Medium 500 → SemiBold 600），
 * **字号 / 行高 / 字距一律不变**——这样 emphasized 与 baseline 混排时基线节奏与换行位置一致。
 *
 * 用法：把 `style = MaterialTheme.typography.headlineSmall` + `fontWeight = FontWeight.SemiBold`
 * 这种散写换成 `style = EmphasizedTypography.headlineSmall`，字重只在这里集中定义。
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
