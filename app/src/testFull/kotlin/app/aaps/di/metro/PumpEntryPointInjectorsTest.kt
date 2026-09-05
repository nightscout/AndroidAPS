package app.aaps.di.metro

import app.aaps.pump.danar.services.DanaRExecutionService
import app.aaps.pump.danarkorean.services.DanaRKoreanExecutionService
import app.aaps.pump.danarv2.services.DanaRv2ExecutionService
import app.aaps.pump.diaconn.service.DiaconnG8Service
import app.aaps.pump.insight.InsightAlertService
import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import app.aaps.pump.common.hw.rileylink.service.RileyLinkBroadcastReceiver
import app.aaps.pump.danars.services.DanaRSService
import app.aaps.pump.medtrum.services.MedtrumService
import app.aaps.pump.medtronic.service.RileyLinkMedtronicService
import app.aaps.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The pump entry points Android constructs must have a member injector entry.
 *
 * Same failure mode as the receivers, and the same reason it needs a test: a missing entry is not a
 * build error. `MetroService.onCreate` looks the injector up by `target::class`, throws when it is not
 * there, and that only happens when the service actually starts - which for these is when the phone
 * connects to the pump. A DanaR user would see the driver fail at connect time.
 *
 * All three subclass `AbstractDanaRExecutionService`, and the fields they need are declared on that
 * base. The entry still has to name each concrete class: the lookup uses the runtime class, so the
 * base's entry would never be found. That is the trap `ReceiverInjectorsTest` spells out for
 * `SmsReceiver`, and it applies here three times over.
 */
class PumpEntryPointInjectorsTest {

    @Test
    fun `each DanaR execution service has its own injector`() {
        val injectors = testRoot().contributedMemberInjectors

        assertThat(injectors.keys).containsAtLeast(
            DanaRExecutionService::class,
            DanaRKoreanExecutionService::class,
            DanaRv2ExecutionService::class
        )
    }

    @Test
    fun `the other converted pump services have injectors`() {
        val injectors = testRoot().contributedMemberInjectors

        assertThat(injectors.keys).containsAtLeast(MedtrumService::class, DiaconnG8Service::class)
    }

    @Test
    fun `the rileylink entry points have injectors`() {
        val injectors = testRoot().contributedMemberInjectors

        // RileyLinkService is abstract; the entry has to name the concrete subclass, which lives in
        // :pump:medtronic - the lookup uses the runtime class.
        assertThat(injectors.keys).containsAtLeast(
            RileyLinkBluetoothStateReceiver::class,
            RileyLinkBroadcastReceiver::class,
            RileyLinkMedtronicService::class,
            // Eros's entry is hand written (the service is Java, which crashes Metro's codegen) - so it is
            // the one that most needs asserting, because nothing else checks it exists.
            RileyLinkOmnipodService::class
        )
    }

    @Test
    fun `the danars service has an injector`() {
        val injectors = testRoot().contributedMemberInjectors

        assertThat(injectors.keys).contains(DanaRSService::class)
    }

    @Test
    fun `the insight entry points have injectors`() {
        val injectors = testRoot().contributedMemberInjectors

        assertThat(injectors.keys).containsAtLeast(
            InsightAlertActivity::class,
            InsightAlertService::class,
            InsightConnectionService::class
        )
    }

    @Test
    fun `the three services get separate injectors, not one shared base entry`() {
        val injectors = testRoot().contributedMemberInjectors

        assertThat(injectors[DanaRExecutionService::class])
            .isNotSameInstanceAs(injectors[DanaRKoreanExecutionService::class])
        assertThat(injectors[DanaRKoreanExecutionService::class])
            .isNotSameInstanceAs(injectors[DanaRv2ExecutionService::class])
    }
}
