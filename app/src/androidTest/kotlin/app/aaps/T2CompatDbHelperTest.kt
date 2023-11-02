package app.aaps

import androidx.test.core.app.ApplicationProvider
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.rx.events.EventDeviceStatusChange
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventFoodDatabaseChanged
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventOfflineChange
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.rx.events.EventTreatmentChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.events.Event1stFinished
import app.aaps.events.Event2ndFinished
import app.aaps.helpers.RxHelper
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

class T2CompatDbHelperTest @Inject constructor() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var l: L
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rxBus: RxBus

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun inject() {
        context.androidInjector().inject(this)
    }

    @After
    fun tearDown() {
        rxHelper.clear()
    }

    @Test
    fun dbHelperTest() {
        rxHelper.listen(Event1stFinished::class.java)
        rxHelper.waitFor(Event1stFinished::class.java, 60, "finish 1st test")
        // Prepare
        rxHelper.listen(EventNewBG::class.java)
        rxHelper.listen(EventNewHistoryData::class.java)
        rxHelper.listen(EventTreatmentChange::class.java)
        rxHelper.listen(EventTempBasalChange::class.java)
        rxHelper.listen(EventExtendedBolusChange::class.java)
        rxHelper.listen(EventProfileSwitchChanged::class.java)
        rxHelper.listen(EventEffectiveProfileSwitchChanged::class.java)
        rxHelper.listen(EventTempTargetChange::class.java)
        rxHelper.listen(EventTherapyEventChange::class.java)
        rxHelper.listen(EventFoodDatabaseChanged::class.java)
        rxHelper.listen(EventOfflineChange::class.java)
        rxHelper.listen(EventDeviceStatusChange::class.java)

        // Enable event logging
        l.findByName(LTag.EVENTS.name).enabled = true

        // EventProfileSwitchChanged tested in LoopTest
        // EventEffectiveProfileSwitchChanged tested in LoopTest
        // EventNewBG and EventNewHistoryData tested in LoopTest

        val now = dateUtil.now()

        // Let generate some carbs
        rxHelper.resetState(EventTreatmentChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        var detailedBolusInfo = DetailedBolusInfo().also {
            it.eventType = TE.Type.CARBS_CORRECTION
            it.carbs = 10.0
            it.context = context
            it.notes = "Note"
            it.carbsDuration = T.hours(1).msecs()
            it.carbsTimestamp = now
        }
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        // EventTreatmentChange should be triggered
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step1").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step2").first).isTrue()

        // Let generate some bolus
        rxHelper.resetState(EventTreatmentChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        detailedBolusInfo = DetailedBolusInfo().also {
            it.eventType = TE.Type.CORRECTION_BOLUS
            it.insulin = 1.0
            it.context = null
            it.notes = "Note"
        }
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        // EventTreatmentChange should be triggered
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step3").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step4").first).isTrue()

        rxBus.send(Event2ndFinished())
    }
}