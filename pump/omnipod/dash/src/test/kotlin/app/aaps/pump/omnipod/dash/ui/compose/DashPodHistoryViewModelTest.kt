package app.aaps.pump.omnipod.dash.ui.compose

import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.HistoryRecord
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class DashPodHistoryViewModelTest {

    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var profileUtil: ProfileUtil

    private val dashHistory: DashHistory = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
    }

    @Test
    fun `init loads recent history into records`() {
        val record = mock<HistoryRecord>()
        whenever(dashHistory.getRecordsAfter(anyLong())).thenReturn(Single.just(listOf(record)))

        val sut = DashPodHistoryViewModel(dashHistory, aapsSchedulers, rh, profileUtil)

        assertThat(sut.records.value).containsExactly(record)
    }

    @Test
    fun `records is empty when history has no recent records`() {
        whenever(dashHistory.getRecordsAfter(anyLong())).thenReturn(Single.just(emptyList()))

        val sut = DashPodHistoryViewModel(dashHistory, aapsSchedulers, rh, profileUtil)

        assertThat(sut.records.value).isEmpty()
    }
}
