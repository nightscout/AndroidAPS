package app.aaps.database.persistence.converters

import app.aaps.core.data.ue.Action
import app.aaps.database.entities.UserEntry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class ActionExtensionTest {

    @Test
    fun allDomainValuesRoundTripThroughDb() {
        // Domain -> Entity -> Domain must be stable for every value
        Action.entries.forEach { action ->
            assertEquals(action, action.toDb().fromDb())
        }
    }

    @Test
    fun allEntityValuesRoundTripThroughDomain() {
        // Entity -> Domain -> Entity must be stable for every value
        UserEntry.Action.entries.forEach { action ->
            assertEquals(action, action.fromDb().toDb())
        }
    }

    @Test
    fun bothEnumsHaveTheSameNumberOfValues() {
        // Guards against a value being added to one enum but not mapped in the converter
        assertEquals(UserEntry.Action.entries.size, Action.entries.size)
    }

    @Test
    fun mappingPreservesEnumName() {
        // The mapping is name-identity in both directions
        Action.entries.forEach { action ->
            assertEquals(action.name, action.toDb().name)
        }
        UserEntry.Action.entries.forEach { action ->
            assertEquals(action.name, action.fromDb().name)
        }
    }

    @Test
    fun representativeValuesMapExplicitly() {
        assertEquals(UserEntry.Action.BOLUS, Action.BOLUS.toDb())
        assertEquals(UserEntry.Action.UNKNOWN, Action.UNKNOWN.toDb())
        assertEquals(UserEntry.Action.REMOTE_CONFIG_CHANGED, Action.REMOTE_CONFIG_CHANGED.toDb())

        assertEquals(Action.BOLUS, UserEntry.Action.BOLUS.fromDb())
        assertEquals(Action.UNKNOWN, UserEntry.Action.UNKNOWN.fromDb())
        assertEquals(Action.SCENE_ACTIVATED, UserEntry.Action.SCENE_ACTIVATED.fromDb())
    }
}
