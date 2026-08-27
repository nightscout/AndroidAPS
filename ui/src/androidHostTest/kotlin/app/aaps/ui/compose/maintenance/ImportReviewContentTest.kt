package app.aaps.ui.compose.maintenance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.rx.bus.RxBus
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [ImportReviewContent]: renders review + fires back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImportReviewContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val rxBus: RxBus = mock()

    private lateinit var titleLabel: String
    private lateinit var closeLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        titleLabel = ctx.getString(CoreUiR.string.import_setting)
        closeLabel = ctx.getString(CoreUiR.string.close)
    }

    @Test
    fun rendersAndFiresBack() {
        var back = false
        compose.setContent {
            MaterialTheme {
                ImportReviewContent(
                    state = ImportStep.Review(
                        file = PrefsFile(name = "backup.json", content = "", metadata = emptyMap()),
                        fileSource = ImportSource.LOCAL
                    ),
                    rxBus = rxBus,
                    onMasterPasswordChanged = {},
                    onDecryptionPasswordChanged = {},
                    onDecrypt = {},
                    onImport = {},
                    onBack = { back = true }
                )
            }
        }
        compose.onNodeWithText(titleLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }
}
