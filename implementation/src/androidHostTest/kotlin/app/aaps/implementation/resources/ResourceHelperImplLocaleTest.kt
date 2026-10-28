package app.aaps.implementation.resources

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.R
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * [ResourceHelperImpl] resolves text in the language the user picked, and follows a change of it.
 *
 * Three bugs lived here, all of them invisible to a mocked test because they need real resources:
 *
 * - The cached context was dropped only when Simple mode changed. A language change recreates the
 *   activity, which re-runs `attachBaseContext` for the activity alone, so this app-scoped class kept
 *   the old language for the rest of the process.
 * - The decision "is this context already right?" compared the picked locale with [Locale.getDefault].
 *   `LocaleHelper.update()` and `LocaleHelper.wrap()` both move the default locale to the picked
 *   language, while the Application resources stay in the phone language, so after either of them had
 *   run the comparison matched and handed back phone-language text.
 * - The context was dropped by a listener on the language setting. Code reacting to the same change -
 *   the overview reading its texts again - could ask for text before that listener had run, and got the
 *   old language. Now the stored language is checked on every use, so the change counts at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class ResourceHelperImplLocaleTest {

    @Mock private lateinit var fabricPrivacy: FabricPrivacy

    private lateinit var context: Context
    private lateinit var sut: ResourceHelperImpl

    // Locale.setDefault is global to the JVM, so what these tests do to it has to be undone.
    private val localeBefore: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        // LocaleHelper reads the settings store it was handed: it runs in attachBaseContext, where
        // nothing can be injected.
        storePickedLanguage("de", simpleMode = false)
        sut = ResourceHelperImpl(context, fabricPrivacy)
        sut.start()
    }

    @After
    fun tearDown() {
        Locale.setDefault(localeBefore)
        LocaleHelper.settings = null
    }

    @Test
    fun `the translations these tests compare are really in the resources`() {
        // Guards every test below. Without this, a Robolectric setup that resolves no translation at all
        // would compare English with English everywhere and pass while testing nothing.
        assertThat(textIn(Locale.GERMAN, R.string.cancel)).isNotEqualTo(textIn(Locale.ENGLISH, R.string.cancel))
        assertThat(textIn(Locale.FRENCH, R.string.cancel)).isNotEqualTo(textIn(Locale.ENGLISH, R.string.cancel))
        assertThat(textIn(Locale.FRENCH, R.string.cancel)).isNotEqualTo(textIn(Locale.GERMAN, R.string.cancel))
        assertThat(pluralIn(Locale.GERMAN, R.plurals.hours, 3, 3)).isNotEqualTo(pluralIn(Locale.ENGLISH, R.plurals.hours, 3, 3))
    }

    @Test
    fun `text is in the picked language even after the default locale moved to it`() {
        // What LocaleHelper.update() does in MainApp.doInit, and wrap() in every attachBaseContext.
        Locale.setDefault(Locale.GERMAN)

        assertThat(sut.gs(R.string.cancel)).isEqualTo(textIn(Locale.GERMAN, R.string.cancel))
    }

    @Test
    fun `text follows a change of the picked language at once`() {
        assertThat(sut.gs(R.string.cancel)).isEqualTo(textIn(Locale.GERMAN, R.string.cancel))

        // Nothing else happens in between: no listener, no idle main thread. Code that reacts to the
        // change asks for text straight away.
        storePickedLanguage("fr", simpleMode = false)
        assertThat(sut.gs(R.string.cancel)).isEqualTo(textIn(Locale.FRENCH, R.string.cancel))

        // The activity does this, and it is what made the old comparison match.
        Locale.setDefault(Locale.FRENCH)
        assertThat(sut.gs(R.string.cancel)).isEqualTo(textIn(Locale.FRENCH, R.string.cancel))
    }

    @Test
    fun `turning Simple mode on follows the phone, and turning it off again returns to the picked language`() {
        // Simple mode forces the language to "default". Turning it off used to leave gs() in the phone
        // language until the app was restarted.
        Locale.setDefault(Locale.GERMAN)

        storePickedLanguage("de", simpleMode = true)
        assertThat(sut.gs(R.string.cancel)).isEqualTo(context.getString(R.string.cancel))

        storePickedLanguage("de", simpleMode = false)
        assertThat(sut.gs(R.string.cancel)).isEqualTo(textIn(Locale.GERMAN, R.string.cancel))
    }

    @Test
    fun `plurals are in the picked language as well`() {
        // gq() read the plain context from 2019 until this was fixed, so "3 hours ago" and the objectives
        // duration were in the phone language even after a restart.
        Locale.setDefault(Locale.GERMAN)

        assertThat(sut.gq(R.plurals.hours, 3, 3)).isEqualTo(pluralIn(Locale.GERMAN, R.plurals.hours, 3, 3))
    }

    private fun storePickedLanguage(language: String, simpleMode: Boolean) {
        LocaleHelper.settings = FileKeyValueStore(mapOf(BooleanKey.GeneralSimpleMode.key to simpleMode, StringKey.GeneralLanguage.key to language))
    }

    private fun contextIn(locale: Locale): Context =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )

    private fun textIn(locale: Locale, id: Int): String = contextIn(locale).getString(id)

    private fun pluralIn(locale: Locale, id: Int, quantity: Int, vararg args: Any?): String =
        contextIn(locale).resources.getQuantityString(id, quantity, *args)
}
