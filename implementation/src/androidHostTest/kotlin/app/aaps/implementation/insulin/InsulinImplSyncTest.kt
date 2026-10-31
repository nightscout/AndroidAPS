package app.aaps.implementation.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.generatedTextResolver
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
import kotlin.reflect.KClass

/**
 * Verifies how insulin follows a value written by someone else, on both roles.
 *
 * Client: a master push (cold-key write via `putRemote`) is picked up by the self-observe, applied as a
 * pure verbatim re-parse, and produces **no echo write** back to the master.
 * Master: a client push, a change back to an earlier value, and an emptied value (an import) are all
 * adopted without any screen, and the master's own edits do not trigger a reload.
 *
 * In-memory fake over the `@Mock preferences`: a single StateFlow is both the `observe` source and the
 * `get` value; `put` is a LOCAL edit (counted), `putRemote` is the master-wins apply (not counted).
 * `config.AAPSCLIENT` picks the role (client by default); the Unconfined scope makes the observer react
 * synchronously.
 */
class InsulinImplSyncTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    private val rh = generatedTextResolver()
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uel: UserEntryLogger

    private val configFlow = MutableStateFlow("{}")
    private var localPutCount = 0

    @BeforeEach
    fun setup() {
        whenever(persistenceLayer.observeChanges(any<KClass<*>>())).thenReturn(emptyFlow())
        whenever(config.AAPSCLIENT).thenReturn(true)

        whenever(preferences.observe(StringNonKey.InsulinConfiguration)).thenReturn(configFlow)
        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenAnswer { configFlow.value }
        doAnswer { configFlow.value = it.getArgument(1); localPutCount++; null } // local edit
            .whenever(preferences).put(eq(StringNonKey.InsulinConfiguration), any<String>())
        doAnswer { configFlow.value = it.getArgument(1); null } // master-wins apply, no echo
            .whenever(preferences).putRemote(eq(StringNonKey.InsulinConfiguration), any<String>(), any<Long>())
    }

    private fun create() = InsulinImpl(
        preferences, rh, profileFunction, aapsLogger, config, hardLimits, uel,
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

    // ---- Master. It used to reload only through the insulin screen's view model, which exists only while
    // the main UI is alive and skips the reload while an edit is unsaved. The reload hook that did this in
    // the service went with the move to the generic sync channel (7165e88f14). ----

    private fun insulin(nickname: String, peakMs: Long) =
        """{"insulinLabel":"$nickname","insulinEndTime":18000000,"insulinPeakTime":$peakMs,"concentration":1.0,"insulinNickname":"$nickname"}"""

    private fun cfg(vararg insulins: String) = """{"insulin":[${insulins.joinToString(",")}]}"""

    @Test
    fun masterAdoptsAClientPushWithNoScreenInvolved() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        val sut = create()
        localPutCount = 0

        // What the generic sync channel does with a client's edit: putRemote.
        preferences.putRemote(StringNonKey.InsulinConfiguration, cfg(insulin("FromClient", 2700000)), 5L)

        assertThat(sut.insulins.map { it.insulinNickname }).containsExactly("FromClient")
        assertThat(localPutCount).isEqualTo(0) // adopting is not an edit - nothing is sent back to the client
    }

    @Test
    fun masterAdoptsAChangeBackToAnEarlierValue() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        configFlow.value = cfg(insulin("X", 2700000))
        val sut = create()
        val x = configFlow.value // as normalized and stored by the master at init
        preferences.putRemote(StringNonKey.InsulinConfiguration, cfg(insulin("Y", 4500000)), 5L)
        val y = configFlow.value // as normalized and stored by the master
        preferences.putRemote(StringNonKey.InsulinConfiguration, x, 6L)
        assertThat(sut.insulins.map { it.insulinNickname }).containsExactly("X")

        // Back to Y, already in its stored form, so adopting it writes nothing. Remembering only the
        // master's own last write (y) would skip exactly this change and leave X in memory.
        preferences.putRemote(StringNonKey.InsulinConfiguration, y, 7L)

        assertThat(sut.insulins.map { it.insulinNickname }).containsExactly("Y")
    }

    @Test
    fun masterSeedsTheDefaultAgainWhenTheListIsEmptied() {
        // A value with no insulin list (a client on another version, or an old backup once an import makes
        // the flow emit - issue #5140): the pickers must still offer one.
        whenever(config.AAPSCLIENT).thenReturn(false)
        configFlow.value = cfg(insulin("Old", 2700000))
        val sut = create()

        configFlow.value = "{}" // written below this class, not through put or putRemote

        assertThat(sut.insulins).hasSize(1)
        assertThat(sut.insulins[0].insulinNickname).isNotEqualTo("Old")
    }

    @Test
    fun masterDoesNotReloadAfterItsOwnEdit() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        val sut = create()
        val before = sut.insulins.toList()

        sut.addNewInsulin(
            ICfg(insulinLabel = "", insulinEndTime = 18_000_000, insulinPeakTime = 2_700_000, concentration = 1.0)
                .also { it.insulinNickname = "Mine" }
        )

        // A reload would rebuild every entry from JSON; the existing ones are still the same objects.
        before.forEachIndexed { i, insulin -> assertThat(sut.insulins[i]).isSameInstanceAs(insulin) }
        assertThat(localPutCount).isEqualTo(1)
    }

    @Test
    fun clientKeepsALocalEditThatOvertookAPush() {
        // A master push arrives, but before the observer runs, a local edit stores over it. The edit wins,
        // and it is what the stored value holds - so the observer must not load the push it was woken by.
        val sut = create()
        sut.addNewInsulin(
            ICfg(insulinLabel = "", insulinEndTime = 18_000_000, insulinPeakTime = 2_700_000, concentration = 1.0)
                .also { it.insulinNickname = "Mine" }
        )
        val stored = configFlow.value
        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenReturn(stored)

        configFlow.value = cfg(insulin("FromMaster", 4500000)) // the push, seen by the observer only now

        assertThat(sut.insulins.map { it.insulinNickname }).contains("Mine")
        assertThat(sut.insulins.map { it.insulinNickname }).doesNotContain("FromMaster")
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
