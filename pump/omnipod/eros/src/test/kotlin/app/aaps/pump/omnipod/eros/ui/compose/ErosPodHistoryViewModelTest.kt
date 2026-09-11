package app.aaps.pump.omnipod.eros.ui.compose

import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ErosPodHistoryViewModelTest {

    @Mock private lateinit var aapsOmnipodUtil: AapsOmnipodUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var profileUtil: ProfileUtil

    private val erosHistory: ErosHistory = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `records is empty when history has no recent records`() {
        // init calls loadHistory() synchronously: getAllErosHistoryRecordsFromTimestamp(...).sorted()
        whenever(erosHistory.getAllErosHistoryRecordsFromTimestamp(anyLong())).thenReturn(emptyList())

        val sut = ErosPodHistoryViewModel(erosHistory, aapsOmnipodUtil, rh, profileUtil)

        assertThat(sut.records.value).isEmpty()
    }
}
