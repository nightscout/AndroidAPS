package app.aaps.ui.compose.calibrationDialog

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class CalibrationDialogViewModelTest {

    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var xDripBroadcast: XDripBroadcast
    @Mock private lateinit var xDripSource: XDripSource
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper

    private lateinit var sut: CalibrationDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // refreshPreconditions() is launched on viewModelScope -> deferred by StandardTestDispatcher.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MGDL)
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(0.0)
        sut = CalibrationDialogViewModel(
            profileUtil, profileFunction, xDripBroadcast, xDripSource, uel, glucoseStatusProvider,
            activePlugin, persistenceLayer, dateUtil, rh
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no action when bg is zero`() {
        assertThat(sut.uiState.value.bg).isEqualTo(0.0)
        assertThat(sut.hasAction()).isFalse()
    }

    @Test
    fun `updateBg sets the value and enables the action`() {
        sut.updateBg(120.0)

        assertThat(sut.uiState.value.bg).isEqualTo(120.0)
        assertThat(sut.hasAction()).isTrue()
    }
}
