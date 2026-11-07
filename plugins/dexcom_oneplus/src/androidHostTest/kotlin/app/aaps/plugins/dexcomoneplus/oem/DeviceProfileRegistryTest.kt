package app.aaps.plugins.dexcomoneplus.oem

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeviceProfileRegistryTest {

    @Test
    fun pixel_doesNotRequestMtuBeforeDiscover() {
        // Juggluco Dex path never requestMtu before discover; Pixel previously did and
        // could race CCCD/auth on fragile stacks.
        assertThat(DeviceProfileRegistry.PixelDefault.requestMtuOnConnect).isFalse()
        assertThat(DeviceProfileRegistry.SamsungDefault.requestMtuOnConnect).isFalse()
        assertThat(DeviceProfileRegistry.GenericFallback.requestMtuOnConnect).isFalse()
    }

    @Test
    fun samsung_requiresAdvOnFirstAttemptAndAutoConnectFromStart() {
        val s = DeviceProfileRegistry.SamsungDefault
        assertThat(s.autoConnectFromAttempt).isEqualTo(0)
        // Hard ADV gate only on attempt 0 (driver); flag still true for Samsung.
        assertThat(s.requireAdvBeforeConnect).isTrue()
        assertThat(s.preConnectScanMs).isAtLeast(8_000L)
    }

    @Test
    fun motorola_getsItsOwnProfileAndNotTheFallback() {
        val resolved = DeviceProfileRegistry.resolve(manufacturer = "motorola", model = "moto g84 5G")
        assertThat(resolved.id).isEqualTo(OemProfileId.MOTOROLA)
        assertThat(resolved).isEqualTo(DeviceProfileRegistry.MotorolaDefault)
    }

    @Test
    fun motorola_takesTheSamsungLessonOnBlindConnects() {
        // A blind hard connect without a fresh ADV is what cost minutes on this stack, exactly as it
        // did on Samsung. Wait for the advertisement, and let the platform hold the link from the
        // first attempt.
        val m = DeviceProfileRegistry.MotorolaDefault
        assertThat(m.requireAdvBeforeConnect).isTrue()
        assertThat(m.autoConnectFromAttempt).isEqualTo(0)
        assertThat(m.preConnectScanMs).isAtLeast(8_000L)
        assertThat(m.requestMtuOnConnect).isFalse()
    }

    @Test
    fun motorola_isNotMistakenForPixelOrSamsung() {
        assertThat(DeviceProfileRegistry.resolve(manufacturer = "Google", model = "Pixel 8").id)
            .isEqualTo(OemProfileId.PIXEL)
        assertThat(DeviceProfileRegistry.resolve(manufacturer = "samsung", model = "SM-S911B").id)
            .isEqualTo(OemProfileId.SAMSUNG)
        assertThat(DeviceProfileRegistry.resolve(manufacturer = "Xiaomi", model = "whatever").id)
            .isEqualTo(OemProfileId.GENERIC_FALLBACK)
    }

    @Test
    fun everyProfileIsReachableById() {
        OemProfileId.entries.forEach { id ->
            assertThat(DeviceProfileRegistry.byId(id).id).isEqualTo(id)
        }
    }

    @Test
    fun genericFallback_isTheSafestProfileNotTheBoldest() {
        // It is where every phone that is not a Pixel or a Samsung lands, so a blind connect must not
        // be its default. Two field logs — a Motorola and a CUBOT KING KONG MINI 3 — reached it.
        val g = DeviceProfileRegistry.GenericFallback
        assertThat(g.requireAdvBeforeConnect).isTrue()
        assertThat(g.autoConnectFromAttempt).isEqualTo(0)
        assertThat(g.preConnectScanMs).isAtLeast(8_000L)
    }

    @Test
    fun everyProfileWaitsForTheAdvertisementExceptPixel() {
        // Pixel is the one stack with field evidence that a direct connect works without it.
        assertThat(DeviceProfileRegistry.SamsungDefault.requireAdvBeforeConnect).isTrue()
        assertThat(DeviceProfileRegistry.MotorolaDefault.requireAdvBeforeConnect).isTrue()
        assertThat(DeviceProfileRegistry.GenericFallback.requireAdvBeforeConnect).isTrue()
    }

    @Test
    fun unknownOemFallsBackAndKeepsTheGuard() {
        val resolved = DeviceProfileRegistry.resolve(manufacturer = "CUBOT", model = "KINGKONG MINI 3")
        assertThat(resolved.id).isEqualTo(OemProfileId.GENERIC_FALLBACK)
        assertThat(resolved.requireAdvBeforeConnect).isTrue()
    }
}
