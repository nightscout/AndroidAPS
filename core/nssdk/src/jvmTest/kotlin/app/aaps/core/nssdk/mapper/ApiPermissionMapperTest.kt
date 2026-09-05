package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.remotemodel.RemoteApiPermissions
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers [ApiPermissionMapper]: the c/r/u/d permission-string flags and RemoteApiPermissions.toLocal(). */
class ApiPermissionMapperTest {

    @Test
    fun mapsPermissionFlagsFromStrings() {
        val local = RemoteApiPermissions(
            deviceStatus = "crud",
            entries = "r",
            food = "",
            profile = "cr",
            settings = "rud",
            treatments = "crud"
        ).toLocal()

        // full crud
        assertThat(local.deviceStatus.create).isTrue()
        assertThat(local.deviceStatus.read).isTrue()
        assertThat(local.deviceStatus.update).isTrue()
        assertThat(local.deviceStatus.delete).isTrue()

        // read-only
        assertThat(local.entries.read).isTrue()
        assertThat(local.entries.create).isFalse()
        assertThat(local.entries.update).isFalse()
        assertThat(local.entries.delete).isFalse()

        // no permission
        assertThat(local.food.read).isFalse()
        assertThat(local.food.create).isFalse()

        // partial
        assertThat(local.profile.create).isTrue()
        assertThat(local.profile.read).isTrue()
        assertThat(local.profile.update).isFalse()

        assertThat(local.settings.read).isTrue()
        assertThat(local.settings.update).isTrue()
        assertThat(local.settings.delete).isTrue()
        assertThat(local.settings.create).isFalse()
    }
}
