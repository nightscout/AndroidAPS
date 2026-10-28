package app.aaps.implementation.resources

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * [LocaleHelper] keeps the default locale on the language of the app. Numbers and dates are formatted
 * with it, also in the background.
 *
 * Android sets the default back to the phone language on every configuration change. With the phone in
 * English and the app in Czech, the overview showed "7.3" instead of "7,3" after the font size was
 * changed, next to a BG value still showing "5,5". `MainApp.onConfigurationChanged` calls `update()`
 * for that; these tests pin what `update()` does. Here, not in `:core:ui`, because the settings store
 * used below lives in this module.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class LocaleHelperDefaultLocaleTest {

    // Locale.setDefault is global to the JVM, so what these tests do to it has to be undone.
    private val localeBefore: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(localeBefore)
        LocaleHelper.settings = null
    }

    @Test
    fun `after a configuration change update puts the default back on the picked language`() {
        storeLanguage("cs")
        // What Android does on a configuration change: the phone language again.
        Locale.setDefault(Locale.US)
        assertThat(DecimalFormatSymbols.getInstance().decimalSeparator).isEqualTo('.')

        LocaleHelper.update()

        assertThat(Locale.getDefault().language).isEqualTo("cs")
        assertThat(DecimalFormatSymbols.getInstance().decimalSeparator).isEqualTo(',')
    }

    @Test
    fun `back to the phone language, update moves the default to the phone language too`() {
        storeLanguage("cs")
        LocaleHelper.update()
        assertThat(Locale.getDefault().language).isEqualTo("cs")

        // It used to do nothing here, so the default stayed Czech while the text was in the phone language.
        storeLanguage("default")
        LocaleHelper.update()

        assertThat(Locale.getDefault()).isEqualTo(Resources.getSystem().configuration.locales[0])
    }

    @Test
    fun `Simple mode is the phone language`() {
        storeLanguage("cs", simpleMode = true)

        LocaleHelper.update()

        assertThat(Locale.getDefault()).isEqualTo(Resources.getSystem().configuration.locales[0])
    }

    @Test
    fun `wrap sets the default as well`() {
        storeLanguage("fr")
        Locale.setDefault(Locale.US)

        LocaleHelper.wrap(ApplicationProvider.getApplicationContext())

        assertThat(Locale.getDefault().language).isEqualTo("fr")
    }

    private fun storeLanguage(language: String, simpleMode: Boolean = false) {
        LocaleHelper.settings = FileKeyValueStore(mapOf(BooleanKey.GeneralSimpleMode.key to simpleMode, StringKey.GeneralLanguage.key to language))
    }
}
