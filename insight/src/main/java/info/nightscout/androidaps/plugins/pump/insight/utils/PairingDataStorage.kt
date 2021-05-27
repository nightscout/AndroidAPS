package info.nightscout.androidaps.plugins.pump.insight.utils

import android.content.Context
import android.content.SharedPreferences
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SystemIdentification
import org.spongycastle.util.encoders.Hex

class PairingDataStorage(context: Context) {

    internal val preferences: SharedPreferences

    internal var paired : Boolean = false
        get() { return field }
        set(paired) {
            field = paired
            preferences.edit().putBoolean("paired", paired).apply()
        }

    internal var macAddress : String? = null
        get() { return field }
        set(macAddress) {
            field = macAddress
            preferences.edit().putString("macAddress", macAddress).apply()
        }

    internal var lastNonceSent : Nonce? = null
        get() { return field }
        set(lastNonceSent) {
            field = lastNonceSent
            preferences.edit().putString("lastNonceSent", if (lastNonceSent == null) null else Hex.toHexString(lastNonceSent.storageValue)).apply()
        }

    internal var lastNonceReceived : Nonce? = null
        get() { return field }
        set(lastNonceReceived) {
            field = lastNonceReceived
            preferences.edit().putString("lastNonceReceived", if (lastNonceReceived == null) null else Hex.toHexString(lastNonceReceived.storageValue)).apply()
        }

    internal var commId : Long = 0
        get() { return field }
        set(commId) {
            field = commId
            preferences.edit().putLong("commId", commId).apply()
        }

    internal var incomingKey : ByteArray? = null
        get() { return field }
        set(incomingKey) {
            field = incomingKey
            preferences.edit().putString("incomingKey", if (incomingKey == null) null else Hex.toHexString(incomingKey)).apply()
        }

    internal var outgoingKey : ByteArray? = null
        get() { return field }
        set(outgoingKey) {
            field = outgoingKey
            preferences.edit().putString("outgoingKey", if (outgoingKey == null) null else Hex.toHexString(outgoingKey)).apply()
        }

    internal var firmwareVersions : FirmwareVersions? = null
        get() { return field }
        set(firmwareVersions) {
            field = firmwareVersions
            if (firmwareVersions == null) {
                preferences.edit()
                    .putString("releaseSWVersion", null)
                    .putString("uiProcSWVersion", null)
                    .putString("pcProcSWVersion", null)
                    .putString("mdTelProcSWVersion", null)
                    .putString("btInfoPageVersion", null)
                    .putString("safetyProcSWVersion", null)
                    .putInt("configIndex", 0)
                    .putInt("historyIndex", 0)
                    .putInt("stateIndex", 0)
                    .putInt("vocabularyIndex", 0)
                    .apply()
            } else {
                preferences.edit()
                    .putString("releaseSWVersion", firmwareVersions.releaseSWVersion)
                    .putString("uiProcSWVersion", firmwareVersions.uiProcSWVersion)
                    .putString("pcProcSWVersion", firmwareVersions.pcProcSWVersion)
                    .putString("mdTelProcSWVersion", firmwareVersions.mdTelProcSWVersion)
                    .putString("btInfoPageVersion", firmwareVersions.btInfoPageVersion)
                    .putString("safetyProcSWVersion", firmwareVersions.safetyProcSWVersion)
                    .putInt("configIndex", firmwareVersions.configIndex)
                    .putInt("historyIndex", firmwareVersions.historyIndex)
                    .putInt("stateIndex", firmwareVersions.stateIndex)
                    .putInt("vocabularyIndex", firmwareVersions.vocabularyIndex)
                    .apply()
            }
        }

    internal var systemIdentification : SystemIdentification? = null
        get() { return field }
        set(systemIdentification) {
            field = systemIdentification
            if (systemIdentification == null) {
                preferences.edit()
                    .putString("pumpSerial", null)
                    .putString("manufacturingDate", null)
                    .putLong("systemIdAppendix", 0)
                    .apply()
            } else {
                preferences.edit()
                    .putString("pumpSerial", systemIdentification.serialNumber)
                    .putString("manufacturingDate", systemIdentification.manufacturingDate)
                    .putLong("systemIdAppendix", systemIdentification.systemIdAppendix)
                    .apply()
            }
        }

    fun reset() {
        paired = false
        macAddress = null
        commId = 0
        incomingKey = null
        outgoingKey = null
        lastNonceReceived = null
        lastNonceSent = null
        firmwareVersions = null
        systemIdentification = null
        macAddress = null
    }

    init {
        preferences = context.getSharedPreferences(context.packageName + ".PAIRING_DATA_STORAGE", Context.MODE_PRIVATE)
        paired = preferences.getBoolean("paired", false)
        macAddress = preferences.getString("macAddress", null)
        val lastNonceSentHex = preferences.getString("lastNonceSent", null)
        if (lastNonceSentHex != null) lastNonceSent = Nonce(Hex.decode(lastNonceSentHex))
        val lastNonceReceivedHex = preferences.getString("lastNonceReceived", null)
        if (lastNonceReceivedHex != null) lastNonceReceived = Nonce(Hex.decode(lastNonceReceivedHex))
        commId = preferences.getLong("commId", 0)
        val incomingKeyHex = preferences.getString("incomingKey", null)
        incomingKey = if (incomingKeyHex == null) null else Hex.decode(incomingKeyHex)
        val outgoingKeyHex = preferences.getString("outgoingKey", null)
        outgoingKey = if (outgoingKeyHex == null) null else Hex.decode(outgoingKeyHex)
        val pumpSerial = preferences.getString("pumpSerial", null)
        val manufacturingDate = preferences.getString("manufacturingDate", null)
        val systemIdAppendix = preferences.getLong("systemIdAppendix", 0)
        if (pumpSerial != null) {
            systemIdentification = SystemIdentification()
            systemIdentification!!.serialNumber = pumpSerial
            systemIdentification!!.manufacturingDate = manufacturingDate
            systemIdentification!!.systemIdAppendix = systemIdAppendix
        }
        val releaseSWVersion = preferences.getString("releaseSWVersion", null)
        val uiProcSWVersion = preferences.getString("uiProcSWVersion", null)
        val pcProcSWVersion = preferences.getString("pcProcSWVersion", null)
        val mdTelProcSWVersion = preferences.getString("mdTelProcSWVersion", null)
        val btInfoPageVersion = preferences.getString("btInfoPageVersion", null)
        val safetyProcSWVersion = preferences.getString("safetyProcSWVersion", null)
        val configIndex = preferences.getInt("configIndex", 0)
        val historyIndex = preferences.getInt("historyIndex", 0)
        val stateIndex = preferences.getInt("stateIndex", 0)
        val vocabularyIndex = preferences.getInt("vocabularyIndex", 0)
        if (releaseSWVersion != null) {
            firmwareVersions = FirmwareVersions()
            firmwareVersions?.let {
                it.releaseSWVersion = releaseSWVersion
                it.uiProcSWVersion = uiProcSWVersion
                it.pcProcSWVersion = pcProcSWVersion
                it.mdTelProcSWVersion = mdTelProcSWVersion
                it.btInfoPageVersion = btInfoPageVersion
                it.safetyProcSWVersion = safetyProcSWVersion
                it.configIndex = configIndex
                it.historyIndex = historyIndex
                it.stateIndex = stateIndex
                it.vocabularyIndex = vocabularyIndex
            }
        }
    }
}