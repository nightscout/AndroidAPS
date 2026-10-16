package app.aaps.pump.omnipod.eros

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTimeZone
import org.joda.time.tz.UTCProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OmnipodErosPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var aapsOmnipodErosManager: AapsOmnipodErosManager
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var erosHistoryDatabase: ErosHistoryDatabase
    @Mock lateinit var erosPodStateManager: ErosPodStateManager
    @Mock lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Mock lateinit var aapsOmnipodUtil: AapsOmnipodUtil
    @Mock lateinit var omnipodAlertUtil: OmnipodAlertUtil
    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var plugin: OmnipodErosPumpPlugin

    @BeforeEach
    fun prepare() {
        DateTimeZone.setProvider(UTCProvider())
        whenever(rh.gs(ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong()))
            .thenReturn("")

        plugin = OmnipodErosPumpPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context,
            erosPodStateManager, aapsOmnipodErosManager, fabricPrivacy, rileyLinkServiceData, aapsOmnipodUtil,
            rileyLinkUtil, omnipodAlertUtil, pumpSync, uiInteraction, notificationManager, erosHistoryDatabase, pumpEnactResultProvider,
            protectionCheck, blePreCheck
        )
    }

    @Test fun `setTempBasalPercent should throw because pump does not support percent basal rate`() {
        assertThrows<IllegalStateException> {
            runBlocking { plugin.setTempBasalPercent(80, 30, false, PumpSync.TemporaryBasalType.NORMAL) }
        }
    }

    /** #2454: each rate rounded to the nearest 0.05 U/h, and an hour is the insulin of its two half hours. */
    @Test fun `the delivered basal is the schedule of the pod hour by hour`() {
        val profile = mock<Profile>()
        whenever(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 0.5), Profile.ProfileValue(1800, 1.0), Profile.ProfileValue(7200, 0.42)))
        val schedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile)
        whenever(erosPodStateManager.isPodActivationCompleted).thenReturn(true)
        whenever(erosPodStateManager.isBasalCertain).thenReturn(true)
        whenever(erosPodStateManager.basalSchedule).thenReturn(schedule)

        val delivered = plugin.deliveredBasal()!!

        assertThat(delivered.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
        assertThat(delivered.map { it.value }).containsExactlyElementsIn(listOf(0.75, 1.0) + List(22) { 0.4 }).inOrder()
    }

    @Test fun `the delivered basal is unknown without an activated pod`() {
        whenever(erosPodStateManager.isPodActivationCompleted).thenReturn(false)
        assertThat(plugin.deliveredBasal()).isNull()
    }

    /** After a set that may have failed, the pod may still run the old schedule. */
    @Test fun `the delivered basal is unknown while the schedule is uncertain`() {
        whenever(erosPodStateManager.isPodActivationCompleted).thenReturn(true)
        whenever(erosPodStateManager.isBasalCertain).thenReturn(false)
        assertThat(plugin.deliveredBasal()).isNull()
    }
}
