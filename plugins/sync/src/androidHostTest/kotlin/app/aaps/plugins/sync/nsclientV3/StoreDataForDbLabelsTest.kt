package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Pins the type names `StoreDataForDbImpl` uses as counter keys and log labels.
 *
 * `StoreDataForDbImpl` counts what it stored per type and shows it in the NS client log, keyed by the
 * class's simple name. Those keys were read with `X::class.java.simpleName`, which is JVM only and had
 * to become `X::class.simpleName` for the class to reach commonMain - 111 call sites of it.
 *
 * A rename there is silent: the counters still add up, the log line just says something else, and no
 * existing test asserts the strings. So this pins both halves - that the two forms agree, and that
 * each one is the literal the log has always shown.
 */
class StoreDataForDbLabelsTest {

    private val expected: List<Pair<KClass<*>, String>> = listOf(
        BCR::class to "BCR",
        BS::class to "BS",
        CA::class to "CA",
        CAL::class to "CAL",
        DS::class to "DS",
        EB::class to "EB",
        EPS::class to "EPS",
        FD::class to "FD",
        GV::class to "GV",
        PS::class to "PS",
        RM::class to "RM",
        TB::class to "TB",
        TE::class to "TE",
        TT::class to "TT"
    )

    @Test
    fun kotlinSimpleNameMatchesTheJvmOne() {
        // The proof that swapping ::class.java.simpleName for ::class.simpleName changes no label.
        expected.forEach { (klass, _) ->
            assertThat(klass.simpleName).isEqualTo(klass.java.simpleName)
        }
    }

    @Test
    fun everyLabelIsTheNameTheLogHasAlwaysShown() {
        expected.forEach { (klass, name) ->
            assertThat(klass.simpleName).isEqualTo(name)
        }
    }
}
