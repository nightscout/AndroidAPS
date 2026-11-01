package app.aaps.core.ui.compose

import android.content.ActivityNotFoundException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafeUriHandlerTest {

    @get:Rule
    val compose = createComposeRule()

    /** What Android throws when no browser can open the link, e.g. blocked by Family Link. */
    private val noBrowser = object : UriHandler {
        var asked: String? = null
        override fun openUri(uri: String) {
            asked = uri
            throw ActivityNotFoundException("No Activity found to handle Intent")
        }
    }

    @Test
    fun noBrowserDoesNotCrash() {
        var safe: UriHandler? = null
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides noBrowser) {
                safe = rememberSafeUriHandler()
            }
        }

        safe!!.openUri("https://wiki.aaps.app")

        // The link was really passed on; only the failure was swallowed
        assertThat(noBrowser.asked).isEqualTo("https://wiki.aaps.app")
    }
}
