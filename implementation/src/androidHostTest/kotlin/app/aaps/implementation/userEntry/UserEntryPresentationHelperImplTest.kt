package app.aaps.implementation.userEntry

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers [UserEntryPresentationHelperImpl.icon] and [iconColor] — pure `when` mappings over every
 * [Sources] value. They are `@Composable` (resolve themed colours / composable icon vals), so they
 * are invoked inside `setContent`; iterating `Sources.entries` exercises all branches and asserts
 * every source resolves to an icon without throwing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
internal class UserEntryPresentationHelperImplTest {

    @get:Rule val compose = createComposeRule()

    @Mock private lateinit var translator: Translator
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter

    private lateinit var sut: UserEntryPresentationHelperImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        sut = UserEntryPresentationHelperImpl(translator, profileUtil, rh, dateUtil, decimalFormatter)
    }

    @Test
    fun `every source resolves to an icon and a colour`() {
        val icons = mutableListOf<ImageVector>()
        compose.setContent {
            MaterialTheme {
                Sources.entries.forEach { source ->
                    icons.add(sut.icon(source))
                    sut.iconColor(source)
                }
            }
        }
        compose.waitForIdle()
        assertThat(icons).hasSize(Sources.entries.size)
    }
}
