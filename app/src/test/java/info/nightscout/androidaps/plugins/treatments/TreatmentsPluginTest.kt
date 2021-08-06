package info.nightscout.androidaps.plugins.treatments

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@Suppress("SpellCheckingInspection")
@RunWith(PowerMockRunner::class)
@PrepareForTest(FabricPrivacy::class, AppRepository::class)
class TreatmentsPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var repository: AppRepository

    val injector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    @Test fun dummy() {}
/*
    private lateinit var insulinOrefRapidActingPlugin: InsulinOrefRapidActingPlugin
    private lateinit var sot: TreatmentsPlugin

    @Before
    fun prepare() {
        insulinOrefRapidActingPlugin = InsulinOrefRapidActingPlugin(profileInjector, resourceHelper, profileFunction, rxBus, aapsLogger)

        `when`(profileFunction.getProfile(ArgumentMatchers.anyLong())).thenReturn(validProfile)
        `when`(activePluginProvider.activeInsulin).thenReturn(insulinOrefRapidActingPlugin)

        sot = TreatmentsPlugin(profileInjector, aapsLogger, rxBus, aapsSchedulers, resourceHelper, context, sp, profileFunction, activePluginProvider, nsUpload, fabricPrivacy, dateUtil, databaseHelper, repository)
        sot.service = treatmentService
    }

    @Test
    fun `zero TBR should produce zero absolute insulin`() {
        val now = dateUtil._now()
        val tbrs: MutableList<TemporaryBasal> = ArrayList()
        tbrs.add(TemporaryBasal(injector).date(now - T.hours(30).msecs()).duration(10000).percent(0))

        `when`(databaseHelper.getTemporaryBasalsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(tbrs)
        sot.initializeData(T.hours(30).msecs())
        val iob = sot.getAbsoluteIOBTempBasals(now)
        Assert.assertEquals(0.0, iob.iob, 0.0)
    }

    @Test
    fun `90pct TBR and should produce less absolute insulin`() {
        val now = dateUtil._now()
        val tbrs: MutableList<TemporaryBasal> = ArrayList()
        `when`(databaseHelper.getTemporaryBasalsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(tbrs)
        sot.initializeData(T.hours(30).msecs())
        val iob100pct = sot.getAbsoluteIOBTempBasals(now)

        tbrs.add(TemporaryBasal(injector).date(now - T.hours(30).msecs()).duration(10000).percent(90))
        sot.initializeData(T.hours(30).msecs())
        val iob90pct = sot.getAbsoluteIOBTempBasals(now)
        Assert.assertTrue(iob100pct.basaliob > iob90pct.basaliob)
    }

    @Test
    fun `110pct TBR and should produce 10pct more absolute insulin`() {
        val now = dateUtil._now()
        val tbrs: MutableList<TemporaryBasal> = ArrayList()
        `when`(databaseHelper.getTemporaryBasalsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(tbrs)
        sot.initializeData(T.hours(30).msecs())
        val iob100pct = sot.getAbsoluteIOBTempBasals(now)

        tbrs.add(TemporaryBasal(injector).date(now - T.hours(30).msecs()).duration(10000).percent(110))
        sot.initializeData(T.hours(30).msecs())
        val iob110pct = sot.getAbsoluteIOBTempBasals(now)
        Assert.assertEquals(1.1, iob110pct.basaliob / iob100pct.basaliob, 0.0001)
    }

 */
}