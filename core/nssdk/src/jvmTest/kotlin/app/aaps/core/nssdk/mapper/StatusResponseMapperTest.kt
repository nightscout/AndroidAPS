package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.remotemodel.RemoteApiPermissions
import app.aaps.core.nssdk.remotemodel.RemoteStatusResponse
import app.aaps.core.nssdk.remotemodel.RemoteStorage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers [StatusResponseMapper]: RemoteStatusResponse.toLocal() (fields + nested storage/permissions). */
class StatusResponseMapperTest {

    @Test
    fun mapsStatusResponse() {
        val status = RemoteStatusResponse(
            version = "1.2.3",
            apiVersion = "3.0.0",
            srvDate = 1_700_000_000_000L,
            storage = RemoteStorage(storage = "openaps", version = "1"),
            apiPermissions = RemoteApiPermissions(
                deviceStatus = "crud",
                entries = "r",
                food = "cr",
                profile = "rud",
                settings = "",
                treatments = "crud"
            )
        ).toLocal()

        assertThat(status.version).isEqualTo("1.2.3")
        assertThat(status.apiVersion).isEqualTo("3.0.0")
        assertThat(status.srvDate).isEqualTo(1_700_000_000_000L)
        // nested permission mapping came through
        assertThat(status.apiPermissions.deviceStatus.create).isTrue()
        assertThat(status.apiPermissions.entries.read).isTrue()
        assertThat(status.apiPermissions.settings.read).isFalse()
    }
}
