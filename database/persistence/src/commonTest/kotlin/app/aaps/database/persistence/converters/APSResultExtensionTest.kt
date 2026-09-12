package app.aaps.database.persistence.converters

import app.aaps.core.interfaces.aps.APSResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith
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
            assertEquals(algorithm, algorithm.toDb().fromDb())
        }
    }

    @Test
    fun algorithmRoundTripFromDb() {
        supportedDb.forEach { algorithm ->
            assertEquals(algorithm, algorithm.fromDb().toDb())
        }
    }

    @Test
    fun algorithmExplicitMapping() {
        assertEquals(DbAPSResult.Algorithm.AMA, APSResult.Algorithm.AMA.toDb())
        assertEquals(DbAPSResult.Algorithm.SMB, APSResult.Algorithm.SMB.toDb())
        assertEquals(DbAPSResult.Algorithm.AUTO_ISF, APSResult.Algorithm.AUTO_ISF.toDb())

        assertEquals(APSResult.Algorithm.AMA, DbAPSResult.Algorithm.AMA.fromDb())
        assertEquals(APSResult.Algorithm.SMB, DbAPSResult.Algorithm.SMB.fromDb())
        assertEquals(APSResult.Algorithm.AUTO_ISF, DbAPSResult.Algorithm.AUTO_ISF.fromDb())
    }

    @Test
    fun unknownAlgorithmThrows() {
        assertFailsWith<IllegalStateException> { APSResult.Algorithm.UNKNOWN.toDb() }
        assertFailsWith<IllegalStateException> { DbAPSResult.Algorithm.UNKNOWN.fromDb() }
    }
}
