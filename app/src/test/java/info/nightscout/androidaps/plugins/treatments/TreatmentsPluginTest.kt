package info.nightscout.androidaps.plugins.treatments

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.insulin.InsulinOrefRapidActingPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(FabricPrivacy::class, MainApp::class, DatabaseHelper::class)
class TreatmentsPluginTest : TestBaseWithProfile() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var databaseHelper: DatabaseHelper
    @Mock lateinit var treatmentService: TreatmentService
    @Mock lateinit var nsUpload: NSUpload
    @Mock lateinit var uploadQueue: UploadQueue

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is TemporaryBasal) {
                it.aapsLogger = aapsLogger
                it.activePlugin = activePluginProvider
                it.profileFunction = profileFunction
                it.sp = sp
            }
        }
    }

    lateinit var insulinOrefRapidActingPlugin: InsulinOrefRapidActingPlugin
    lateinit var sot: TreatmentsPlugin

    @Before
    fun prepare() {
        PowerMockito.mockStatic(MainApp::class.java)
        `when`(MainApp.getDbHelper()).thenReturn(databaseHelper)

        insulinOrefRapidActingPlugin = InsulinOrefRapidActingPlugin(profileInjector, resourceHelper, profileFunction, rxBus, aapsLogger)

        `when`(profileFunction.getProfile(ArgumentMatchers.anyLong())).thenReturn(validProfile)
        `when`(activePluginProvider.activeInsulin).thenReturn(insulinOrefRapidActingPlugin)

        sot = TreatmentsPlugin(profileInjector, aapsLogger, rxBus, resourceHelper, context, sp, profileFunction, activePluginProvider, nsUpload, fabricPrivacy, dateUtil, uploadQueue)
        sot.service = treatmentService
    }

    @Test
    fun `zero TBR should produce zero absolute insulin`() {
        val now = DateUtil.now()
        val tbrs: MutableList<TemporaryBasal> = ArrayList()
        tbrs.add(TemporaryBasal(injector).date(now - T.hours(30).msecs()).duration(10000).percent(0))

        `when`(databaseHelper.getTemporaryBasalsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(tbrs)
        sot.initializeData(T.hours(30).msecs())
        val iob = sot.getAbsoluteIOBTempBasals(now)
        Assert.assertEquals(0.0, iob.iob, 0.0)
    }

    @Test
    fun `90% TBR and should produce less absolute insulin`() {
        val now = DateUtil.now()
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
    fun `110% TBR and should produce 10% more absolute insulin`() {
        val now = DateUtil.now()
        val tbrs: MutableList<TemporaryBasal> = ArrayList()
        `when`(databaseHelper.getTemporaryBasalsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(tbrs)
        sot.initializeData(T.hours(30).msecs())
        val iob100pct = sot.getAbsoluteIOBTempBasals(now)

        tbrs.add(TemporaryBasal(injector).date(now - T.hours(30).msecs()).duration(10000).percent(110))
        sot.initializeData(T.hours(30).msecs())
        val iob110pct = sot.getAbsoluteIOBTempBasals(now)
        Assert.assertEquals(1.1, iob110pct.basaliob / iob100pct.basaliob, 0.0001)
    }
}