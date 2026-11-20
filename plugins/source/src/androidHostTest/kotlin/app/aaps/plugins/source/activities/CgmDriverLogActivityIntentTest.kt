package app.aaps.plugins.source.activities

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What a driver screen hands to the shared log screen, and what the log screen reads back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmDriverLogActivityIntentTest {

    @Test
    fun `the logger names and the title come back as they were given`() {
        val context = RuntimeEnvironment.getApplication()

        val intent = CgmDriverLogActivity.intent(context, setOf("DEXCOM_ONEPLUS", "OTHER_DRIVER"), "My driver log")

        assertThat(intent.component?.className).isEqualTo(CgmDriverLogActivity::class.java.name)
        assertThat(CgmDriverLogActivity.loggerNamesOf(intent)).containsExactly("DEXCOM_ONEPLUS", "OTHER_DRIVER")
        assertThat(intent.getStringExtra(CgmDriverLogActivity.EXTRA_TITLE)).isEqualTo("My driver log")
    }

    @Test
    fun `without a title the intent carries none so the generic title is used`() {
        val context = RuntimeEnvironment.getApplication()

        val intent = CgmDriverLogActivity.intent(context, setOf("DEXCOM_ONEPLUS"))

        assertThat(intent.hasExtra(CgmDriverLogActivity.EXTRA_TITLE)).isFalse()
        assertThat(CgmDriverLogActivity.loggerNamesOf(intent)).containsExactly("DEXCOM_ONEPLUS")
    }

    @Test
    fun `an intent without logger names gives an empty set`() {
        assertThat(CgmDriverLogActivity.loggerNamesOf(Intent())).isEmpty()
    }
}
