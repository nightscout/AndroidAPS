package app.aaps.plugins.eversense

import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.plugins.eversense.DriverHarness.Companion.ADDRESS
import app.aaps.plugins.eversense.DriverHarness.Companion.NAME
import app.aaps.plugins.eversense.DriverHarness.Companion.PASSWORD
import app.aaps.plugins.eversense.DriverHarness.Companion.USERNAME
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.util.StorageKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test

/** The login the driver keeps in its own store: what a sign-in, a sign-out, a pairing and an unpairing leave there. */
@OptIn(ExperimentalCoroutinesApi::class)
class EversenseCredentialsTest {

    private val harness = DriverHarness()
    private val store get() = harness.store

    private fun storeToken() {
        store.values[StorageKeys.ACCESS_TOKEN] = "token"
        store.values[StorageKeys.ACCESS_TOKEN_EXPIRY] = Long.MAX_VALUE
        store.values[StorageKeys.CLOUD_QUEUE] = "[]"
        store.values[StorageKeys.LOGIN_REFUSED] = true
    }

    @Test fun clearCredentialsRemovesTheLoginItsTokenAndTheShortcut(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, model = "E365_USA", shortcut = true)
        storeToken()
        driver.start()
        driver.clearCredentials()
        val saved = harness.secureState()
        assertThat(saved.username).isEmpty()
        assertThat(saved.password).isEmpty()
        assertThat(saved.model).isEmpty()
        assertThat(saved.canUseShortcut).isFalse()
        assertThat(driver.username).isEmpty()
        assertThat(driver.password).isEmpty()
        assertThat(store.contains(StorageKeys.ACCESS_TOKEN)).isFalse()
        assertThat(store.contains(StorageKeys.ACCESS_TOKEN_EXPIRY)).isFalse()
        assertThat(store.contains(StorageKeys.CLOUD_QUEUE)).isFalse()
        // Signing out also clears a refusal: the login it was about is gone
        assertThat(store.contains(StorageKeys.LOGIN_REFUSED)).isFalse()
        assertThat(harness.central.connects).isEmpty()
    }

    /** The region picks the server: a token or a registration of the other region is no good. */
    @Test fun aRegionChangeDropsTheTokenAndTheShortcutBeforeTheStart(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, shortcut = true)
        storeToken()
        driver.setCredentials(USERNAME, PASSWORD, europeanRegion = true)
        val saved = harness.secureState()
        assertThat(saved.isEuropeanRegion).isTrue()
        assertThat(saved.canUseShortcut).isFalse()
        assertThat(store.contains(StorageKeys.ACCESS_TOKEN)).isFalse()
        assertThat(store.contains(StorageKeys.ACCESS_TOKEN_EXPIRY)).isFalse()
        assertThat(store.contains(StorageKeys.LOGIN_REFUSED)).isFalse()
        assertThat(harness.central.connects).isEmpty()
    }

    /** The same login again changes nothing stored: the registration and the token stay. */
    @Test fun theSameLoginAgainKeepsTheRegistration(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, shortcut = true)
        store.values[StorageKeys.ACCESS_TOKEN] = "token"
        driver.start()
        val before = store.getString(StorageKeys.SECURE_STATE, null)
        driver.setCredentials(USERNAME, PASSWORD, europeanRegion = false)
        assertThat(store.getString(StorageKeys.SECURE_STATE, null)).isEqualTo(before)
        assertThat(store.getString(StorageKeys.ACCESS_TOKEN, null)).isEqualTo("token")
    }

    /** A new login with a paired transmitter connects with it. */
    @Test fun aNewLoginConnectsAPairedTransmitter(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E3")
        harness.storeTransmitter()
        harness.central.refuseConnects(status = 133)
        driver.start()
        runCurrent()
        harness.central.acceptConnects()
        driver.setCredentials(USERNAME, PASSWORD, europeanRegion = false)
        runCurrent()
        assertThat(driver.username).isEqualTo(USERNAME)
        assertThat(harness.central.connects).hasSize(2)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /** The login is the driver's own: a start reads it from the store, a stop forgets it in memory only. */
    @Test fun aStartReadsTheStoredLoginAndAStopForgetsItInMemory(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD)
        driver.start()
        assertThat(driver.username).isEqualTo(USERNAME)
        assertThat(driver.password).isEqualTo(PASSWORD)
        driver.stop()
        assertThat(driver.username).isEmpty()
        assertThat(driver.password).isEmpty()
        assertThat(harness.secureState().username).isEqualTo(USERNAME)
        driver.start()
        assertThat(driver.username).isEqualTo(USERNAME)
    }

    @Test fun aStartWithoutATransmitterDoesNotConnect(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD)
        driver.start()
        runCurrent()
        assertThat(harness.central.connects).isEmpty()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.PAUSED)
    }

    /** Pairing stores the transmitter with its name and makes the next connection a full registration. */
    @Test fun pairStoresTheTransmitterAndForcesRegistration(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, shortcut = true)
        // Not started: stored, but not connected
        assertThat(driver.pair(ADDRESS, NAME)).isFalse()
        assertThat(store.getString(StorageKeys.REMOTE_DEVICE_KEY, null)).isEqualTo(ADDRESS)
        assertThat(store.getString(StorageKeys.REMOTE_DEVICE_NAME, null)).isEqualTo(NAME)
        assertThat(harness.secureState().canUseShortcut).isFalse()
        assertThat(harness.central.connects).isEmpty()
    }

    /** Unpair forgets the transmitter and its registration; the login stays for a replacement. */
    @Test fun clearStoredDeviceForgetsTheTransmitterAndKeepsTheLogin(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, model = "E3", shortcut = true)
        harness.storeTransmitter()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        driver.clearStoredDevice()
        assertThat(store.contains(StorageKeys.REMOTE_DEVICE_KEY)).isFalse()
        assertThat(store.contains(StorageKeys.REMOTE_DEVICE_NAME)).isFalse()
        val saved = harness.secureState()
        assertThat(saved.canUseShortcut).isFalse()
        assertThat(saved.username).isEqualTo(USERNAME)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.PAUSED)
        assertThat(harness.central.links[0].isUp).isFalse()
    }

    /**
     * Unpair removes the phone's bond too, once the link is closed: a bond the phone keeps while the transmitter has
     * forgotten it can stop the next pairing on this phone.
     */
    @Test fun clearStoredDeviceRemovesTheBondAfterTheLinkIsClosed(): Unit = harness.run { driver ->
        val bonding = FakeBleBonding(harness.central)
        harness.central.bonding = bonding
        harness.storeLogin(USERNAME, PASSWORD, model = "E3", shortcut = true)
        harness.storeTransmitter()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)

        driver.clearStoredDevice()

        assertThat(bonding.removed).containsExactly(DeviceId(ADDRESS) to false)
        // Removed: nothing for the user to do
        assertThat(store.contains(StorageKeys.BOND_LEFT_NAME)).isFalse()
    }

    /** With no transmitter stored there is no bond to remove. */
    @Test fun clearStoredDeviceWithNoTransmitterRemovesNoBond(): Unit = harness.run { driver ->
        val bonding = FakeBleBonding(harness.central)
        harness.central.bonding = bonding
        harness.storeLogin(USERNAME, PASSWORD, model = "E3")

        driver.clearStoredDevice()

        assertThat(bonding.removed).isEmpty()
        assertThat(store.contains(StorageKeys.BOND_LEFT_NAME)).isFalse()
    }

    /** iOS: an app cannot remove the bond, so the transmitter is remembered for the hint until the next pairing. */
    @Test fun aBondThePhoneCannotRemoveIsRememberedUntilTheNextPairing(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, model = "E3")
        harness.storeTransmitter()

        driver.clearStoredDevice()
        assertThat(store.getString(StorageKeys.BOND_LEFT_NAME, null)).isEqualTo(NAME)

        driver.pair(ADDRESS, NAME)
        assertThat(store.contains(StorageKeys.BOND_LEFT_NAME)).isFalse()
    }

    /** An E3 used without a login: the stored login and its cloud queue go, and the paired transmitter connects. */
    @Test fun useWithoutLoginForgetsTheLoginAndConnectsAnE3(): Unit = harness.run { driver ->
        harness.storeLogin(USERNAME, PASSWORD, model = "E365_USA")
        harness.storeTransmitter()
        storeToken()
        harness.central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3() } }
        driver.start()
        runCurrent()
        driver.useWithoutLogin()
        runCurrent()
        val saved = harness.secureState()
        assertThat(saved.username).isEmpty()
        assertThat(saved.model).isEqualTo("E3")
        assertThat(store.contains(StorageKeys.CLOUD_QUEUE)).isFalse()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    // ----- failed sign-ins of a registered 365 -----

    /** A registered 365 whose AuthStart answer cannot be read: every sign-in fails on the shortcut. */
    private fun aTransmitterThatFailsTheShortcut() {
        harness.registered365()
        harness.central.newLink = { device ->
            FakeBleLink(device, is365 = true).also { link ->
                link.onWrite = { send(byteArrayOf(1, 1, 1, Eversense365Packets.AuthenticateResponseId, Eversense365Packets.AuthenticateStart)) }
            }
        }
    }

    /** Offline a full sign-in cannot run: failed shortcuts must not throw away the registration the phone has. */
    @Test fun repeatedOfflineShortcutFailuresKeepTheRegisteredIdentity(): Unit = harness.run { driver ->
        aTransmitterThatFailsTheShortcut()
        harness.online = false
        val registered = store.getString(StorageKeys.SECURE_STATE, null)
        driver.start()
        runCurrent()
        advance(400_000)
        assertThat(harness.central.connects.size).isAtLeast(10)
        assertThat(store.getString(StorageKeys.SECURE_STATE, null)).isEqualTo(registered)
        assertThat(harness.secureState().canUseShortcut).isTrue()
    }

    /** Online, three failed shortcuts in a row make the next sign-in a full one. */
    @Test fun threeShortcutFailuresOnlineAskForAFullSignIn(): Unit = harness.run { driver ->
        aTransmitterThatFailsTheShortcut()
        driver.start()
        runCurrent()
        advance(5_000)
        assertThat(harness.central.connects).hasSize(2)
        assertThat(harness.secureState().canUseShortcut).isTrue()
        advance(10_000)
        assertThat(harness.central.connects).hasSize(3)
        assertThat(harness.secureState().canUseShortcut).isFalse()
    }
}
