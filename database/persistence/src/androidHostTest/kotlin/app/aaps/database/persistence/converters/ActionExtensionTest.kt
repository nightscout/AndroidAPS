package app.aaps.database.persistence.converters

import app.aaps.core.data.ue.Action
import app.aaps.database.entities.UserEntry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ActionExtensionTest {

    @Test
    fun allDomainValuesRoundTripThroughDb() {
        // Domain -> Entity -> Domain must be stable for every value
        Action.entries.forEach { action ->
            assertThat(action.toDb().fromDb()).isEqualTo(action)
        }
    }

    @Test
    fun allEntityValuesRoundTripThroughDomain() {
        // Entity -> Domain -> Entity must be stable for every value
        UserEntry.Action.entries.forEach { action ->
            assertThat(action.fromDb().toDb()).isEqualTo(action)
        }
    }

    @Test
    fun bothEnumsHaveTheSameNumberOfValues() {
        // Guards against a value being added to one enum but not mapped in the converter
        assertThat(Action.entries.size).isEqualTo(UserEntry.Action.entries.size)
    }

    @Test
    fun mappingPreservesEnumName() {
        // The mapping is name-identity in both directions
        Action.entries.forEach { action ->
            assertThat(action.toDb().name).isEqualTo(action.name)
        }
        UserEntry.Action.entries.forEach { action ->
            assertThat(action.fromDb().name).isEqualTo(action.name)
        }
    }

    @Test
    fun representativeValuesMapExplicitly() {
        assertThat(Action.BOLUS.toDb()).isEqualTo(UserEntry.Action.BOLUS)
        assertThat(Action.UNKNOWN.toDb()).isEqualTo(UserEntry.Action.UNKNOWN)
        assertThat(Action.REMOTE_CONFIG_CHANGED.toDb()).isEqualTo(UserEntry.Action.REMOTE_CONFIG_CHANGED)

        assertThat(UserEntry.Action.BOLUS.fromDb()).isEqualTo(Action.BOLUS)
        assertThat(UserEntry.Action.UNKNOWN.fromDb()).isEqualTo(Action.UNKNOWN)
        assertThat(UserEntry.Action.SCENE_ACTIVATED.fromDb()).isEqualTo(Action.SCENE_ACTIVATED)
    }
}
