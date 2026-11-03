package app.aaps.database.persistence

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.database.AppRepository
import app.aaps.database.transactions.InsertOrUpdateApsResultTransaction
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The master reports a loop result with a non-finite value. A client stores the same result when it comes
 * from Nightscout, and must not report it: the master already did, and it would send the master's data
 * from the phone of a follower.
 *
 * On the JVM because it needs a mocked `AppRepository`, which holds a real database.
 */
internal class ReportNonFiniteRtTest {

    private val fabricPrivacy = mock<FabricPrivacy>()

    /** The token is text, so it survives the way through Nightscout where a real NaN number cannot. */
    private val result = mock<APSResult>().also {
        whenever(it.algorithm).thenReturn(APSResult.Algorithm.SMB)
        whenever(it.date).thenReturn(1_000L)
        whenever(it.rawData()).thenReturn(
            RT(
                algorithm = APSResult.Algorithm.SMB,
                runningDynamicIsf = false,
                consoleError = mutableListOf("round(): non-finite value substituted with 0 (roundNonFinite_deviation=NaN)")
            )
        )
    }

    private suspend fun sut(isClient: Boolean): PersistenceLayerImpl {
        val repository = mock<AppRepository>()
        whenever(repository.runTransactionForResultSuspend<InsertOrUpdateApsResultTransaction.TransactionResult>(any()))
            .thenReturn(InsertOrUpdateApsResultTransaction.TransactionResult())
        val config = mock<Config>().also { whenever(it.AAPSCLIENT).thenReturn(isClient) }
        return PersistenceLayerImpl(mock<AAPSLogger>(), repository, mock<DateUtil>(), config, { result }, fabricPrivacy)
    }

    @Test
    fun masterReportsNonFiniteValue() = runTest {
        sut(isClient = false).insertOrUpdateApsResult(result)

        verify(fabricPrivacy, times(1)).logException(any())
    }

    @Test
    fun clientDoesNotReportWhatTheMasterCalculated() = runTest {
        sut(isClient = true).insertOrUpdateApsResult(result)

        verify(fabricPrivacy, never()).logException(any())
        verify(fabricPrivacy, never()).logMessage(any())
    }
}
