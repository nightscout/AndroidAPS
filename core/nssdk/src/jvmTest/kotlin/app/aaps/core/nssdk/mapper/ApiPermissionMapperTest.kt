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

    /**
     * The `aaps-client` role: read everywhere, create and update on settings (`v3/status` itself shows only the reads;
     * `NSAndroidClientImpl.getStatus` asks for the settings rights). Enough for a client, not for a master.
     */
    @Test
    fun aClientTokenIsEnoughForAClientOnly() {
        val client = RemoteApiPermissions(deviceStatus = "r", entries = "r", food = "r", profile = "r", settings = "cru", treatments = "r").toLocal()

        assertThat(client.isClient()).isTrue()
        assertThat(client.isFull()).isFalse()
    }

    /** The built-in `readable` role cannot write to settings, so a client could not reach its master with it. */
    @Test
    fun aReadOnlyTokenIsNotEnoughForAClient() {
        val readable = RemoteApiPermissions(deviceStatus = "r", entries = "r", food = "r", profile = "r", settings = "r", treatments = "r").toLocal()

        assertThat(readable.isClient()).isFalse()
    }
}
