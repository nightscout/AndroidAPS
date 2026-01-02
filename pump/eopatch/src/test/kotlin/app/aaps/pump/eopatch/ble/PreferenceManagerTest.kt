package app.aaps.pump.eopatch.ble

import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.BolusCurrent
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PreferenceManagerTest {

    @Test
    fun `interface should define init method`() {
        val preferenceManager = mock(PreferenceManager::class.java)

        preferenceManager.init()

        verify(preferenceManager).init()
    }

    @Test
    fun `interface should define flush methods`() {
        val preferenceManager = mock(PreferenceManager::class.java)

        preferenceManager.flushPatchConfig()
        preferenceManager.flushPatchState()
        preferenceManager.flushBolusCurrent()
        preferenceManager.flushNormalBasalManager()
        preferenceManager.flushTempBasalManager()
        preferenceManager.flushAlarms()

        verify(preferenceManager).flushPatchConfig()
        verify(preferenceManager).flushPatchState()
        verify(preferenceManager).flushBolusCurrent()
        verify(preferenceManager).flushNormalBasalManager()
        verify(preferenceManager).flushTempBasalManager()
        verify(preferenceManager).flushAlarms()
    }

    @Test
    fun `interface should define updatePatchLifeCycle method`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val event = mock(PatchLifecycleEvent::class.java)

        preferenceManager.updatePatchLifeCycle(event)

        verify(preferenceManager).updatePatchLifeCycle(event)
    }

    @Test
    fun `interface should define updatePatchState method`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val newState = mock(PatchState::class.java)

        preferenceManager.updatePatchState(newState)

        verify(preferenceManager).updatePatchState(newState)
    }

    @Test
    fun `interface should define setMacAddress method`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val mac = "00:11:22:33:44:55"

        preferenceManager.setMacAddress(mac)

        verify(preferenceManager).setMacAddress(mac)
    }

    @Test
    fun `interface should define setSharedKey method`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val key = ByteArray(16)

        preferenceManager.setSharedKey(key)

        verify(preferenceManager).setSharedKey(key)
    }

    @Test
    fun `interface should define seq15 methods`() {
        val preferenceManager = mock(PreferenceManager::class.java)

        `when`(preferenceManager.getSeq15()).thenReturn(5)

        preferenceManager.setSeq15(10)
        val seq = preferenceManager.getSeq15()
        preferenceManager.increaseSeq15()

        verify(preferenceManager).setSeq15(10)
        verify(preferenceManager).getSeq15()
        verify(preferenceManager).increaseSeq15()
        assertThat(seq).isEqualTo(5)
    }

    @Test
    fun `interface should define getPatchWakeupTimestamp method`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val timestamp = 123456789L

        `when`(preferenceManager.getPatchWakeupTimestamp()).thenReturn(timestamp)

        val result = preferenceManager.getPatchWakeupTimestamp()

        verify(preferenceManager).getPatchWakeupTimestamp()
        assertThat(result).isEqualTo(timestamp)
    }

    @Test
    fun `interface should define observe methods`() {
        val preferenceManager = mock(PreferenceManager::class.java)

        `when`(preferenceManager.observePatchLifeCycle()).thenReturn(Observable.just(PatchLifecycle.ACTIVATED))
        `when`(preferenceManager.observePatchConfig()).thenReturn(Observable.just(mock(PatchConfig::class.java)))
        `when`(preferenceManager.observePatchState()).thenReturn(Observable.just(mock(PatchState::class.java)))
        `when`(preferenceManager.observeBolusCurrent()).thenReturn(Observable.just(mock(BolusCurrent::class.java)))
        `when`(preferenceManager.observeAlarm()).thenReturn(Observable.just(mock(Alarms::class.java)))

        preferenceManager.observePatchLifeCycle().test().assertValue(PatchLifecycle.ACTIVATED)
        preferenceManager.observePatchConfig().test().assertValueCount(1)
        preferenceManager.observePatchState().test().assertValueCount(1)
        preferenceManager.observeBolusCurrent().test().assertValueCount(1)
        preferenceManager.observeAlarm().test().assertValueCount(1)

        verify(preferenceManager).observePatchLifeCycle()
        verify(preferenceManager).observePatchConfig()
        verify(preferenceManager).observePatchState()
        verify(preferenceManager).observeBolusCurrent()
        verify(preferenceManager).observeAlarm()
    }

    @Test
    fun `interface should define isInitDone method`() {
        val preferenceManager = mock(PreferenceManager::class.java)

        `when`(preferenceManager.isInitDone()).thenReturn(true)

        val result = preferenceManager.isInitDone()

        verify(preferenceManager).isInitDone()
        assertThat(result).isTrue()
    }

    @Test
    fun `interface should define patchState property`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val patchState = mock(PatchState::class.java)

        `when`(preferenceManager.patchState).thenReturn(patchState)

        val result = preferenceManager.patchState

        assertThat(result).isEqualTo(patchState)
    }

    @Test
    fun `interface should define bolusCurrent property with getter and setter`() {
        val preferenceManager = mock(PreferenceManager::class.java)
        val bolusCurrent = mock(BolusCurrent::class.java)

        `when`(preferenceManager.bolusCurrent).thenReturn(bolusCurrent)

        val result = preferenceManager.bolusCurrent
        preferenceManager.bolusCurrent = bolusCurrent

        assertThat(result).isEqualTo(bolusCurrent)
    }
}
