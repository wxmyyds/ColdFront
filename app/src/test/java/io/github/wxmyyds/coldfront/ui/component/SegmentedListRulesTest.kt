package io.github.wxmyyds.coldfront.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 组件层全局规则的纯逻辑部分：不依赖 Compose 运行时，可在 JVM 单测覆盖。 */
class SegmentedListRulesTest {
    @Test
    fun `single item group disables press morph`() {
        assertFalse(segmentedGroupPressMorph(0))
        assertFalse(segmentedGroupPressMorph(1))
    }

    @Test
    fun `multi item group enables unified press morph`() {
        assertTrue(segmentedGroupPressMorph(2))
        assertTrue(segmentedGroupPressMorph(3))
    }

    @Test
    fun `dropdown shows actual selected label`() {
        val options = listOf("system" to "跟随系统", "light" to "浅色")
        assertEquals(
            "浅色",
            dropdownSelectedLabel(options.map { it.first }, "light") { value ->
                options.firstOrNull { it.first == value }?.second ?: value
            },
        )
    }

    @Test
    fun `dropdown falls back to selected value itself when not in options`() {
        assertEquals(
            "dark",
            dropdownSelectedLabel(listOf("system", "light"), "dark") { it },
        )
    }
}
