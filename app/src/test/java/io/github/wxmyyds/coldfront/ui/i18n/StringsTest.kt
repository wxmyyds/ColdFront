package io.github.wxmyyds.coldfront.ui.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StringsTest {
    @Test
    fun `explicit language overrides system locale`() {
        assertSame(EnStrings, stringsFor(Locale.CHINA, "en"))
        assertSame(ZhStrings, stringsFor(Locale.US, "zh"))
    }

    @Test
    fun `diagnostic UUID overflow count is localized in both language tables`() {
        assertEquals("另有 1 项", ZhStrings.diagMoreCount.format(Locale.ROOT, 1))
        assertEquals("另有 12 项", ZhStrings.diagMoreCount.format(Locale.ROOT, 12))
        assertEquals("+1 more", EnStrings.diagMoreCount.format(Locale.ROOT, 1))
        assertEquals("+12 more", EnStrings.diagMoreCount.format(Locale.ROOT, 12))
        assertEquals("+4 more", stringsFor(Locale.CHINA, "en").diagMoreCount.format(Locale.ROOT, 4))
        assertEquals("另有 4 项", stringsFor(Locale.US, "zh").diagMoreCount.format(Locale.ROOT, 4))
    }

    @Test
    fun `system and unknown preferences retain existing fallback`() {
        assertSame(ZhStrings, stringsFor(Locale.TAIWAN, "system"))
        assertSame(EnStrings, stringsFor(Locale.FRANCE, "system"))
        assertSame(ZhStrings, stringsFor(Locale.CHINA, "invalid"))
    }
}
