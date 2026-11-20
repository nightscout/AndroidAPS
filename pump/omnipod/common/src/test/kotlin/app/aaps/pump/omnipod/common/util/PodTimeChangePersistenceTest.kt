package app.aaps.pump.omnipod.common.util

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.common.keys.DashStringNonPreferenceKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PodTimeChangePersistenceTest {

    @Test
    fun `Dash restart preserves the in-progress flag and the sent command time zone`() {
        val storage = mock<Preferences>()
        var json: String? = null
        whenever(storage.put(eq(DashStringNonPreferenceKey.PodState), any())).thenAnswer {
            json = it.getArgument(1)
            Unit
        }
        whenever(storage.getIfExists(DashStringNonPreferenceKey.PodState)).thenAnswer { json }
        whenever(storage.get(DashStringNonPreferenceKey.PodState)).thenAnswer { json ?: "" }
        val writer = OmnipodDashPodStateManagerImpl(mock(), mock(), storage)
        val oldZone = PodTimeZone("Europe/Amsterdam", 3600000, 1000)
        val sentZone = oldZone.copy(offset = 7200000, updatedAt = 2000)
        writer.confirmTimeZone(oldZone)
        writer.timeChangeInProgress = true
        writer.createActiveCommand(7, BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))).blockingGet()
        writer.recordBasalProgramTimeZone(sentZone)

        val reader = OmnipodDashPodStateManagerImpl(mock(), mock(), storage)
        assertThat(reader.timeChangeInProgress).isTrue()
        assertThat(reader.timeZoneOffset).isEqualTo(oldZone.offset)
        assertThat(reader.activeCommand!!.basalTimeZone).isEqualTo(sentZone)
        reader.confirmTimeZone(sentZone)
        assertThat(reader.timeZoneOffset).isEqualTo(sentZone.offset)
        reader.reset()
        assertThat(OmnipodDashPodStateManagerImpl(mock(), mock(), storage).timeChangeInProgress).isFalse()
    }
}
