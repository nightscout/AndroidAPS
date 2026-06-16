package app.aaps.implementation.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Characterization / invariant suite for [InsulinImpl], written as the acceptance criteria for the
 * planned "normalize-on-ingest, verbatim-load" migration (move normalization off the load path so the
 * config can ride the generic `SyncSpec(Bidirectional)` channel like QuickWizard/TempTargetPresets).
 *
 * These tests pin the **observable end state** (resulting `insulins` list + persisted JSON), NOT the
 * call sequence — deliberately, because the migration changes *where* normalization runs. A correct
 * migration must keep every test here green; the only one expected to change is the version-bump
 * semantics test, and only if that semantics is intentionally altered.
 *
 * Unlike [InsulinImplTest] (whose `preferences` mock returns a constant and ignores writes), this
 * suite uses a tiny in-memory `Preferences` fake for the two insulin keys so idempotency and the
 * actually-persisted content can be asserted.
 */
class InsulinImplMigrationTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uel: UserEntryLogger

    // In-memory backing for InsulinConfiguration. put()/putRemote() update it, get() reflects it.
    private var storedConfig = "{}"

    private val rapidPeakMs = InsulinType.OREF_RAPID_ACTING.insulinPeakTime   // 75 min
    private val rapidEndMs = InsulinType.OREF_RAPID_ACTING.insulinEndTime     // 8 h
    private val lyumjevPeakMs = InsulinType.OREF_LYUMJEV.insulinPeakTime      // 45 min
    private val freePeak30Ms = 30L * 60_000                                   // 30 min → no template match → FREE_PEAK

    @BeforeEach
    fun setup() {
        whenever(persistenceLayer.observeChanges(any<Class<*>>())).thenReturn(emptyFlow())
        // getProfile() is suspend & returns a nullable type → an unstubbed mock already returns null,
        // which is the "no active profile" case (iCfg then falls back to insulins[0]).
        // Deterministic, unique string per resource id — avoids depending on real translations while
        // still letting us assert "nickname == template label" by calling the same stub.
        whenever(rh.gs(any<Int>())).thenAnswer { "S" + it.getArgument<Int>(0) }

        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenAnswer { storedConfig }
        doAnswer { storedConfig = it.getArgument(1); null }
            .whenever(preferences).put(eq(StringNonKey.InsulinConfiguration), any<String>())
        // The one-time init normalize persists via putRemote (master-wins, no echo) — route it to storage too.
        doAnswer { storedConfig = it.getArgument(1); null }
            .whenever(preferences).putRemote(eq(StringNonKey.InsulinConfiguration), any<String>(), any<Long>())
    }

    private fun create(initialConfig: String): InsulinImpl {
        storedConfig = initialConfig
        return InsulinImpl(
            preferences, rh, profileFunction, persistenceLayer, aapsLogger, config, hardLimits, uel,
            CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private fun cfg(vararg entries: String) = """{"insulin":[${entries.joinToString(",")}]}"""

    private fun ins(label: String = "x", endTime: Long = 18_000_000, peak: Long = freePeak30Ms, conc: Double = 1.0, nickname: String? = null): String {
        val nick = if (nickname != null) ""","insulinNickname":"$nickname"""" else ""
        return """{"insulinLabel":"$label","insulinEndTime":$endTime,"insulinPeakTime":$peak,"concentration":$conc$nick}"""
    }

    private fun storedInsulinCount() = JSONObject(storedConfig).getJSONArray("insulin").length()

    // ── Bootstrap / crash-guard: the list must NEVER be empty (iCfg falls back to insulins[0]) ──────

    @ParameterizedTest
    @ValueSource(strings = ["", "{}", "not json", """{"insulin":[]}""", """{"other":1}""", """{"insulin":null}"""])
    fun emptyOrMalformedConfigYieldsExactlyOneDefaultInsulin(input: String) {
        // The pre-fill inside applyConfiguration is the only thing keeping insulins[0] from throwing.
        // The migration must preserve this guarantee for every shape of empty/garbage input.
        val sut = create(input)

        assertThat(sut.insulins).hasSize(1)
        assertThat(sut.currentInsulinIndex).isEqualTo(0)
        assertThat(sut.insulins[0].insulinPeakTime).isEqualTo(rapidPeakMs)   // OREF_RAPID_ACTING default
        assertThat(sut.insulins[0].insulinEndTime).isEqualTo(rapidEndMs)
        assertThat(sut.insulins[0].concentration).isEqualTo(1.0)
        // Neither accessor may throw with no profile present.
        assertThat(sut.iCfg).isEqualTo(sut.insulins[0])
        assertThat(sut.currentInsulin()).isEqualTo(sut.insulins[0])
    }

    @Test
    fun defaultInsulinHasNonBlankLabelAndNickname() {
        val sut = create("{}")
        assertThat(sut.insulins[0].insulinLabel).isNotEmpty()
        assertThat(sut.insulins[0].insulinNickname).isNotEmpty()
    }

    // ── Numeric parameters are preserved verbatim through a load ────────────────────────────────────

    @Test
    fun validSingleInsulinPreservesNumericParameters() {
        val sut = create(cfg(ins(endTime = 18_000_000, peak = freePeak30Ms, conc = 1.0)))

        assertThat(sut.insulins).hasSize(1)
        sut.insulins[0].let {
            assertThat(it.insulinEndTime).isEqualTo(18_000_000)
            assertThat(it.insulinPeakTime).isEqualTo(freePeak30Ms)
            assertThat(it.concentration).isEqualTo(1.0)
            assertThat(it.dia).isEqualTo(5.0)
            assertThat(it.peak).isEqualTo(30)
        }
    }

    // ── Nickname normalization (blank → template label; provided → preserved) ───────────────────────

    @Test
    fun blankNicknameIsFilledFromPeakTemplate_freePeak() {
        val sut = create(cfg(ins(peak = freePeak30Ms, nickname = null))) // 30 min matches no template → FREE_PEAK
        assertThat(sut.insulins[0].insulinNickname).isEqualTo(rh.gs(InsulinType.OREF_FREE_PEAK.label))
    }

    @Test
    fun blankNicknameIsFilledFromPeakTemplate_rapid() {
        val sut = create(cfg(ins(peak = rapidPeakMs, endTime = rapidEndMs, nickname = null)))
        assertThat(sut.insulins[0].insulinNickname).isEqualTo(rh.gs(InsulinType.OREF_RAPID_ACTING.label))
    }

    @Test
    fun providedNicknameIsPreserved() {
        val sut = create(cfg(ins(nickname = "MyInsulin")))
        assertThat(sut.insulins[0].insulinNickname).isEqualTo("MyInsulin")
    }

    @Test
    fun blankLabelIsRegeneratedToNonBlank() {
        val sut = create(cfg(ins(label = "", nickname = null)))
        assertThat(sut.insulins[0].insulinLabel).isNotEmpty()
    }

    // ── Multiplicity: distinct kept, current index, label uniqueness, dedup ─────────────────────────

    @Test
    fun twoDistinctInsulinsAreBothKept() {
        val sut = create(cfg(ins(peak = rapidPeakMs, endTime = rapidEndMs), ins(peak = lyumjevPeakMs, endTime = rapidEndMs)))
        assertThat(sut.insulins).hasSize(2)
    }

    @Test
    fun currentInsulinIndexAfterLoadPointsAtLastEntry() {
        val sut = create(cfg(ins(peak = rapidPeakMs, endTime = rapidEndMs), ins(peak = lyumjevPeakMs, endTime = rapidEndMs)))
        assertThat(sut.currentInsulinIndex).isEqualTo(sut.insulins.size - 1)
    }

    @Test
    fun sameParamsDifferentLabelsProduceUniqueLabels() {
        // Both normalize to the same base name → collision rename must keep the persisted labels distinct.
        val sut = create(cfg(ins(label = "A", peak = freePeak30Ms), ins(label = "B", peak = freePeak30Ms)))
        assertThat(sut.insulins).hasSize(2)
        assertThat(sut.insulins[0].insulinLabel).isNotEqualTo(sut.insulins[1].insulinLabel)
    }

    @Test
    fun duplicateOfNormalizedEntryCollapsesAtInit() {
        // Dedup now happens once at the init normalize, NOT on every load (load is verbatim). Construct
        // once to get the normalized form, duplicate a normalized entry, then construct again over the
        // duplicate-bearing config — the init normalize must collapse it (its label equals the
        // regenerated form → isEqual).
        create(cfg(ins(label = "", peak = rapidPeakMs, endTime = rapidEndMs), ins(label = "", peak = lyumjevPeakMs, endTime = rapidEndMs)))
        val arr = JSONObject(storedConfig).getJSONArray("insulin")
        val withDuplicate = """{"insulin":[${arr.getJSONObject(0)},${arr.getJSONObject(0)},${arr.getJSONObject(1)}]}"""

        val sut = create(withDuplicate)

        assertThat(sut.insulins).hasSize(2)
    }

    // ── Idempotency / fixed point: the heart of the migration ───────────────────────────────────────

    @Test
    fun reloadIsAFixedPoint_singleInsulin() {
        // After the first load normalizes the data, reloading must produce byte-identical persisted JSON
        // and an identical in-memory list. This is exactly the "verbatim load" invariant the migration
        // relies on (and what the storeSettings band-aid skips the redundant write for).
        val sut = create(cfg(ins(nickname = null)))
        val snapshot = storedConfig

        sut.loadSettings()

        assertThat(storedConfig).isEqualTo(snapshot)
        assertThat(sut.insulins).hasSize(1)
    }

    @Test
    fun reloadIsAFixedPoint_twoDistinctInsulins() {
        val sut = create(cfg(ins(peak = rapidPeakMs, endTime = rapidEndMs), ins(peak = lyumjevPeakMs, endTime = rapidEndMs)))
        val snapshot = storedConfig

        sut.loadSettings()

        assertThat(storedConfig).isEqualTo(snapshot)
        assertThat(sut.insulins).hasSize(2)
    }

    // ── Persistence: storeSettings writes the current list ──────────────────────────────────────────

    @Test
    fun storeSettingsPersistsCurrentInsulinList() {
        val sut = create("{}")
        assertThat(storedInsulinCount()).isEqualTo(1)

        sut.addNewInsulin(ICfg(insulinLabel = "", insulinEndTime = rapidEndMs, insulinPeakTime = lyumjevPeakMs, concentration = 1.0), ue = false)

        assertThat(sut.insulins).hasSize(2)
        assertThat(storedInsulinCount()).isEqualTo(2)
    }

    // ── Reload writes nothing (verbatim load is the echo guard) ──────────────────────────────────────

    @Test
    fun reloadWritesNothing() {
        // Applying a snapshot (local reload or master push) is a pure verbatim re-parse — it must NOT
        // write the config back, or it would echo to the master and form a sync loop.
        val sut = create(cfg(ins()))
        val before = storedConfig

        sut.loadSettings()

        assertThat(storedConfig).isEqualTo(before)
        verify(preferences, never()).put(eq(StringNonKey.InsulinConfiguration), any<String>())
    }

    // ── add / remove / current selection ────────────────────────────────────────────────────────────

    @Test
    fun addNewInsulinAppendsSelectsItPersistsAndLogs() {
        val sut = create("{}")
        clearInvocations(uel) // the default-insulin bootstrap during create() already logged a NEW_INSULIN

        sut.addNewInsulin(ICfg(insulinLabel = "", insulinEndTime = rapidEndMs, insulinPeakTime = lyumjevPeakMs, concentration = 1.0), ue = true)

        assertThat(sut.insulins).hasSize(2)
        assertThat(sut.currentInsulinIndex).isEqualTo(1)
        assertThat(storedInsulinCount()).isEqualTo(2)
        verify(uel).log(eq(Action.NEW_INSULIN), any<Sources>(), any<String>(), any<ValueWithUnit>())
    }

    @Test
    fun removeCurrentInsulinRemovesResetsIndexPersistsAndLogs() {
        val sut = create(cfg(ins(peak = rapidPeakMs, endTime = rapidEndMs), ins(peak = lyumjevPeakMs, endTime = rapidEndMs)))
        sut.currentInsulinIndex = 1

        sut.removeCurrentInsulin()

        assertThat(sut.insulins).hasSize(1)
        assertThat(sut.currentInsulinIndex).isEqualTo(0)
        assertThat(storedInsulinCount()).isEqualTo(1)
        verify(uel).log(eq(Action.INSULIN_REMOVED), any<Sources>(), any<String>(), any<ValueWithUnit>())
    }

    @Test
    fun removeCurrentInsulinKeepsAtLeastOneInsulin() {
        // Invariant: the list is never emptied. With verbatim load there is no per-load prefill to
        // recover an empty list, so removeCurrentInsulin must no-op on a single-element list.
        val sut = create("{}") // one seeded default
        assertThat(sut.insulins).hasSize(1)

        sut.removeCurrentInsulin()

        assertThat(sut.insulins).hasSize(1)
    }

    @Test
    fun currentInsulinReflectsCurrentInsulinIndex() {
        val sut = create(cfg(ins(peak = rapidPeakMs, endTime = rapidEndMs), ins(peak = lyumjevPeakMs, endTime = rapidEndMs)))

        sut.currentInsulinIndex = 0
        assertThat(sut.currentInsulin()).isEqualTo(sut.insulins[0])
        sut.currentInsulinIndex = 1
        assertThat(sut.currentInsulin()).isEqualTo(sut.insulins[1])
    }

    // ── iCfg sourcing: profile-driven, list only as fallback (orthogonal to the config cache) ────────

    @Test
    fun iCfgFallsBackToFirstInsulinWhenNoProfile() {
        val sut = create(cfg(ins())) // no getProfile() stub → mock returns null → fallback path
        assertThat(sut.iCfg).isEqualTo(sut.insulins[0])
    }

    @Test
    fun iCfgComesFromActiveProfileWhenPresent() {
        val known = ICfg(insulinLabel = "known", insulinEndTime = 18_000_000, insulinPeakTime = freePeak30Ms, concentration = 1.0)
        val profile = mock<EffectiveProfile>()
        whenever(profile.iCfg).thenReturn(known)
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(profile) }

        val sut = create(cfg(ins()))

        assertThat(sut.iCfg).isEqualTo(known)
    }
}
