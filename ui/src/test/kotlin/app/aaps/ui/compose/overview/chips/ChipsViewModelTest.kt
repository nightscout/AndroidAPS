package app.aaps.ui.compose.overview.chips

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChipsViewModelTest {

    @Mock private lateinit var cache: OverviewDataCache
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var config: Config
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: ChipsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // The stateIn/shareIn coroutines are deferred by StandardTestDispatcher, but the cold combine chains
        // are built at construction, so the cache graph flows they reference must be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(cache.iobGraphFlow).thenReturn(MutableStateFlow(IobGraphData(emptyList(), emptyList())))
        whenever(cache.cobGraphFlow).thenReturn(MutableStateFlow(CobGraphData(emptyList(), emptyList())))
        sut = ChipsViewModel(
            cache, iobCobCalculator, loop, config, persistenceLayer, constraintChecker, profileFunction,
            processedDeviceStatusData, profileUtil, activePlugin, rh, decimalFormatter, dateUtil, aapsLogger,
            preferences, rxBus
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes empty initial values`() {
        assertThat(sut.iobUiState.value.iobTotal).isEqualTo(0.0)
        assertThat(sut.iobUiState.value.text).isEmpty()
        assertThat(sut.cobUiState.value.text).isEmpty()
        assertThat(sut.cobUiState.value.carbsReq).isEqualTo(0)
        assertThat(sut.sensitivityUiState.value.ratio).isEqualTo(1.0)
        assertThat(sut.sensitivityUiState.value.isEnabled).isTrue()
        assertThat(sut.sensitivityUiState.value.hasData).isFalse()
    }
}
