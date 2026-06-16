package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.MasterPairing
import app.aaps.core.nssdk.localmodel.configuration.NSAuthorizedClients
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class OrphanDetectorTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var config: Config
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var pairingRepository: ClientPairingRepository

    private lateinit var sut: OrphanDetector

    private val now = 1_700_000_000_000L
    private val ourClientId = "client-uuid"
    private val pairing = MasterPairing(masterInstallId = "master-uuid", clientId = ourClientId, masterSecretEnc = "ENC:x")
    private var pairedAt: Long = now - 60 * 60 * 1000L  // paired 1h ago by default — well past the race window
    private val clientIdFlow = MutableStateFlow(ourClientId)  // the pairing-change signal the init observer watches

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(pairingRepository.currentPairing()).thenReturn(pairing)
        whenever(preferences.get(LongNonKey.NsClientControlPairedAt)).thenAnswer { pairedAt }
        whenever(preferences.get(any<StringNonKey>())).thenAnswer { (it.arguments[0] as StringNonKey).defaultValue }
        whenever(preferences.observe(StringNonKey.NsClientControlClientId)).thenReturn(clientIdFlow)
        whenever(rh.gs(any<Int>())).thenReturn("orphan")
        sut = OrphanDetector(pairingRepository, preferences, notificationManager, rh, config, aapsLogger, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun configWithRoster(vararg clientIds: String) =
        NSRunningConfiguration(authorizedClients = NSAuthorizedClients(clientIds = clientIds.toList()))

    private fun configWithoutRosterField() = NSRunningConfiguration() // authorizedClients = null

    /**
     * Old master that doesn't publish the roster yet. Block absent on the wire is the
     * compatibility marker — clients must not infer orphan status from its absence.
     */
    @Test
    fun blockAbsentDoesNotFire() {
        sut.onSettingsDoc(configWithoutRosterField(), docSrvModified = now)
        verify(notificationManager, never()).post(any<NotificationId>(), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }

    /** We're in the roster — authorized. Dismiss any prior orphan notification (recovery path). */
    @Test
    fun rosterContainsUsDismissesOrphanNotification() {
        sut.onSettingsDoc(configWithRoster(ourClientId, "another-uuid"), docSrvModified = now)
        verify(notificationManager).dismiss(NotificationId.NSCLIENT_PAIRING_ORPHAN)
        verify(notificationManager, never()).post(any<NotificationId>(), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
    }

    /** Roster present, our clientId missing, doc well past the post-pairing race window → fire. */
    @Test
    fun rosterMissingUsOutsideRaceWindowFiresOrphanNotification() {
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        verify(notificationManager).post(eq(NotificationId.NSCLIENT_PAIRING_ORPHAN), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
    }

    /**
     * Just-paired race: master debounces 5s before republishing the roster. A settings/aaps
     * doc whose srvModified is older than our pairedAt (plus slack) represents the pre-pairing
     * snapshot in flight — must not false-fire.
     */
    @Test
    fun rosterMissingUsButDocPredatesPairingDoesNotFire() {
        pairedAt = now
        // Doc published 4s before we paired — well inside the 60s grace window.
        val docSrvModified = now - 4_000L
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = docSrvModified)
        verify(notificationManager, never()).post(any<NotificationId>(), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
    }

    /** Doc just outside the grace window — fire (master had time to republish but didn't include us). */
    @Test
    fun rosterMissingUsDocJustOutsideRaceWindowFires() {
        // We paired 2 minutes ago; this doc was just issued, well past the 60s post-pair grace.
        pairedAt = now - 2 * 60_000L
        val docSrvModified = now
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = docSrvModified)
        verify(notificationManager).post(eq(NotificationId.NSCLIENT_PAIRING_ORPHAN), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
    }

    /** Empty roster = master has zero authorized clients (typical post-reinstall). Treat as orphan. */
    @Test
    fun emptyRosterFires() {
        sut.onSettingsDoc(configWithRoster(), docSrvModified = now)
        verify(notificationManager).post(eq(NotificationId.NSCLIENT_PAIRING_ORPHAN), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
    }

    /** Master device must never alarm itself. */
    @Test
    fun masterRoleIsNoOp() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        verify(notificationManager, never()).post(any<NotificationId>(), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }

    /** Unpaired client has nothing to check — no signal either way. */
    @Test
    fun unpairedIsNoOp() {
        whenever(pairingRepository.currentPairing()).thenReturn(null)
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        verify(notificationManager, never()).post(any<NotificationId>(), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }

    /**
     * srvModified=0 means "we don't know when this doc was published". Skip the race guard
     * rather than firing prematurely — better a missed orphan signal than a false positive
     * during an indeterminate doc.
     */
    @Test
    fun missingSrvModifiedSkipsRaceGuardButStillFiresIfPairedAtIsZero() {
        pairedAt = 0L  // legacy install: never set pairedAt
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = 0L)
        verify(notificationManager).post(eq(NotificationId.NSCLIENT_PAIRING_ORPHAN), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())
    }

    // ---- authorized StateFlow (folded into NsClient.masterReachable to gate a revoked client's edits) ----

    /** Optimistic default before any settings doc is seen — first-ever pairing must stay usable. */
    @Test
    fun authorizedDefaultsTrue() {
        assertThat(sut.authorized.value).isTrue()
    }

    /** A confirmed orphan flips authorized to false so masterReachable disables editing. */
    @Test
    fun orphanFlipsAuthorizedFalse() {
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        assertThat(sut.authorized.value).isFalse()
    }

    /** Being re-listed after an orphan restores authorized to true (recovery path). */
    @Test
    fun reauthorizationRestoresAuthorizedTrue() {
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        assertThat(sut.authorized.value).isFalse()
        sut.onSettingsDoc(configWithRoster(ourClientId), docSrvModified = now)
        assertThat(sut.authorized.value).isTrue()
    }

    /** Block-absent (older master) must not flip authorized — the prior verdict is preserved. */
    @Test
    fun blockAbsentLeavesAuthorizedUnchanged() {
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)  // → false
        sut.onSettingsDoc(configWithoutRosterField(), docSrvModified = now)    // absent block → no change
        assertThat(sut.authorized.value).isFalse()
    }

    /** Within the post-pairing race window a missing roster must not flip authorized (optimistic). */
    @Test
    fun withinRaceWindowLeavesAuthorizedTrue() {
        pairedAt = now
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now - 4_000L)
        assertThat(sut.authorized.value).isTrue()
    }

    /** An unpaired client makes no authorization claim — authorized stays at its optimistic default. */
    @Test
    fun unpairedLeavesAuthorizedTrue() {
        whenever(pairingRepository.currentPairing()).thenReturn(null)
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        assertThat(sut.authorized.value).isTrue()
    }

    /** A pairing change (re-pair writes a new clientId) clears a prior orphan verdict, so masterReachable
     *  isn't left locked after re-pairing within the same process. */
    @Test
    fun rePairResetsAuthorizedTrue() {
        sut.onSettingsDoc(configWithRoster("stranger"), docSrvModified = now)
        assertThat(sut.authorized.value).isFalse()        // orphaned
        clientIdFlow.value = "new-client-uuid"            // user re-pairs → new NsClientControlClientId
        assertThat(sut.authorized.value).isTrue()         // optimistically authorized again
    }
}
