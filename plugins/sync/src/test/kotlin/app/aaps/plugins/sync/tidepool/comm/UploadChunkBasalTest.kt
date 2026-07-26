package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.TimeZone

/**
 * Exercises [UploadChunk]'s basal generator (the `getBasals` boundary walk) end-to-end through the
 * public [UploadChunk.get] JSON. All other record sources are stubbed empty so only `basal` records
 * appear. The default time zone is forced to UTC so `secondsFromMidnight` and the profile-block
 * boundaries are deterministic ([t0] is a UTC midnight).
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadChunkBasalTest {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: UploadChunk
    private lateinit var defaultProfile: EffectiveProfile
    private val originalTz: TimeZone = TimeZone.getDefault()

    private val iCfg = ICfg(insulinLabel = "Fake", insulinEndTime = 9 * 3600 * 1000, insulinPeakTime = 60 * 60 * 1000, concentration = 1.0)

    /** A UTC midnight (day 19000 after the epoch) so seconds-from-midnight maths are clean. */
    private val t0 = 19_000L * 86_400_000L

    private fun at(hours: Double): Long = t0 + (hours * 3_600_000L).toLong()

    @BeforeEach
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        whenever(dateUtil.toISOAsUTC(any())).thenAnswer { it.getArgument<Long>(0).toString() }
        whenever(dateUtil.toISONoZone(any())).thenAnswer { it.getArgument<Long>(0).toString() }
        whenever(dateUtil.getTimeZoneOffsetMinutes(any())).thenReturn(0)
        defaultProfile = profileWith(0 to 0.5)
        sut = UploadChunk(preferences, rxBus, aapsLogger, profileFunction, profileUtil, activePlugin, persistenceLayer, dateUtil)
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    // ---------------- helpers ----------------

    private fun profileWith(vararg blocks: Pair<Int, Double>): EffectiveProfile {
        val sorted = blocks.sortedBy { it.first }
        val profile = mock<EffectiveProfile>()
        whenever(profile.getBasalValues()).thenReturn(sorted.map { Profile.ProfileValue(it.first, it.second) }.toTypedArray())
        whenever(profile.getBasalTimeFromMidnight(any<Int>())).thenAnswer { inv -> rateAt(sorted, inv.getArgument<Int>(0)) }
        whenever(profile.getBasal(any<Long>())).thenAnswer { inv -> rateAt(sorted, secondsOfDay(inv.getArgument<Long>(0))) }
        return profile
    }

    private fun rateAt(sorted: List<Pair<Int, Double>>, second: Int): Double =
        sorted.lastOrNull { it.first <= second }?.second ?: sorted.first().second

    private fun secondsOfDay(timestamp: Long): Int = ((timestamp % 86_400_000L) / 1000L).toInt()

    private fun tb(start: Long, durationHours: Double, rate: Double, absolute: Boolean = true, type: TB.Type = TB.Type.NORMAL): TB =
        TB(timestamp = start, type = type, isAbsolute = absolute, rate = rate, duration = (durationHours * 3_600_000L).toLong())

    private fun eps(timestamp: Long): EPS = EPS(
        timestamp = timestamp,
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL,
        originalProfileName = "Test",
        originalCustomizedName = "Test",
        originalTimeshift = 0,
        originalPercentage = 100,
        originalDuration = 0,
        originalEnd = 0,
        iCfg = iCfg
    )

    private fun rm(timestamp: Long, mode: RM.Mode): RM = RM(timestamp = timestamp, mode = mode, duration = 0)

    private suspend fun stub(
        tbrs: List<TB> = emptyList(),
        switches: List<EPS> = emptyList(),
        runningModes: List<RM> = emptyList(),
        modeAt: (Long) -> RM.Mode = { RM.Mode.OPEN_LOOP },
        profileAt: (Long) -> EffectiveProfile? = { defaultProfile }
    ) {
        whenever(persistenceLayer.getBolusesFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getCarbsFromTimeToTimeExpanded(any(), any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(tbrs)
        whenever(persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(any(), any(), any())).thenReturn(switches)
        whenever(persistenceLayer.getRunningModesFromTimeToTime(any(), any(), any())).thenReturn(runningModes)
        whenever(persistenceLayer.getRunningModeActiveAt(any())).thenAnswer { rm(it.getArgument<Long>(0), modeAt(it.getArgument<Long>(0))) }
        whenever(profileFunction.getProfile(any())).thenAnswer { profileAt(it.getArgument<Long>(0)) }
    }

    private data class Seg(
        val deliveryType: String,
        val rate: Double?,
        val duration: Long,
        val start: Long,
        val hasRate: Boolean,
        val hasScheduleName: Boolean,
        val hasSuppressed: Boolean
    )

    private fun basals(json: String): List<Seg> =
        JsonParser.parseString(json).asJsonArray
            .map { it.asJsonObject }
            .filter { it.has("type") && it.get("type").asString == "basal" }
            .map { o ->
                Seg(
                    deliveryType = o.get("deliveryType").asString,
                    rate = if (o.has("rate")) o.get("rate").asDouble else null,
                    duration = o.get("duration").asLong,
                    start = o.get("time").asString.toLong(),
                    hasRate = o.has("rate"),
                    hasScheduleName = o.has("scheduleName"),
                    hasSuppressed = o.has("suppressed")
                )
            }
            .sortedBy { it.start }

    private fun assertContiguous(segments: List<Seg>, start: Long, end: Long) {
        assertThat(segments.first().start).isEqualTo(start)
        for (i in 0 until segments.size - 1)
            assertThat(segments[i].start + segments[i].duration).isEqualTo(segments[i + 1].start)
        assertThat(segments.last().start + segments.last().duration).isEqualTo(end)
    }

    // ---------------- tests ----------------

    @Test
    fun `no temp basal emits a single scheduled segment covering the window`() = runTest {
        stub()
        val segments = basals(sut.get(t0, at(4.0)))
        assertThat(segments).hasSize(1)
        assertThat(segments[0].deliveryType).isEqualTo("scheduled")
        assertThat(segments[0].rate).isEqualTo(0.5)
        assertThat(segments[0].hasScheduleName).isTrue()
        assertThat(segments[0].hasSuppressed).isFalse()
        assertContiguous(segments, t0, at(4.0))
    }

    @Test
    fun `scheduled basal is split at profile block boundaries`() = runTest {
        val profile = profileWith(0 to 0.5, 21_600 to 1.0)
        stub(profileAt = { profile })
        val segments = basals(sut.get(t0, at(12.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("scheduled", "scheduled")
        assertThat(segments[0].rate).isEqualTo(0.5)
        assertThat(segments[0].start).isEqualTo(t0)
        assertThat(segments[0].duration).isEqualTo(at(6.0) - t0)
        assertThat(segments[1].rate).isEqualTo(1.0)
        assertThat(segments[1].start).isEqualTo(at(6.0))
        assertContiguous(segments, t0, at(12.0))
    }

    @Test
    fun `absolute temp basal becomes one automated segment between scheduled segments`() = runTest {
        stub(tbrs = listOf(tb(at(2.0), 3.0, 1.2)))
        val segments = basals(sut.get(t0, at(8.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("scheduled", "automated", "scheduled").inOrder()
        assertThat(segments[0].rate).isEqualTo(0.5)
        assertThat(segments[1].rate).isEqualTo(1.2)
        assertThat(segments[1].start).isEqualTo(at(2.0))
        assertThat(segments[1].duration).isEqualTo(at(5.0) - at(2.0))
        assertThat(segments[1].hasScheduleName).isTrue()
        assertThat(segments[1].hasSuppressed).isFalse()
        assertThat(segments[2].rate).isEqualTo(0.5)
        assertThat(segments[2].start).isEqualTo(at(5.0))
        assertContiguous(segments, t0, at(8.0))
    }

    @Test
    fun `percentage temp basal automated rate is profile rate times percent`() = runTest {
        stub(tbrs = listOf(tb(at(2.0), 3.0, 150.0, absolute = false)))
        val automated = basals(sut.get(t0, at(8.0))).single { it.deliveryType == "automated" }
        assertThat(automated.rate).isEqualTo(0.75) // 0.5 * 150 / 100
    }

    @Test
    fun `percentage temp basal splits at a profile block change with a recalculated rate`() = runTest {
        val profile = profileWith(0 to 0.5, 21_600 to 1.0)
        stub(
            tbrs = listOf(tb(at(4.0), 4.0, 200.0, absolute = false)),         // [4h, 8h] crosses the 06:00 block
            profileAt = { profile }
        )
        val segments = basals(sut.get(t0, at(10.0)))
        val automated = segments.filter { it.deliveryType == "automated" }
        assertThat(automated).hasSize(2)
        assertThat(automated[0].start).isEqualTo(at(4.0))
        assertThat(automated[0].rate).isEqualTo(1.0) // 0.5 * 2
        assertThat(automated[1].start).isEqualTo(at(6.0))
        assertThat(automated[1].rate).isEqualTo(2.0) // 1.0 * 2
        assertContiguous(segments, t0, at(10.0))
    }

    @Test
    fun `absolute temp basal is not split at a profile block change`() = runTest {
        val profile = profileWith(0 to 0.5, 21_600 to 1.0)
        stub(
            tbrs = listOf(tb(at(4.0), 4.0, 1.3)),                              // [4h, 8h] crosses the 06:00 block
            profileAt = { profile }
        )
        val segments = basals(sut.get(t0, at(10.0)))
        val automated = segments.filter { it.deliveryType == "automated" }
        assertThat(automated).hasSize(1)
        assertThat(automated[0].rate).isEqualTo(1.3)
        assertThat(automated[0].duration).isEqualTo(at(8.0) - at(4.0))
        assertContiguous(segments, t0, at(10.0))
    }

    @Test
    fun `pump suspend becomes a suspend segment without a rate`() = runTest {
        stub(tbrs = listOf(tb(at(2.0), 3.0, 0.0, absolute = true, type = TB.Type.PUMP_SUSPEND)))
        val segments = basals(sut.get(t0, at(8.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("scheduled", "suspend", "scheduled").inOrder()
        val suspend = segments.single { it.deliveryType == "suspend" }
        assertThat(suspend.hasRate).isFalse()
        assertThat(suspend.hasScheduleName).isFalse()
        assertThat(suspend.start).isEqualTo(at(2.0))
        assertThat(suspend.duration).isEqualTo(at(5.0) - at(2.0))
    }

    @Test
    fun `back to back temp basals leave no scheduled gap between them`() = runTest {
        stub(tbrs = listOf(tb(at(2.0), 2.0, 1.0), tb(at(4.0), 2.0, 1.5)))
        val segments = basals(sut.get(t0, at(8.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("scheduled", "automated", "automated", "scheduled").inOrder()
        assertThat(segments[1].rate).isEqualTo(1.0)
        assertThat(segments[2].rate).isEqualTo(1.5)
        assertContiguous(segments, t0, at(8.0))
    }

    @Test
    fun `temp basal active at the window start is clamped to the window`() = runTest {
        stub(tbrs = listOf(tb(at(-1.0), 3.0, 1.0)))                            // [-1h, +2h] -> active at start
        val segments = basals(sut.get(t0, at(8.0)))
        assertThat(segments.first().deliveryType).isEqualTo("automated")
        assertThat(segments.first().start).isEqualTo(t0)
        assertThat(segments.first().duration).isEqualTo(at(2.0) - t0)
        assertThat(segments[1].deliveryType).isEqualTo("scheduled")
        assertContiguous(segments, t0, at(8.0))
    }

    @Test
    fun `overlapping temp basals do not overlap in output and the newer one wins`() = runTest {
        stub(tbrs = listOf(tb(at(2.0), 4.0, 1.0), tb(at(3.0), 2.0, 2.0)))      // older [2,6], newer [3,5]
        val segments = basals(sut.get(t0, at(8.0)))
        assertContiguous(segments, t0, at(8.0))
        assertThat(segments.map { it.deliveryType })
            .containsExactly("scheduled", "automated", "automated", "automated", "scheduled").inOrder()
        val middle = segments.single { it.start == at(3.0) }
        assertThat(middle.rate).isEqualTo(2.0)
        assertThat(middle.duration).isEqualTo(at(5.0) - at(3.0))
        assertThat(segments.single { it.start == at(2.0) }.rate).isEqualTo(1.0)
        assertThat(segments.single { it.start == at(5.0) }.rate).isEqualTo(1.0)
    }

    @Test
    fun `scheduled basal is split at an effective profile switch`() = runTest {
        val before = profileWith(0 to 0.5)
        val after = profileWith(0 to 0.8)
        stub(switches = listOf(eps(at(5.0))), profileAt = { t -> if (t < at(5.0)) before else after })
        val segments = basals(sut.get(t0, at(10.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("scheduled", "scheduled")
        assertThat(segments[0].start).isEqualTo(t0)
        assertThat(segments[0].rate).isEqualTo(0.5)
        assertThat(segments[0].duration).isEqualTo(at(5.0) - t0)
        assertThat(segments[1].start).isEqualTo(at(5.0))
        assertThat(segments[1].rate).isEqualTo(0.8)
        assertContiguous(segments, t0, at(10.0))
    }

    @Test
    fun `no scheduled basal is emitted while no profile is set`() = runTest {
        val profile = profileWith(0 to 0.5)
        stub(switches = listOf(eps(at(3.0))), profileAt = { t -> if (t < at(3.0)) null else profile })
        val segments = basals(sut.get(t0, at(8.0)))
        assertThat(segments).hasSize(1)
        assertThat(segments[0].deliveryType).isEqualTo("scheduled")
        assertThat(segments[0].start).isEqualTo(at(3.0))
        assertThat(segments[0].duration).isEqualTo(at(8.0) - at(3.0))
    }

    @Test
    fun `no basal records are produced when no profile is available`() = runTest {
        stub(profileAt = { null })
        assertThat(basals(sut.get(t0, at(8.0)))).isEmpty()
    }

    @Test
    fun `profile-rate gap during a closed loop is emitted as automated`() = runTest {
        stub(modeAt = { RM.Mode.CLOSED_LOOP })
        val segments = basals(sut.get(t0, at(4.0)))
        assertThat(segments).hasSize(1)
        assertThat(segments[0].deliveryType).isEqualTo("automated")
        assertThat(segments[0].rate).isEqualTo(0.5)
        assertThat(segments[0].hasScheduleName).isTrue()
        assertContiguous(segments, t0, at(4.0))
    }

    @Test
    fun `closed-loop profile gaps around a temp basal stay one automated band`() = runTest {
        // Loop closed for the whole window: the profile-rate gaps before/after the temp basal are
        // automated too, so the three records share the automated delivery type (no M markers).
        stub(tbrs = listOf(tb(at(2.0), 3.0, 1.2)), modeAt = { RM.Mode.CLOSED_LOOP_LGS })
        val segments = basals(sut.get(t0, at(8.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("automated", "automated", "automated").inOrder()
        assertThat(segments[0].rate).isEqualTo(0.5)
        assertThat(segments[1].rate).isEqualTo(1.2)
        assertThat(segments[2].rate).isEqualTo(0.5)
        assertContiguous(segments, t0, at(8.0))
    }

    @Test
    fun `profile-rate gap is split at a running-mode change between scheduled and automated`() = runTest {
        // Open loop until 5h, then closed loop: the gap is scheduled before the change and automated after.
        stub(
            runningModes = listOf(rm(at(5.0), RM.Mode.CLOSED_LOOP)),
            modeAt = { t -> if (t < at(5.0)) RM.Mode.OPEN_LOOP else RM.Mode.CLOSED_LOOP }
        )
        val segments = basals(sut.get(t0, at(10.0)))
        assertThat(segments.map { it.deliveryType }).containsExactly("scheduled", "automated").inOrder()
        assertThat(segments[0].start).isEqualTo(t0)
        assertThat(segments[0].duration).isEqualTo(at(5.0) - t0)
        assertThat(segments[1].start).isEqualTo(at(5.0))
        assertThat(segments[1].rate).isEqualTo(0.5)
        assertContiguous(segments, t0, at(10.0))
    }
}
