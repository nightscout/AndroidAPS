package app.aaps.pump.eopatch.ble

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.pump.eopatch.core.scan.ScanList
import app.aaps.pump.eopatch.vo.PatchState
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class IPatchManagerTest {

    @Test
    fun `interface should define init method`() {
        val patchManager = mock(IPatchManager::class.java)

        patchManager.init()

        verify(patchManager).init()
    }

    @Test
    fun `interface should define updatePatchState method`() {
        val patchManager = mock(IPatchManager::class.java)
        val patchState = mock(PatchState::class.java)

        patchManager.updatePatchState(patchState)

        verify(patchManager).updatePatchState(patchState)
    }

    @Test
    fun `interface should define setConnection method`() {
        val patchManager = mock(IPatchManager::class.java)

        patchManager.setConnection()

        verify(patchManager).setConnection()
    }

    @Test
    fun `interface should define patchActivation method`() {
        val patchManager = mock(IPatchManager::class.java)
        val timeout = 5000L

        `when`(patchManager.patchActivation(timeout)).thenReturn(Single.just(true))

        val result = patchManager.patchActivation(timeout)

        verify(patchManager).patchActivation(timeout)
        assertThat(result.blockingGet()).isTrue()
    }

    @Test
    fun `interface should define scan method`() {
        val patchManager = mock(IPatchManager::class.java)
        val timeout = 10000L
        val scanList = mock(ScanList::class.java)

        `when`(patchManager.scan(timeout)).thenReturn(Single.just(scanList))

        val result = patchManager.scan(timeout)

        verify(patchManager).scan(timeout)
        assertThat(result.blockingGet()).isEqualTo(scanList)
    }

    @Test
    fun `interface should define addBolusToHistory method`() {
        val patchManager = mock(IPatchManager::class.java)
        val bolusInfo = mock(DetailedBolusInfo::class.java)

        patchManager.addBolusToHistory(bolusInfo)

        verify(patchManager).addBolusToHistory(bolusInfo)
    }

    @Test
    fun `interface should define changeBuzzerSetting method`() {
        val patchManager = mock(IPatchManager::class.java)

        patchManager.changeBuzzerSetting()

        verify(patchManager).changeBuzzerSetting()
    }

    @Test
    fun `interface should define changeReminderSetting method`() {
        val patchManager = mock(IPatchManager::class.java)

        patchManager.changeReminderSetting()

        verify(patchManager).changeReminderSetting()
    }

    @Test
    fun `interface should define checkActivationProcess method`() {
        val patchManager = mock(IPatchManager::class.java)

        patchManager.checkActivationProcess()

        verify(patchManager).checkActivationProcess()
    }
}
