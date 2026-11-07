package app.aaps.desktop.shell

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language the desktop answers in, from the stored setting.
 *
 * The start up and the change while the app runs both use `languageTag`. The change used the stored
 * value as it was, so four of the offered languages gave English text until the next start.
 */
class DesktopLanguageTagTest {

    @Test
    fun `stored values that are not tags are fixed up`() {
        assertEquals("zh-TW", languageTag("zh_TW"))
        assertEquals("zh-CN", languageTag("zh_CN"))
        assertEquals("pt-BR", languageTag("pt_BR"))
        assertEquals("da", languageTag("dk"))
    }

    @Test
    fun `a plain language code is used as it is`() {
        assertEquals("cs", languageTag("cs"))
    }

    @Test
    fun `following the device gives the machine language`() {
        assertEquals(Locale.getDefault().toLanguageTag(), languageTag("default"))
    }
}
