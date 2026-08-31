package app.aaps.implementation.resources

import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.ImplementationStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A `ui` owned [TextRef.Named] resolves to real text, not to its own name.
 *
 * `:core:ui` is above `:core:interfaces`, so `ResourceHelper` cannot see `CoreUiStringIds` and has to be
 * told about it - [ResourceHelperImpl] does that from its `init`, through `TextRefIdRegistry`. When
 * that wiring is missing the resolver falls back to returning the raw name, which compiles, passes
 * every mocked test, and shows `read_status` on screen instead of "Read status".
 *
 * That fallback now carries the command queue, the translator and the alerts, so it is worth one real
 * resolution through a Robolectric context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class ResourceHelperImplNamedRefTest {

    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: ResourceHelperImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(true))
        sut = ResourceHelperImpl(ApplicationProvider.getApplicationContext(), fabricPrivacy, preferences)
        // The `coreUi` and `implementation` owners are registered by start(), not by the constructor -
        // they moved so the Application controls when it happens and Metro can own this class.
        sut.start()
    }

    @Test
    fun `a ui owned name resolves to its string`() {
        val resolved = sut.gs(CoreUiStrings.read_status)
        assertThat(resolved).isNotEqualTo("read_status")
        assertThat(resolved).isNotEmpty()
    }

    @Test
    fun `format arguments reach the ui owned string`() {
        // read_status carries one %s. Going through the name must format exactly as the id does.
        assertThat(sut.gs(CoreUiStrings.read_status, "because"))
            .isEqualTo(sut.gs(app.aaps.core.ui.R.string.read_status, "because"))
    }

    @Test
    fun `a name owned by this module resolves to its string`() {
        // The `implementation` owner is registered from the same init as `ui`. It has to be, for the same
        // reason: ResourceHelper lives here but the interface it implements is in :core:interfaces, which
        // cannot see ImplementationStringIds.
        val resolved = sut.gs(ImplementationStrings.backup_to_google_drive)
        assertThat(resolved).isNotEqualTo("backup_to_google_drive")
        assertThat(resolved).isNotEmpty()
    }

    @Test
    fun `an unclaimed owner still falls back to the raw name`() {
        // The documented behaviour when nobody registered the owner - visibly wrong, never blank.
        assertThat(sut.gs(TextRef.Named("nobody-owns-this", "some_name"))).isEqualTo("some_name")
    }
}
