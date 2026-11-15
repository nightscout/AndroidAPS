package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Instant

/**
 * The real [AutotuneIob]: history rows from the database in, treatments, insulin on board and the
 * exported files out.
 *
 * [AutotunePrepTest] replaces this class with a fake, so before these tests nothing checked how the
 * history is read, how a temporary basal is cut at each hour, or what the exported files contain.
 * The days are chosen so that the cutting meets the time zone: both daylight saving changes in
 * Prague, and Kathmandu, whose offset is not a whole hour.
 */
class AutotuneIobGoldenTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var autotuneFS: AutotuneFS

    private val iCfg = ICfg("PumpInsulin", peak = 75, dia = 5.0, concentration = 1.0)

    @Test
    fun `spring forward in Prague`() {
        inZone("Europe/Prague") { runDay("iob_prague_spring.txt", Instant.parse("2022-03-27T10:00:00Z").toEpochMilliseconds()) }
    }

    @Test
    fun `fall back in Prague`() {
        inZone("Europe/Prague") { runDay("iob_prague_autumn.txt", Instant.parse("2022-10-30T10:00:00Z").toEpochMilliseconds()) }
    }

    @Test
    fun `offset of five hours and 45 minutes`() {
        inZone("Asia/Kathmandu") { runDay("iob_kathmandu.txt", Instant.parse("2022-05-21T10:00:00Z").toEpochMilliseconds()) }
    }

    /** A profile whose basal changes during the day, so a cut at the wrong hour picks a wrong rate. */
    private fun profile(): ProfileSealed.EPS = ProfileSealed.EPS(
        EPS(
            timestamp = 0,
            basalBlocks = listOf(
                Block(T.hours(3).msecs(), 0.6),
                Block(T.hours(4).msecs(), 0.8),
                Block(T.hours(5).msecs(), 1.2),
                Block(T.hours(6).msecs(), 0.9),
                Block(T.hours(6).msecs(), 0.75)
            ),
            isfBlocks = listOf(Block(T.hours(24).msecs(), 50.0)),
            icBlocks = listOf(Block(T.hours(24).msecs(), 10.0)),
            targetBlocks = listOf(TargetBlock(T.hours(24).msecs(), 100.0, 100.0)),
            glucoseUnit = GlucoseUnit.MGDL,
            originalProfileName = "Test",
            originalCustomizedName = "Test",
            originalTimeshift = 0,
            originalPercentage = 100,
            originalDuration = 0,
            originalEnd = 0,
            iCfg = iCfg
        ),
        activePlugin
    )

    private fun runDay(golden: String, dayTime: Long) = runBlocking {
        // The same day bounds the plugin uses: 4 AM to 4 AM.
        val from = MidnightTime.before(dayTime, SystemTimeZone.current()) + T.hours(4).msecs()
        val to = from + T.hours(24).msecs()
        val profile = profile()
        whenever(profileFunction.getProfile(any())).thenReturn(profile)
        stubHistory(from, to)

        val tuned = ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger).with(profile, iCfg)
        val autotuneIob = AutotuneIob(aapsLogger, persistenceLayer, profileFunction, preferences, dateUtil, autotuneFS)
        autotuneIob.initializeData(from, to, tuned)

        val report = StringBuilder()
        report.append("from ${AutotuneGolden.utc(from)} to ${AutotuneGolden.utc(to)}\n")
        report.append("== meals\n")
        autotuneIob.meals.forEach { report.append("${AutotuneGolden.utc(it.timestamp)} ${it.amount}\n") }
        report.append("== boluses used for insulin on board (${autotuneIob.boluses.size})\n")
        autotuneIob.boluses.forEach { report.append("${AutotuneGolden.utc(it.timestamp)} ${it.amount} ${it.type} ${it.ids.nightscoutId}\n") }
        report.append("== insulin on board\n")
        var time = from
        while (time <= to) {
            val iob = autotuneIob.getIOB(time, iCfg)
            report.append("${AutotuneGolden.utc(time)} iob=${iob.iob} activity=${iob.activity}\n")
            time += T.mins(30).msecs()
        }
        report.append("== glucoseToJSON\n").append(AutotuneGolden.json(autotuneIob.glucoseToJSON())).append('\n')
        report.append("== nsHistoryToJSON\n").append(AutotuneGolden.json(autotuneIob.nsHistoryToJSON())).append('\n')
        report.append("== bolusesToJSON\n").append(AutotuneGolden.json(autotuneIob.bolusesToJSON())).append('\n')
        AutotuneGolden.assertMatches(golden, report.toString())
    }

    private suspend fun stubHistory(from: Long, to: Long) {
        val h = T.hours(1).msecs()
        val m = T.mins(1).msecs()
        val pumpIds = IDs(pumpType = PumpType.DANA_I, pumpSerial = "DI12345", pumpId = 4321L, nightscoutId = "ns_pump")

        // Every 20 minutes, newest first, as the database answers.
        val glucose = (0..72).map { i ->
            val value = (120 + 40 * sin(i / 6.0)).roundToLong().toDouble()
            GV(
                timestamp = from + i * 20 * m, raw = value, value = value, noise = null,
                trendArrow = if (i % 2 == 0) TrendArrow.FLAT else TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.DEXCOM_UNKNOWN,
                ids = IDs(nightscoutId = if (i % 3 == 0) "bg$i" else null)
            )
        }.filter { it.timestamp <= to }.reversed()
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(glucose)

        val carbs = listOf(
            CA(timestamp = from + 2 * h, amount = 30.0, duration = 0, notes = "lunch/dinner", ids = IDs(nightscoutId = "ns_carb1")),
            CA(timestamp = from + 5 * h, amount = 12.0, duration = 0),
            CA(timestamp = from + 9 * h, amount = 8.0, duration = T.mins(60).msecs(), ids = pumpIds.copy()),
            CA(timestamp = from + 3 * h, amount = 20.0, duration = 0, isValid = false),
            CA(timestamp = from + 4 * h, amount = 0.0, duration = 0),
            CA(timestamp = from - 1 * h, amount = 15.0, duration = 0)
        )
        whenever(persistenceLayer.getCarbsFromTimeToTimeExpanded(any(), any(), any())).thenReturn(carbs)

        val boluses = listOf(
            BS(timestamp = from + 2 * h, amount = 4.5, type = BS.Type.NORMAL, ids = pumpIds.copy(nightscoutId = "ns_bolus1"), iCfg = iCfg),
            BS(timestamp = from + 3 * h + 20 * m, amount = 0.3, type = BS.Type.SMB, iCfg = iCfg),
            BS(timestamp = from + 1 * h, amount = 0.5, type = BS.Type.PRIMING, iCfg = iCfg),
            BS(timestamp = from + 6 * h, amount = 2.0, type = BS.Type.NORMAL, isValid = false, iCfg = iCfg),
            BS(timestamp = from - 2 * h, amount = 1.25, type = BS.Type.NORMAL, iCfg = iCfg),
            BS(timestamp = from + 8 * h, amount = 1.0, type = BS.Type.NORMAL, notes = "a/b \"quoted\"", iCfg = iCfg)
        )
        whenever(persistenceLayer.getBolusesFromTimeToTime(any(), any(), any())).thenReturn(boluses)

        val temporaryBasals = listOf(
            TB(timestamp = from + 30 * m, duration = 90 * m, rate = 150.0, isAbsolute = false, type = TB.Type.NORMAL, ids = IDs(nightscoutId = "ns_tb1")),
            TB(timestamp = from + 3 * h, duration = 45 * m, rate = 0.0, isAbsolute = true, type = TB.Type.NORMAL),
            TB(timestamp = from + 10 * h + 15 * m, duration = 30 * m, rate = 50.0, isAbsolute = false, type = TB.Type.NORMAL),
            TB(timestamp = from + 12 * h, duration = 30 * m, rate = 200.0, isAbsolute = false, type = TB.Type.NORMAL, isValid = false),
            // Shorter than a minute: not counted.
            TB(timestamp = from + 13 * h, duration = 30_000, rate = 120.0, isAbsolute = false, type = TB.Type.NORMAL),
            // Starts before the day and runs over the daylight saving change in Prague.
            TB(timestamp = from - 3 * h, duration = 140 * m, rate = 2.15, isAbsolute = true, type = TB.Type.NORMAL, ids = pumpIds.copy(endId = 99L, nightscoutId = "ns_tb2"))
        )
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(temporaryBasals)

        val extendedBoluses = listOf(
            EB(timestamp = from + 14 * h, duration = 60 * m, amount = 2.0, ids = IDs(nightscoutId = "ns_eb1")),
            EB(timestamp = from + 16 * h, duration = 90 * m, amount = 1.5, isEmulatingTempBasal = true, ids = IDs(nightscoutId = "ns_eb2")),
            EB(timestamp = from + 18 * h, duration = 30 * m, amount = 1.0, isValid = false)
        )
        whenever(persistenceLayer.getExtendedBolusesStartingFromTimeToTime(any(), any(), any())).thenReturn(extendedBoluses)
    }
}
