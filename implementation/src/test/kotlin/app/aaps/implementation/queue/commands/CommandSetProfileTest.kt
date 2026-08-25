package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandSetProfileTest : TestBaseWithProfile() {

    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var commandQueue: CommandQueue

    private fun newCommand(hasNsId: Boolean = false, callback: Callback? = null) =
        CommandSetProfile(
            aapsLogger, rh, smsCommunicator, activePlugin, dateUtil, commandQueue, config, persistenceLayer,
            pumpEnactResultProvider, effectiveProfile, hasNsId, callback
        )

    @Test
    fun `execute returns success not enacted when profile already set`() = runTest {
        whenever(commandQueue.isThisProfileSet(effectiveProfile)).thenReturn(true)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        whenever(activePlugin.activePump).thenReturn(testPumpPlugin)

        val result = newCommand().execute()

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(smsCommunicator, never()).sendNotificationToAllNumbers(any())
    }

    @Test
    fun `execute calls pump setNewBasalProfile when profile differs`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setNewBasalProfile(effectiveProfile) } doReturn pumpResult
        }
        whenever(commandQueue.isThisProfileSet(effectiveProfile)).thenReturn(false)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `execute sends SMS notification when enacted and hasNsId and not AAPSCLIENT`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setNewBasalProfile(effectiveProfile) } doReturn pumpResult
        }
        whenever(commandQueue.isThisProfileSet(effectiveProfile)).thenReturn(false)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(config.isEnabled(eq(ExternalOptions.DO_NOT_SEND_SMS_ON_PROFILE_CHANGE))).thenReturn(false)
        whenever(smsCommunicator.isEnabled()).thenReturn(true)
        whenever(rh.gs(app.aaps.core.ui.R.string.profile_set_ok)).thenReturn("profile set ok")

        newCommand(hasNsId = true).execute()

        verify(smsCommunicator).sendNotificationToAllNumbers("profile set ok")
    }

    @Test
    fun `execute does not send SMS when hasNsId is false`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setNewBasalProfile(effectiveProfile) } doReturn pumpResult
        }
        whenever(commandQueue.isThisProfileSet(effectiveProfile)).thenReturn(false)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)

        newCommand(hasNsId = false).execute()

        verify(smsCommunicator, never()).sendNotificationToAllNumbers(any())
    }

    @Test
    fun `execute does not send SMS when AAPSCLIENT`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setNewBasalProfile(effectiveProfile) } doReturn pumpResult
        }
        whenever(commandQueue.isThisProfileSet(effectiveProfile)).thenReturn(false)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        whenever(config.AAPSCLIENT).thenReturn(true)

        newCommand(hasNsId = true).execute()

        verify(smsCommunicator, never()).sendNotificationToAllNumbers(any())
    }

    @Test
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        whenever(commandQueue.isThisProfileSet(effectiveProfile)).thenReturn(true)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        whenever(activePlugin.activePump).thenReturn(testPumpPlugin)
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                received = result
            }
        }

        newCommand(callback = callback).executeWithCallback()

        assertThat(received).isNotNull()
        assertThat(received!!.success).isTrue()
        assertThat(received.enacted).isFalse()
    }

    @Test
    fun `cancel invokes callback with success by default`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                received = result
            }
        }

        newCommand(callback = callback).cancel(app.aaps.core.ui.R.string.command_replaced)

        assertThat(received).isNotNull()
        assertThat(received!!.success).isTrue()
    }

    @Test
    fun `cancel invokes callback with failure when success=false`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                received = result
            }
        }

        newCommand(callback = callback).cancel(app.aaps.core.ui.R.string.command_replaced, success = false)

        assertThat(received).isNotNull()
        assertThat(received!!.success).isFalse()
    }

    @Test
    fun `commandType is BASAL_PROFILE`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.BASAL_PROFILE)
    }

    @Test
    fun `log is SET PROFILE`() {
        assertThat(newCommand().log()).isEqualTo("SET PROFILE")
    }
}
