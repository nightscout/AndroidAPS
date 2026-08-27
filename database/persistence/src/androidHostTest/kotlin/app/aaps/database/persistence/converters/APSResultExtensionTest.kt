package app.aaps.database.persistence.converters

import app.aaps.core.interfaces.aps.APSResult
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import app.aaps.database.entities.APSResult as DbAPSResult

/**
 * Tests for [APSResultExtension].
 *
 * Only the [APSResult.Algorithm] <-> [DbAPSResult.Algorithm] enum mapper is covered here.
 * The [APSResult] data mapping (`fromDb(Provider)` / `toDb()`) is not a plain field copy: it
 * relies on kotlinx-serialization JSON round-tripping and a `Provider<APSResult>`, and the
 * domain [APSResult] is an interface with no trivial constructor, so it is out of scope for a
 * pure JVM unit test.
 *
 * The supported algorithms are AMA, SMB and AUTO_ISF. Both mappers deliberately `error()` on
 * UNKNOWN (the enum `else` branch), so UNKNOWN is excluded from the round-trip iteration and
 * asserted separately as a thrown exception.
 */
internal class APSResultExtensionTest {

    private val supportedDomain = APSResult.Algorithm.entries.filter { it != APSResult.Algorithm.UNKNOWN }
    private val supportedDb = DbAPSResult.Algorithm.entries.filter { it != DbAPSResult.Algorithm.UNKNOWN }

    @Test
    fun algorithmRoundTripFromDomain() {
        supportedDomain.forEach { algorithm ->
            assertThat(algorithm.toDb().fromDb()).isEqualTo(algorithm)
        }
    }

    @Test
    fun algorithmRoundTripFromDb() {
        supportedDb.forEach { algorithm ->
            assertThat(algorithm.fromDb().toDb()).isEqualTo(algorithm)
        }
    }

    @Test
    fun algorithmExplicitMapping() {
        assertThat(APSResult.Algorithm.AMA.toDb()).isEqualTo(DbAPSResult.Algorithm.AMA)
        assertThat(APSResult.Algorithm.SMB.toDb()).isEqualTo(DbAPSResult.Algorithm.SMB)
        assertThat(APSResult.Algorithm.AUTO_ISF.toDb()).isEqualTo(DbAPSResult.Algorithm.AUTO_ISF)

        assertThat(DbAPSResult.Algorithm.AMA.fromDb()).isEqualTo(APSResult.Algorithm.AMA)
        assertThat(DbAPSResult.Algorithm.SMB.fromDb()).isEqualTo(APSResult.Algorithm.SMB)
        assertThat(DbAPSResult.Algorithm.AUTO_ISF.fromDb()).isEqualTo(APSResult.Algorithm.AUTO_ISF)
    }

    @Test
    fun unknownAlgorithmThrows() {
        assertThrows<IllegalStateException> { APSResult.Algorithm.UNKNOWN.toDb() }
        assertThrows<IllegalStateException> { DbAPSResult.Algorithm.UNKNOWN.fromDb() }
    }
}
