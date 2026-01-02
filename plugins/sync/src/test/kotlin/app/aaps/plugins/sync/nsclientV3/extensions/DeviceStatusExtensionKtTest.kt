package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.DS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.nsclient.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclient.data.ProcessedDeviceStatusDataImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import javax.inject.Provider

@Suppress("SpellCheckingInspection")
internal class DeviceStatusExtensionKtTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var apsResultProvider: Provider<APSResult>
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var overviewData: OverviewData
    @Mock lateinit var calculationWorkflow: CalculationWorkflow

    private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    private lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler

    @BeforeEach
    fun setup() {
        processedDeviceStatusData = ProcessedDeviceStatusDataImpl(rh, dateUtil, preferences, apsResultProvider)
        nsDeviceStatusHandler = NSDeviceStatusHandler(preferences, config, dateUtil, runningConfiguration, processedDeviceStatusData, aapsLogger, persistenceLayer, overviewData, calculationWorkflow)
        whenever(config.AAPSCLIENT).thenReturn(true)
    }

    @Test
    fun dotest() {

        val deviceStatus = DS(
            timestamp = 10000,
            suggested = "{\"temp\":\"absolute\",\"bg\":133,\"tick\":-6,\"eventualBG\":67,\"targetBG\":99,\"insulinReq\":0,\"deliverAt\":\"2023-01-02T15:29:33.374Z\",\"sensitivityRatio\":1,\"variable_sens\":97.5,\"predBGs\":{\"IOB\":[133,127,121,116,111,106,101,97,93,89,85,81,78,75,72,69,67,65,62,60,58,57,55,54,52,51,50,49,48,47,46,45,45,44,43,43,42,42,41,41,41,41,40,40,40,40,39],\"ZT\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39,39,39,39,39,39,39],\"UAM\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39]},\"reason\":\"COB: 0, Dev: 0.1, BGI: -0.3, ISF: 5.4, CR: 13, Target: 5.5, minPredBG 2.2, minGuardBG 2.1, IOBpredBG 2.2, UAMpredBG 2.2; minGuardBG 2.1<4.0\",\"COB\":0,\"IOB\":0.692,\"duration\":90,\"rate\":0,\"timestamp\":\"2023-01-02T15:29:39.460Z\"}",
            iob = "{\"iob\":0.692,\"basaliob\":-0.411,\"activity\":0.0126,\"time\":\"2023-01-02T15:29:39.460Z\"}",
            enacted = "{\"temp\":\"absolute\",\"bg\":133,\"tick\":-6,\"eventualBG\":67,\"targetBG\":99,\"insulinReq\":0,\"deliverAt\":\"2023-01-02T15:29:33.374Z\",\"sensitivityRatio\":1,\"variable_sens\":97.5,\"predBGs\":{\"IOB\":[133,127,121,116,111,106,101,97,93,89,85,81,78,75,72,69,67,65,62,60,58,57,55,54,52,51,50,49,48,47,46,45,45,44,43,43,42,42,41,41,41,41,40,40,40,40,39],\"ZT\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39,39,39,39,39,39,39],\"UAM\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39]},\"reason\":\"COB: 0, Dev: 0.1, BGI: -0.3, ISF: 5.4, CR: 13, Target: 5.5, minPredBG 2.2, minGuardBG 2.1, IOBpredBG 2.2, UAMpredBG 2.2; minGuardBG 2.1<4.0\",\"COB\":0,\"IOB\":0.692,\"duration\":90,\"rate\":0,\"timestamp\":\"2023-01-02T15:29:39.460Z\"}",
            device = "openaps://samsung SM-G970F",
            pump = "{\"battery\":{\"percent\":75},\"status\":{\"status\":\"normal\",\"timestamp\":\"2023-01-02T15:20:20.656Z\"},\"extended\":{\"Version\":\"3.1.0.3-dev-e-295e1ad18f-2022.12.24\"," +
                "\"LastBolus\":\"02.01.23 15:24\",\"LastBolusAmount\":\"1\",\"TempBasalAbsoluteRate\":\"0\",\"TempBasalStart\":\"02.01.23 16:20\",\"TempBasalRemaining\":\"55\",\"BaseBasalRate\":\"0" +
                ".41\"," +
                "\"ActiveProfile\":\"L29_U200 IC\"},\"reservoir\":\"133\",\"clock\":\"2023-01-02T15:25:05.826Z\"}",
            uploaderBattery = 60,
            isCharging = false,
            configuration = "{\"insulin\":5,\"insulinConfiguration\":{},\"sensitivity\":2,\"sensitivityConfiguration\":{\"openapsama_min_5m_carbimpact\":8,\"absorption_cutoff\":4,\"autosens_max\":1.2,\"autosens_min\":0.7},\"overviewConfiguration\":{\"units\":\"mmol\",\"QuickWizard\":\"[]\",\"eatingsoon_duration\":60,\"eatingsoon_target\":4,\"activity_duration\":180,\"activity_target\":7.5,\"hypo_duration\":90,\"hypo_target\":8,\"low_mark\":3.9,\"high_mark\":10,\"statuslights_cage_warning\":72,\"statuslights_cage_critical\":96,\"statuslights_iage_warning\":120,\"statuslights_iage_critical\":150,\"statuslights_sage_warning\":168,\"statuslights_sage_critical\":336,\"statuslights_sbat_warning\":25,\"statuslights_sbat_critical\":5,\"statuslights_bage_warning\":720,\"statuslights_bage_critical\":800,\"statuslights_res_warning\":30,\"statuslights_res_critical\":10,\"statuslights_bat_warning\":50,\"statuslights_bat_critical\":25,\"boluswizard_percentage\":70},\"safetyConfiguration\":{\"age\":\"resistantadult\",\"treatmentssafety_maxbolus\":10,\"treatmentssafety_maxcarbs\":70}}"
        )

        val nsDeviceStatus = deviceStatus.toNSDeviceStatus()

        nsDeviceStatusHandler.handleNewData(arrayOf(nsDeviceStatus))
        assertThat(processedDeviceStatusData.pumpData!!.percent).isEqualTo(75)

        val nsDeviceStatus2 = nsDeviceStatus.convertToRemoteAndBack()
        assertThat(nsDeviceStatus2.device).isEqualTo(nsDeviceStatus.device)
        assertThat(nsDeviceStatus2.identifier).isEqualTo(nsDeviceStatus.identifier)
        assertThat(nsDeviceStatus2.srvCreated).isEqualTo(nsDeviceStatus.srvCreated)
        assertThat(nsDeviceStatus2.srvModified).isEqualTo(nsDeviceStatus.srvModified)
        assertThat(nsDeviceStatus2.createdAt).isEqualTo(nsDeviceStatus.createdAt)
        assertThat(nsDeviceStatus2.date).isEqualTo(nsDeviceStatus.date)
        assertThat(nsDeviceStatus2.uploaderBattery).isEqualTo(nsDeviceStatus.uploaderBattery)
        assertThat(nsDeviceStatus2.device).isEqualTo(nsDeviceStatus.device)
        assertThat(nsDeviceStatus2.uploader?.battery).isEqualTo(nsDeviceStatus.uploader?.battery)
        assertThat(nsDeviceStatus2.pump?.battery).isEqualTo(nsDeviceStatus.pump?.battery)
        assertThat(nsDeviceStatus2.openaps?.enacted?.toString()).isEqualTo(nsDeviceStatus.openaps?.enacted?.toString())
        assertThat(nsDeviceStatus2.configuration?.toString()).isEqualTo(nsDeviceStatus.configuration?.toString())
    }
}
