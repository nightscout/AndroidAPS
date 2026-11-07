package app.aaps.plugins.source

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.identity.Libre3SessionKeys
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The two slots must share one file for the phone's identity and nothing else.
 *
 * Robolectric is needed because the store is a real `SharedPreferences` file. The store itself
 * lives in `:plugins:libre3`, which has no Robolectric, so the test lives in the source host tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Libre3SensorStoreNamespaceTest {

    private val application: Application get() = RuntimeEnvironment.getApplication()

    private fun identity(serial: String, mac: String) = Libre3SensorIdentity(
        serialNumber = serial,
        bleAddress = mac,
        blePin = byteArrayOf(1, 2, 3, 4),
        receiverId = 1234,
        generation = 0,
        warmupMinutes = 60,
        wearDurationMinutes = 14 * 24 * 60,
        activatedAtMs = 1_700_000_000_000L,
    )

    private fun keys(marker: Byte) = Libre3SessionKeys(
        phase5RawKey = ByteArray(16) { marker },
        kEnc = ByteArray(16) { (marker + 1).toByte() },
        ivEnc = ByteArray(8) { (marker + 2).toByte() },
    )

    @Test
    fun `the production namespace is the file every install already has`() {
        val store = Libre3SensorStore(application)

        assertThat(store.saveIdentityAndWait(identity("PROD-1", "AA:BB:CC:DD:EE:01"))).isTrue()

        val raw = application.getSharedPreferences("libre3_sensor_store", Context.MODE_PRIVATE)
        assertThat(raw.getString("serial", null)).isEqualTo("PROD-1")
        assertThat(raw.getString("ble_mac", null)).isEqualTo("AA:BB:CC:DD:EE:01")
    }

    /**
     * A pre-soak write never lands in the production file. `saveIdentityAndWait` drops the pairing key when the serial changes, and a
     * running Libre 3 refuses a fresh first pairing, so a production key lost to a pre-soak write
     * would mean a sensor that can never be reached again.
     */
    @Test
    fun `storing a pre-soak sensor does not touch the production keys`() {
        val production = Libre3SensorStore(application)
        production.saveIdentityAndWait(identity("PROD-1", "AA:BB:CC:DD:EE:01"))
        production.savePhase5RawKeyAndWait(ByteArray(16) { 7 })
        production.saveSessionKeys(ByteArray(16) { 8 }, ByteArray(8) { 9 })

        val staging = Libre3SensorStore(application, "staging")
        assertThat(staging.saveIdentityAndWait(identity("SOAK-2", "AA:BB:CC:DD:EE:02"))).isTrue()

        assertThat(production.loadIdentity()?.serialNumber).isEqualTo("PROD-1")
        val stillThere = production.loadSessionKeys()
        assertThat(stillThere.phase5RawKey).isEqualTo(ByteArray(16) { 7 })
        assertThat(stillThere.kEnc).isEqualTo(ByteArray(16) { 8 })
        assertThat(stillThere.ivEnc).isEqualTo(ByteArray(8) { 9 })
    }

    @Test
    fun `the two slots share one receiver id, because it is the phone's identity`() {
        val staging = Libre3SensorStore(application, "staging")
        val fromStaging = staging.receiverId()

        val production = Libre3SensorStore(application)
        assertThat(production.receiverId()).isEqualTo(fromStaging)

        val stagingFile = application.getSharedPreferences("libre3_sensor_store_staging", Context.MODE_PRIVATE)
        assertThat(stagingFile.contains("app_uuid")).isFalse()
    }

    @Test
    fun `adopt installs the taken over sensor with its keys`() {
        val production = Libre3SensorStore(application)
        production.saveIdentityAndWait(identity("PROD-1", "AA:BB:CC:DD:EE:01"))
        production.saveLastLifeCount(9_000)
        production.saveSensorChangeLoggedSerial("PROD-1")

        val staged = identity("SOAK-2", "AA:BB:CC:DD:EE:02")
        assertThat(production.adopt(staged, keys(marker = 5))).isTrue()

        assertThat(production.loadIdentity()).isEqualTo(staged)
        assertThat(production.loadSessionKeys()).isEqualTo(keys(marker = 5))
        // The old counter would refuse every reading of the new sensor, and the old mark would keep
        // the new sensor's start out of the database.
        assertThat(production.loadLastLifeCount()).isEqualTo(-1)
        assertThat(production.loadSensorChangeLoggedSerial()).isNull()
    }

    @Test
    fun `adopt keeps a missing key out instead of writing an empty one`() {
        val production = Libre3SensorStore(application)
        val staged = identity("SOAK-2", "AA:BB:CC:DD:EE:02")
        val halfKeys = Libre3SessionKeys(phase5RawKey = null, kEnc = ByteArray(16) { 3 }, ivEnc = ByteArray(8) { 4 })

        assertThat(production.adopt(staged, halfKeys)).isTrue()

        val stored = production.loadSessionKeys()
        assertThat(stored.phase5RawKey).isNull()
        assertThat(stored.kEnc).isEqualTo(ByteArray(16) { 3 })
        assertThat(stored.ivEnc).isEqualTo(ByteArray(8) { 4 })
    }

    /**
     * One `commit`. When that commit reports failure, the file is the file from before the call:
     * no new serial, and the old life counter and sensor-change mark are still there.
     */
    @Test
    fun `adopt that fails at commit writes nothing`() {
        val seeded = Libre3SensorStore(application)
        val before = identity("PROD-1", "AA:BB:CC:DD:EE:01")
        assertThat(seeded.saveIdentityAndWait(before)).isTrue()
        seeded.saveLastLifeCount(9_000)
        seeded.saveSensorChangeLoggedSerial("PROD-1")
        seeded.savePhase5RawKeyAndWait(ByteArray(16) { 7 })
        assertThat(seeded.loadSensorChangeLoggedSerial()).isEqualTo("PROD-1")

        val raw = application.getSharedPreferences("libre3_sensor_store", Context.MODE_PRIVATE)
        val snapshot = HashMap(raw.all)

        val refusing = Libre3SensorStore(commitRefusingContext(application))
        assertThat(refusing.adopt(identity("SOAK-2", "AA:BB:CC:DD:EE:02"), keys(marker = 5))).isFalse()

        assertThat(raw.all).isEqualTo(snapshot)
        val reread = Libre3SensorStore(application)
        assertThat(reread.loadIdentity()).isEqualTo(before)
        assertThat(reread.loadLastLifeCount()).isEqualTo(9_000)
        assertThat(reread.loadSensorChangeLoggedSerial()).isEqualTo("PROD-1")
        assertThat(reread.loadSessionKeys().phase5RawKey).isEqualTo(ByteArray(16) { 7 })
    }

    @Test
    fun `clearAll wipes only the pre-soak file`() {
        val production = Libre3SensorStore(application)
        production.saveIdentityAndWait(identity("PROD-1", "AA:BB:CC:DD:EE:01"))
        production.savePhase5RawKeyAndWait(ByteArray(16) { 7 })
        val receiverId = production.receiverId()

        val staging = Libre3SensorStore(application, "staging")
        staging.saveIdentityAndWait(identity("SOAK-2", "AA:BB:CC:DD:EE:02"))
        staging.saveSlotProgress(present = true, validReadingCount = 12)

        assertThat(staging.clearAll()).isTrue()

        assertThat(staging.loadIdentity()).isNull()
        assertThat(staging.loadSlotPresent()).isFalse()
        assertThat(staging.loadSlotValidReadingCount()).isEqualTo(0)
        assertThat(production.loadIdentity()?.serialNumber).isEqualTo("PROD-1")
        assertThat(production.loadSessionKeys().phase5RawKey).isEqualTo(ByteArray(16) { 7 })
        assertThat(production.receiverId()).isEqualTo(receiverId)
        // The receiver id never lived in the pre-soak file, so a wipe there cannot take it away.
        assertThat(staging.receiverId()).isEqualTo(receiverId)
    }

    @Test
    fun `the pre-soak slot keeps its progress across a restart`() {
        val staging = Libre3SensorStore(application, "staging")
        staging.saveSlotProgress(present = true, validReadingCount = 7)
        staging.saveSlotWarmupDone(true)
        staging.saveSlotActivatedAt(1_700_000_000_000L)

        val reopened = Libre3SensorStore(application, "staging")
        assertThat(reopened.loadSlotPresent()).isTrue()
        assertThat(reopened.loadSlotValidReadingCount()).isEqualTo(7)
        assertThat(reopened.loadSlotWarmupDone()).isTrue()
        assertThat(reopened.loadSlotActivatedAt()).isEqualTo(1_700_000_000_000L)
        // The production slot never set any of this.
        assertThat(Libre3SensorStore(application).loadSlotPresent()).isFalse()
    }

    /**
     * Reads go to the real file. `commit` returns false and does not touch that file, which is the
     * failure `SharedPreferences.Editor.commit` reports when the write did not land.
     */
    private fun commitRefusingContext(base: Context): Context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            CommitRefusingPreferences(base.getSharedPreferences(name, mode))
    }

    private class CommitRefusingPreferences(
        private val real: SharedPreferences,
    ) : SharedPreferences by real {

        override fun edit(): SharedPreferences.Editor = RefusingEditor()

        private class RefusingEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
            override fun commit(): Boolean = false
            override fun apply() = Unit
        }
    }
}
