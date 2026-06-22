package app.aaps.implementation.insulin

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Verifies the generic-channel client behavior for insulin after the verbatim-load migration:
 * a master push (cold-key write via `putRemote`) is picked up by the client self-observe, applied as a
 * pure verbatim re-parse, and produces **no echo write** back to the master.
 *
 * In-memory fake over the `@Mock preferences`: a single StateFlow is both the `observe` source and the
 * `get` value; `put` is a LOCAL edit (counted), `putRemote` is the master-wins apply (not counted).
 * `config.AAPSCLIENT = true` enables the self-observe; the Unconfined scope makes it react synchronously.
 */
class InsulinImplSyncTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uel: UserEntryLogger

    private val configFlow = MutableStateFlow("{}")
    private var localPutCount = 0

    @BeforeEach
    fun setup() {
        whenever(persistenceLayer.observeChanges(any<Class<*>>())).thenReturn(emptyFlow())
        whenever(rh.gs(any<Int>())).thenAnswer { "S" + it.getArgument<Int>(0) }
        whenever(config.AAPSCLIENT).thenReturn(true)

        whenever(preferences.observe(StringNonKey.InsulinConfiguration)).thenReturn(configFlow)
        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenAnswer { configFlow.value }
        doAnswer { configFlow.value = it.getArgument(1); localPutCount++; null } // local edit
            .whenever(preferences).put(eq(StringNonKey.InsulinConfiguration), any<String>())
        doAnswer { configFlow.value = it.getArgument(1); null } // master-wins apply, no echo
            .whenever(preferences).putRemote(eq(StringNonKey.InsulinConfiguration), any<String>(), any<Long>())
    }

    private fun create() = InsulinImpl(
        preferences, rh, profileFunction, persistenceLayer, aapsLogger, config, hardLimits, uel,
        CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun masterPushIsAppliedVerbatimWithoutEcho() {
        val sut = create() // init seeds a default + wires the self-observe
        localPutCount = 0   // ignore any bootstrap writes

        // Master pushes its (already-normalized) config via the cold-key channel (putRemote).
        val masterConfig =
            """{"insulin":[{"insulinLabel":"Master","insulinEndTime":18000000,"insulinPeakTime":2700000,"concentration":1.0,"insulinNickname":"NN"}]}"""
        preferences.putRemote(StringNonKey.InsulinConfiguration, masterConfig, 5L)

        // Self-observe applied it verbatim (label/nickname preserved exactly — no re-normalization)…
        assertThat(sut.insulins).hasSize(1)
        assertThat(sut.insulins[0].insulinLabel).isEqualTo("Master")
        assertThat(sut.insulins[0].insulinNickname).isEqualTo("NN")
        // …and applying it produced NO local write back to the master (no echo).
        assertThat(localPutCount).isEqualTo(0)
    }

    @Test
    fun initSeedsDefaultViaPutRemoteWithoutUplink() {
        // Fresh-install client (empty pref): init seeds a default so iCfg's insulins[0] fallback is safe,
        // but must persist it via putRemote (no syncedLocalChanges) — NOT a local put that would uplink
        // the client's bootstrap default and race the master's real config.
        val sut = create() // empty "{}" → client seeds a default

        assertThat(sut.insulins).hasSize(1)
        assertThat(localPutCount).isEqualTo(0) // seed went through putRemote, not a local put
    }
}
