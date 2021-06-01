package info.nightscout.androidaps.plugins.pump.insight.utils

import android.content.Context
import android.content.SharedPreferences
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SystemIdentification
import org.spongycastle.util.encoders.Hex

class PairingDataStorage(context: Context) {

    private val preferences: SharedPreferences

    private var _paired : Boolean = false
    internal var paired : Boolean
        get() { return _paired }
        set(paired) {
            _paired = paired
            preferences.edit().putBoolean("paired", _paired).apply()
        }

    private var _macAddress : String? = null
    internal var macAddress : String?
        get() { return _macAddress }
        set(macAddress) {
            _macAddress = macAddress
            preferences.edit().putString("macAddress", _macAddress).apply()
        }

    private var _lastNonceSent : Nonce? = null
    internal var lastNonceSent : Nonce?
        get() { return _lastNonceSent }
        set(lastNonceSent) {
            _lastNonceSent = lastNonceSent
            preferences.edit().putString("lastNonceSent", if (lastNonceSent == null) null else Hex.toHexString(lastNonceSent.storageValue)).apply()
        }

    private var _lastNonceReceived : Nonce? = null
    internal var lastNonceReceived : Nonce?
        get() { return _lastNonceReceived }
        set(lastNonceReceived) {
            _lastNonceReceived = lastNonceReceived
            preferences.edit().putString("lastNonceReceived", if (lastNonceReceived == null) null else Hex.toHexString(lastNonceReceived.storageValue)).apply()
        }

    private var _commId : Long = 0
    internal var commId : Long
        get() { return _commId }
        set(commId) {
            _commId = commId
            preferences.edit().putLong("commId", commId).apply()
        }

    private var _incomingKey : ByteArray? = null
    internal var incomingKey : ByteArray?
        get() { return _incomingKey }
        set(incomingKey) {
            _incomingKey = incomingKey
            preferences.edit().putString("incomingKey", if (incomingKey == null) null else Hex.toHexString(incomingKey)).apply()
        }

    private var _outgoingKey : ByteArray? = null
    internal var outgoingKey : ByteArray?
        get() { return _outgoingKey }
        set(outgoingKey) {
            _outgoingKey = outgoingKey
            preferences.edit().putString("outgoingKey", if (outgoingKey == null) null else Hex.toHexString(outgoingKey)).apply()
        }

    private var _firmwareVersions : FirmwareVersions? = null
    internal var firmwareVersions : FirmwareVersions?
        get() { return _firmwareVersions }
        set(firmwareVersions) {
            _firmwareVersions = firmwareVersions
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

    private var _systemIdentification : SystemIdentification? = null
    internal var systemIdentification : SystemIdentification?
        get() { return _systemIdentification }
        set(systemIdentification) {
            _systemIdentification = systemIdentification
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
        _paired = preferences.getBoolean("paired", false)
        _macAddress = preferences.getString("macAddress", null)
        val lastNonceSentHex = preferences.getString("lastNonceSent", null)
        if (lastNonceSentHex != null) _lastNonceSent = Nonce(Hex.decode(lastNonceSentHex))
        val lastNonceReceivedHex = preferences.getString("lastNonceReceived", null)
        if (lastNonceReceivedHex != null) _lastNonceReceived = Nonce(Hex.decode(lastNonceReceivedHex))
        _commId = preferences.getLong("commId", 0)
        val incomingKeyHex = preferences.getString("incomingKey", null)
        _incomingKey = if (incomingKeyHex == null) null else Hex.decode(incomingKeyHex)
        val outgoingKeyHex = preferences.getString("outgoingKey", null)
        _outgoingKey = if (outgoingKeyHex == null) null else Hex.decode(outgoingKeyHex)
        val pumpSerial = preferences.getString("pumpSerial", null)
        val manufacturingDate = preferences.getString("manufacturingDate", null)
        val systemIdAppendix = preferences.getLong("systemIdAppendix", 0)
        if (pumpSerial != null) {
            _systemIdentification = SystemIdentification().also {
                it.serialNumber = pumpSerial
                it.manufacturingDate = manufacturingDate
                it.systemIdAppendix = systemIdAppendix
            }
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
            _firmwareVersions = FirmwareVersions().also {
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