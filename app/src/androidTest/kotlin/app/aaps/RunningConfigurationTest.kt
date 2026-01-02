package app.aaps

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.AppRepository
import app.aaps.di.TestApplication
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

class RunningConfigurationTest @Inject constructor() {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var repository: AppRepository

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    private val profileData = "{\"_id\":\"653f90bc89f99714b4635b33\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.449Z\"," +
        "\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":14.618357917185001},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":8.99591256442154},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":10.12040163497423},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":11.244890705526924},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":13.493868846632308},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":13.493868846632308},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":13.493868846632308}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55361111111111}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.306},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.306},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.334},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.337},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.35},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.388},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.388},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.391},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.365},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.34},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.337},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.35},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.36},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.351},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.349},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.359},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.354},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.336},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.339},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.357},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.368},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.327},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.318},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.318}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"GMT\"}},\"app\":\"AAPS\",\"utcOffset\":120,\"identifier\":\"6b503f6c-b676-5746-b331-658b03d50843\",\"srvModified\":1698763282534,\"srvCreated\":1698664636986,\"subject\":\"Phone\"},{\"_id\":\"6511a54e3c60c21734f1988b\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.4490000Z\",\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":14.618357917185001},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":8.99591256442154},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":10.12040163497423},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":11.244890705526924},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":13.493868846632308},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":13.493868846632308},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":13.493868846632308}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55361111111111}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.306},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.306},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.334},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.337},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.35},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.388},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.388},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.391},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.365},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.34},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.337},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.35},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.36},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.351},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.349},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.359},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.354},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.336},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.339},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.357},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.368},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.327},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.318},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.318}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"Europe/Prague\"}}}"

    @Before
    fun inject() {
        context.androidInjector().inject(this)
        repository.clearDatabases()
    }

    @After
    fun tearDown() {
        repository.clearDatabases()
    }

    @SuppressLint("CheckResult")
    @Test
    fun runningConfigurationTest() {

        // There is existing RunningConfig
        assertThat(persistenceLayer.getPermanentRunningModeActiveAt(dateUtil.now()).mode).isEqualTo(RM.DEFAULT_MODE)
        assertThat(persistenceLayer.getRunningModeActiveAt(dateUtil.now()).mode).isEqualTo(RM.DEFAULT_MODE)

        // Change to OPEN_LOOP for 3 sec and let expire
        persistenceLayer.insertOrUpdateRunningMode(
            RM(
                timestamp = dateUtil.now(),
                mode = RM.Mode.OPEN_LOOP,
                duration = T.secs(3).msecs()
            ),
            action = Action.OPEN_LOOP_MODE,
            note = "Test",
            listValues = listOf(ValueWithUnit.SimpleString(RM.Mode.OPEN_LOOP.toString())),
            source = Sources.Loop
        ).blockingGet()
        assertThat(persistenceLayer.getRunningModeActiveAt(dateUtil.now()).mode).isEqualTo(RM.Mode.OPEN_LOOP)
        SystemClock.sleep(T.secs(3).msecs())
        assertThat(persistenceLayer.getRunningModeActiveAt(dateUtil.now()).mode).isEqualTo(RM.DEFAULT_MODE)
        assertThat(persistenceLayer.getRunningModes().size).isEqualTo(1)
        persistenceLayer.clearDatabases()

        // Change to permanent CLOSED_LOOP
        persistenceLayer.insertOrUpdateRunningMode(
            RM(
                timestamp = dateUtil.now(),
                mode = RM.Mode.CLOSED_LOOP,
                duration = 0
            ),
            action = Action.CLOSED_LOOP_MODE,
            note = "Test",
            listValues = listOf(ValueWithUnit.SimpleString(RM.Mode.CLOSED_LOOP.toString())),
            source = Sources.Loop
        ).blockingGet()
        assertThat(persistenceLayer.getRunningModeActiveAt(dateUtil.now()).mode).isEqualTo(RM.Mode.CLOSED_LOOP)
    }
}