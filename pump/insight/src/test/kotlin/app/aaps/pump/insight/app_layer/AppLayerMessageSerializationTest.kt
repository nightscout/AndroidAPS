package app.aaps.pump.insight.app_layer

import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import app.aaps.pump.insight.app_layer.connection.ActivateServiceMessage
import app.aaps.pump.insight.app_layer.history.HistoryReadingDirection
import app.aaps.pump.insight.app_layer.history.StartReadingHistoryMessage
import app.aaps.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock
import app.aaps.pump.insight.app_layer.remote_control.DeliverBolusMessage
import app.aaps.pump.insight.app_layer.remote_control.SetDateTimeMessage
import app.aaps.pump.insight.app_layer.remote_control.SetOperatingModeMessage
import app.aaps.pump.insight.descriptors.AppCommands
import app.aaps.pump.insight.descriptors.BasalProfile
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.descriptors.PumpTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers the wire format shared by every message the driver can send.
 *
 * `serialize()` writes a four byte header - version, service id, and the command id taken from
 * [AppCommands] - and then whatever the message's own `data` builds. Each message class supplies
 * that `data` itself, and none of them were exercised before. Two things can go wrong and neither
 * shows up at compile time: a `data` getter that throws (the command then fails only when the pump
 * is actually asked for it), and a header carrying the wrong command id, which makes the pump answer
 * a different command than the one intended.
 */
class AppLayerMessageSerializationTest {

    /** Every command id the pump understands, read back from the lookup table. */
    private val commandIds: List<Int> =
        AppCommands::class.java.declaredFields
            .filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.type == Int::class.javaPrimitiveType && !it.name.contains('$')
            }
            .map { field ->
                field.isAccessible = true
                field.getInt(null)
            }

    /**
     * Builds the message for an id, giving it a service when the class leaves that open.
     * `ReadParameterBlockMessage` and friends have their service chosen by the caller, and
     * `serialize()` dereferences it.
     */
    private fun message(id: Int): AppLayerMessage =
        AppCommands.fromId(id)!!.also { msg ->
            if (msg.service == null) msg.service = Service.REMOTE_CONTROL
            // Seven of the messages carry a value the caller has to choose. Filled in with a valid
            // one here so that `data` runs; the point is the format, not these particular values.
            when (msg) {
                is ActivateServiceMessage          -> msg.servicePassword = ByteArray(16)
                is SetOperatingModeMessage         -> msg.operatingMode = OperatingMode.STARTED
                is ReadParameterBlockMessage       -> msg.parameterBlockId = ActiveBRProfileBlock::class.java
                // configurationBlockId is read back from the pump's answer, not set by the caller.
                is WriteConfigurationBlockMessage  -> msg.parameterBlock = ActiveBRProfileBlock().also { it.activeBasalProfile = BasalProfile.PROFILE_1 }

                is DeliverBolusMessage             -> msg.bolusType = BolusType.STANDARD
                is StartReadingHistoryMessage      -> msg.direction = HistoryReadingDirection.FORWARD
                is SetDateTimeMessage              -> msg.pumpTime = PumpTime()
            }
        }

    @Test
    fun theTestFindsTheCommandIds() {
        // Without this, every other test here would pass over an empty list.
        assertThat(commandIds.size).isAtLeast(30)
    }

    @Test
    fun everyMessageSerializesWithoutThrowing() {
        val broken = commandIds.mapNotNull { id ->
            runCatching { message(id).serialize() }
                .exceptionOrNull()
                ?.let { "$id ${AppCommands.fromId(id)!!::class.simpleName}: ${it::class.simpleName}" }
        }
        assertThat(broken).isEmpty()
    }

    @Test
    fun everyMessageWritesItsOwnCommandIdIntoTheHeader() {
        val wrong = commandIds.filter { id ->
            val bytes = message(id).serialize().bytes
            // Header: [0] version, [1] service id, [2..3] command id little endian.
            val encoded = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
            encoded != id
        }
        assertThat(wrong).isEmpty()
    }

    @Test
    fun everyMessageCarriesTheServiceItWasGiven() {
        val wrong = commandIds.filter { id ->
            val sut = message(id)
            val serviceId = sut.service!!.id
            sut.serialize().bytes[1] != serviceId
        }
        assertThat(wrong).isEmpty()
    }

    /** The header is fixed width, so anything shorter means the message wrote nothing at all. */
    @Test
    fun everyMessageIsAtLeastAsLongAsTheHeader() {
        val tooShort = commandIds.filter { id -> message(id).serialize().bytes.size < 4 }
        assertThat(tooShort).isEmpty()
    }
}
