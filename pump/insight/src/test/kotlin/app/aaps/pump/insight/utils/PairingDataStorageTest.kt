package app.aaps.pump.insight.utils

import android.content.Context
import android.content.SharedPreferences
import app.aaps.pump.insight.descriptors.FirmwareVersions
import app.aaps.pump.insight.descriptors.SystemIdentification
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers [PairingDataStorage]: the pairing data the driver keeps between app runs.
 *
 * Everything here is written on the way in and read back once, in the constructor. If a value does
 * not survive that trip the pump does not report an error - the app simply comes back up believing
 * it is paired with different keys than the pump holds, and the connection fails in a way that
 * looks like a radio problem. So each test writes through one instance and reads through a second
 * one built over the same store, which is what an app restart does.
 */
class PairingDataStorageTest {

    /** A map-backed stand-in for SharedPreferences: the real one needs a device. */
    private class FakePreferences : SharedPreferences {

        val values = mutableMapOf<String, Any?>()

        private inner class FakeEditor : SharedPreferences.Editor {

            private val pending = mutableMapOf<String, Any?>()
            private var clearFirst = false

            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, value: MutableSet<String>?) = apply { pending[key] = value }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { clearFirst = true }
            override fun commit(): Boolean { apply(); return true }

            override fun apply() {
                if (clearFirst) values.clear()
                // A null value means "not stored", which is how SharedPreferences behaves too.
                pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
                pending.clear()
                clearFirst = false
            }
        }

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String) = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private val store = FakePreferences()

    /** A new storage over the same preferences - what the driver gets after the app restarts. */
    private fun reload(): PairingDataStorage {
        val context = mock<Context>()
        whenever(context.packageName).thenReturn("app.aaps.pump.insight")
        whenever(context.getSharedPreferences(any<String>(), any<Int>())).thenReturn(store)
        return PairingDataStorage(context)
    }

    private fun firmware() = FirmwareVersions().also {
        it.releaseSWVersion = "3.0"
        it.uiProcSWVersion = "ui-1"
        it.pcProcSWVersion = "pc-2"
        it.mdTelProcSWVersion = "tel-3"
        it.btInfoPageVersion = "bt-4"
        it.safetyProcSWVersion = "safe-5"
        it.configIndex = 11
        it.historyIndex = 22
        it.stateIndex = 33
        it.vocabularyIndex = 44
    }

    private fun identification() = SystemIdentification().also {
        it.serialNumber = "SN-12345"
        it.manufacturingDate = "2024-01-31"
        it.systemIdAppendix = 987654321L
    }

    @Test
    fun anEmptyStoreLoadsAsNotPaired() {
        val sut = reload()

        assertThat(sut.paired).isFalse()
        assertThat(sut.macAddress).isNull()
        assertThat(sut.commId).isEqualTo(0)
        assertThat(sut.lastNonceSent).isNull()
        assertThat(sut.lastNonceReceived).isNull()
        assertThat(sut.firmwareVersions).isNull()
        assertThat(sut.systemIdentification).isNull()
    }

    @Test
    fun theKeysAndTheMacSurviveARestart() {
        val incoming = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val outgoing = byteArrayOf(0x55, 0x66, 0x77, 0x2A.toByte())
        reload().also {
            it.paired = true
            it.macAddress = "AA:BB:CC:DD:EE:FF"
            it.commId = 1234567890L
            it.incomingKey = incoming
            it.outgoingKey = outgoing
        }

        val restarted = reload()

        assertThat(restarted.paired).isTrue()
        assertThat(restarted.macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(restarted.commId).isEqualTo(1234567890L)
        assertThat(restarted.incomingKey).isEqualTo(incoming)
        assertThat(restarted.outgoingKey).isEqualTo(outgoing)
    }

    @Test
    fun theNoncesSurviveARestart() {
        val sent = Nonce(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
        val received = Nonce(byteArrayOf(0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10))
        reload().also {
            it.lastNonceSent = sent
            it.lastNonceReceived = received
        }

        val restarted = reload()

        assertThat(restarted.lastNonceSent?.storageValue).isEqualTo(sent.storageValue)
        assertThat(restarted.lastNonceReceived?.storageValue).isEqualTo(received.storageValue)
    }

    @Test
    fun theFirmwareVersionsSurviveARestart() {
        reload().firmwareVersions = firmware()

        val restarted = reload().firmwareVersions

        assertThat(restarted).isNotNull()
        assertThat(restarted?.releaseSWVersion).isEqualTo("3.0")
        assertThat(restarted?.uiProcSWVersion).isEqualTo("ui-1")
        assertThat(restarted?.pcProcSWVersion).isEqualTo("pc-2")
        assertThat(restarted?.mdTelProcSWVersion).isEqualTo("tel-3")
        assertThat(restarted?.btInfoPageVersion).isEqualTo("bt-4")
        assertThat(restarted?.safetyProcSWVersion).isEqualTo("safe-5")
        assertThat(restarted?.configIndex).isEqualTo(11)
        assertThat(restarted?.historyIndex).isEqualTo(22)
        assertThat(restarted?.stateIndex).isEqualTo(33)
        assertThat(restarted?.vocabularyIndex).isEqualTo(44)
    }

    @Test
    fun theSystemIdentificationSurvivesARestart() {
        reload().systemIdentification = identification()

        val restarted = reload().systemIdentification

        assertThat(restarted).isNotNull()
        assertThat(restarted?.serialNumber).isEqualTo("SN-12345")
        assertThat(restarted?.manufacturingDate).isEqualTo("2024-01-31")
        assertThat(restarted?.systemIdAppendix).isEqualTo(987654321L)
    }

    /** Unpairing must leave nothing behind: stale keys would be used against a new pump. */
    @Test
    fun resetLeavesNothingBehindAfterARestart() {
        reload().also {
            it.paired = true
            it.macAddress = "AA:BB:CC:DD:EE:FF"
            it.commId = 42L
            it.incomingKey = byteArrayOf(1, 2, 3)
            it.outgoingKey = byteArrayOf(4, 5, 6)
            it.lastNonceSent = Nonce(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
            it.lastNonceReceived = Nonce(byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01))
            it.firmwareVersions = firmware()
            it.systemIdentification = identification()
        }

        reload().reset()
        val restarted = reload()

        assertThat(restarted.paired).isFalse()
        assertThat(restarted.macAddress).isNull()
        assertThat(restarted.commId).isEqualTo(0)
        assertThat(restarted.incomingKey).isNull()
        assertThat(restarted.outgoingKey).isNull()
        assertThat(restarted.lastNonceSent).isNull()
        assertThat(restarted.lastNonceReceived).isNull()
        assertThat(restarted.firmwareVersions).isNull()
        assertThat(restarted.systemIdentification).isNull()
    }
}
