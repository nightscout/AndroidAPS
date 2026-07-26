package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import javax.inject.Provider

internal class PairingOfferPublisherTest {

    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: PairingOfferPublisher

    private val deleteOk = CreateUpdateResponse(response = 200, identifier = null, isDeduplication = false, deduplicatedIdentifier = null, lastModified = null, errorResponse = null)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        sut = PairingOfferPublisher(Provider { nsClientV3Plugin }, nsClientRepository, aapsLogger)
    }

    /**
     * The offer doc holds the AES-GCM-wrapped pairing secret, so cleanup must be a PERMANENT
     * delete — a soft delete would leave the wrapped payload in a tombstone and keep the PIN
     * brute-force window open. Guards against a future revert to the plain (soft) delete.
     */
    @Test
    fun deleteOfferUsesPermanentDeleteNotSoftDelete() = runTest {
        val clientId = "abc-123"
        val identifier = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}$clientId"
        whenever(nsAndroidClient.deleteSettingsPermanent(identifier)).thenReturn(deleteOk)

        sut.deleteOffer(clientId)

        verify(nsAndroidClient).deleteSettingsPermanent(identifier)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    /** Documented non-throwing contract: a delete IOException is logged, never propagated. */
    @Test
    fun deleteOfferSwallowsIoExceptionAndLogs() = runTest {
        val clientId = "abc-123"
        val identifier = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}$clientId"
        whenever(nsAndroidClient.deleteSettingsPermanent(identifier)).thenAnswer { throw IOException("boom") }

        sut.deleteOffer(clientId) // must not throw

        verify(aapsLogger).error(eq(LTag.NSCLIENT), argThat<String> { contains("delete failed") })
    }
}
