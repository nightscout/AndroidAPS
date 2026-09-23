package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoConnectionCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var bleSession: CarelevoBleSession

    private lateinit var sut: CarelevoConnectionCoordinator

    private fun patchInfo(
        mode: Int? = null,
        runningMinutes: Int? = null,
        pumpState: Int? = null
    ): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "AA:BB:CC:DD:EE:FF",
            mode = mode,
            runningMinutes = runningMinutes,
            pumpState = pumpState
        )

    private fun stubPatchInfo(subject: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>>) {
        whenever(carelevoPatch.patchInfo).thenReturn(subject)
    }

    @BeforeEach
    fun setUp() {
        sut = CarelevoConnectionCoordinator(aapsLogger, carelevoPatch, bleSession)
    }

    // ---- isInitialized ----

    @Test
    fun `isInitialized false when the patch subject has no value yet`() {
        // BehaviorSubject with no default → patchInfo.value is null → early return false.
        stubPatchInfo(BehaviorSubject.create<Optional<CarelevoPatchInfoDomainModel>>())
        assertThat(sut.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized false when the patch info is empty`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.empty<CarelevoPatchInfoDomainModel>()))
        assertThat(sut.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized false when mode, runningMinutes and pumpState are all null`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.of(patchInfo())))
        assertThat(sut.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized true when mode is set`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.of(patchInfo(mode = 1))))
        assertThat(sut.isInitialized()).isTrue()
    }

    @Test
    fun `isInitialized true when only runningMinutes is set`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.of(patchInfo(runningMinutes = 100))))
        assertThat(sut.isInitialized()).isTrue()
    }

    @Test
    fun `isInitialized true when only pumpState is set`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.of(patchInfo(pumpState = 0))))
        assertThat(sut.isInitialized()).isTrue()
    }

    // ---- isConnected / isConnecting ----

    @Test
    fun `isConnected is true pre-activation even when the link is down`() {
        // Not initialized (empty patch info) → forced true so the queue never dials a missing device.
        stubPatchInfo(BehaviorSubject.createDefault(Optional.empty<CarelevoPatchInfoDomainModel>()))
        whenever(bleSession.connected).thenReturn(MutableStateFlow(false))
        assertThat(sut.isConnected()).isTrue()
    }

    @Test
    fun `isConnected reflects a down held link once activated`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.of(patchInfo(mode = 1))))
        whenever(bleSession.connected).thenReturn(MutableStateFlow(false))
        assertThat(sut.isConnected()).isFalse()
    }

    @Test
    fun `isConnected reflects an up held link once activated`() {
        stubPatchInfo(BehaviorSubject.createDefault(Optional.of(patchInfo(mode = 1))))
        whenever(bleSession.connected).thenReturn(MutableStateFlow(true))
        assertThat(sut.isConnected()).isTrue()
    }

    @Test
    fun `isConnecting delegates to the session`() {
        whenever(bleSession.isConnecting).thenReturn(MutableStateFlow(true))
        assertThat(sut.isConnecting()).isTrue()
    }

    // ---- connect / disconnect / stopConnecting drive the held link ----

    @Test
    fun `connect fires one attempt against the stored patch MAC`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn("AA:BB:CC:DD:EE:FF")
        sut.connect("bootstrap")
        verify(bleSession).requestConnect("AA:BB:CC:DD:EE:FF", "bootstrap")
    }

    @Test
    fun `connect no-ops when no patch address is known`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        sut.connect("bootstrap")
        verify(bleSession, never()).requestConnect(any(), any())
    }

    @Test
    fun `disconnect closes the held link`() {
        sut.disconnect("shutdown")
        verify(bleSession).requestDisconnect("shutdown")
    }

    @Test
    fun `stopConnecting closes the held link`() {
        sut.stopConnecting()
        verify(bleSession).requestDisconnect("stopConnecting")
    }
}
