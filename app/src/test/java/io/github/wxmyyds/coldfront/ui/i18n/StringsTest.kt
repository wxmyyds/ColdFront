package io.github.wxmyyds.coldfront.ui.i18n

import java.util.Locale
import org.junit.Assert.assertSame
import org.junit.Test

class StringsTest {
    @Test
    fun `explicit language overrides system locale`() {
        assertSame(EnStrings, stringsFor(Locale.CHINA, "en"))
        assertSame(ZhStrings, stringsFor(Locale.US, "zh"))
    }

    @Test
    fun `system and unknown preferences retain existing fallback`() {
        assertSame(ZhStrings, stringsFor(Locale.TAIWAN, "system"))
        assertSame(EnStrings, stringsFor(Locale.FRANCE, "system"))
        assertSame(ZhStrings, stringsFor(Locale.CHINA, "invalid"))
    }
}
