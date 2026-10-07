package io.github.wxmyyds.coldfront.data

/** Persisted palette keys shared by the settings store and the theme generator. */
internal object PaletteStyles {
    const val TONAL_SPOT = "tonal_spot"
    const val NEUTRAL = "neutral"
    const val VIBRANT = "vibrant"
    const val EXPRESSIVE = "expressive"
    const val RAINBOW = "rainbow"
    const val FRUIT_SALAD = "fruit_salad"
    const val MONOCHROME = "monochrome"
    const val FIDELITY = "fidelity"
    const val CONTENT = "content"

    /** Every persisted key, in the order shown by the palette dropdown. */
    val all: List<String> = listOf(
        TONAL_SPOT, NEUTRAL, VIBRANT, EXPRESSIVE, RAINBOW, FRUIT_SALAD, MONOCHROME, FIDELITY, CONTENT,
    )

    const val DEFAULT = TONAL_SPOT

    /** Unknown or legacy stored values fall back to the default without rewriting storage. */
    fun isValid(value: String?): Boolean = value != null && value in all
}
