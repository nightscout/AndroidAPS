package app.aaps.di.metro

import app.aaps.pump.danar.services.DanaRExecutionService
import app.aaps.pump.danarkorean.services.DanaRKoreanExecutionService
import app.aaps.pump.danarv2.services.DanaRv2ExecutionService
import app.aaps.pump.diaconn.service.DiaconnG8Service
import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import app.aaps.pump.medtrum.services.MedtrumService
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
    fun `the insight alert activity has an injector`() {
        val injectors = testRoot().contributedMemberInjectors

        assertThat(injectors.keys).contains(InsightAlertActivity::class)
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
