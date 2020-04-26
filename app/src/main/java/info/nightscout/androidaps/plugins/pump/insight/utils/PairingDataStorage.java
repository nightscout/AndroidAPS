package info.nightscout.androidaps.plugins.pump.insight.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.spongycastle.util.encoders.Hex;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SystemIdentification;

public class PairingDataStorage {

    private SharedPreferences preferences;

    private boolean paired;
    private String macAddress;
    private Nonce lastNonceSent;
    private Nonce lastNonceReceived;
    private long commId;
    private byte[] incomingKey;
    private byte[] outgoingKey;
    private FirmwareVersions firmwareVersions;
    private SystemIdentification systemIdentification;

    public PairingDataStorage(Context context) {
        this.preferences = context.getSharedPreferences(context.getPackageName() + ".PAIRING_DATA_STORAGE", Context.MODE_PRIVATE);
        paired = preferences.getBoolean("paired", false);
        macAddress = preferences.getString("macAddress", null);
        String lastNonceSentHex = preferences.getString("lastNonceSent", null);
        if (lastNonceSentHex != null) lastNonceSent = new Nonce(Hex.decode(lastNonceSentHex));
        String lastNonceReceivedHex = preferences.getString("lastNonceReceived", null);
        if (lastNonceReceivedHex != null) lastNonceReceived = new Nonce(Hex.decode(lastNonceReceivedHex));
        commId = preferences.getLong("commId", 0);
        String incomingKeyHex = preferences.getString("incomingKey", null);
        incomingKey = incomingKeyHex == null ? null : Hex.decode(incomingKeyHex);
        String outgoingKeyHex = preferences.getString("outgoingKey", null);
        outgoingKey = outgoingKeyHex == null ? null : Hex.decode(outgoingKeyHex);

        String pumpSerial = preferences.getString("pumpSerial", null);
        String manufacturingDate = preferences.getString("manufacturingDate", null);
        long systemIdAppendix = preferences.getLong("systemIdAppendix", 0);

        if (pumpSerial != null) {
            systemIdentification = new SystemIdentification();
            systemIdentification.setSerialNumber(pumpSerial);
            systemIdentification.setManufacturingDate(manufacturingDate);
            systemIdentification.setSystemIdAppendix(systemIdAppendix);
        }

        String releaseSWVersion = preferences.getString("releaseSWVersion", null);
        String uiProcSWVersion = preferences.getString("uiProcSWVersion", null);
        String pcProcSWVersion = preferences.getString("pcProcSWVersion", null);
        String mdTelProcSWVersion = preferences.getString("mdTelProcSWVersion", null);
        String btInfoPageVersion = preferences.getString("btInfoPageVersion", null);
        String safetyProcSWVersion = preferences.getString("safetyProcSWVersion", null);
        int configIndex = preferences.getInt("configIndex", 0);
        int historyIndex = preferences.getInt("historyIndex", 0);
        int stateIndex = preferences.getInt("stateIndex", 0);
        int vocabularyIndex = preferences.getInt("vocabularyIndex", 0);
        if (releaseSWVersion != null) {
            firmwareVersions = new FirmwareVersions();
            firmwareVersions.setReleaseSWVersion(releaseSWVersion);
            firmwareVersions.setUiProcSWVersion(uiProcSWVersion);
            firmwareVersions.setPcProcSWVersion(pcProcSWVersion);
            firmwareVersions.setMdTelProcSWVersion(mdTelProcSWVersion);
            firmwareVersions.setBtInfoPageVersion(btInfoPageVersion);
            firmwareVersions.setSafetyProcSWVersion(safetyProcSWVersion);
            firmwareVersions.setConfigIndex(configIndex);
            firmwareVersions.setHistoryIndex(historyIndex);
            firmwareVersions.setStateIndex(stateIndex);
            firmwareVersions.setVocabularyIndex(vocabularyIndex);
        }
    }

    public void setPaired(boolean paired) {
        this.paired = paired;
        preferences.edit().putBoolean("paired", paired).apply();
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
        preferences.edit().putString("macAddress", macAddress).apply();
    }

    public void setLastNonceSent(Nonce lastNonceSent) {
        this.lastNonceSent = lastNonceSent;
        preferences.edit().putString("lastNonceSent", lastNonceSent == null ? null : Hex.toHexString(lastNonceSent.getStorageValue())).apply();
    }

    public void setLastNonceReceived(Nonce lastNonceReceived) {
        this.lastNonceReceived = lastNonceReceived;
        preferences.edit().putString("lastNonceReceived", lastNonceReceived == null ? null : Hex.toHexString(lastNonceReceived.getStorageValue())).apply();
    }

    public void setCommId(long commId) {
        this.commId = commId;
        preferences.edit().putLong("commId", commId).apply();
    }

    public void setIncomingKey(byte[] incomingKey) {
        this.incomingKey = incomingKey;
        preferences.edit().putString("incomingKey", incomingKey == null ? null : Hex.toHexString(incomingKey)).apply();
    }

    public void setOutgoingKey(byte[] outgoingKey) {
        this.outgoingKey = outgoingKey;
        preferences.edit().putString("outgoingKey", outgoingKey == null ? null : Hex.toHexString(outgoingKey)).apply();
    }

    public SharedPreferences getPreferences() {
        return this.preferences;
    }

    public boolean isPaired() {
        return this.paired;
    }

    public String getMacAddress() {
        return this.macAddress;
    }

    public Nonce getLastNonceSent() {
        return this.lastNonceSent;
    }

    public Nonce getLastNonceReceived() {
        return this.lastNonceReceived;
    }

    public long getCommId() {
        return this.commId;
    }

    public byte[] getIncomingKey() {
        return this.incomingKey;
    }

    public byte[] getOutgoingKey() {
        return this.outgoingKey;
    }

    public FirmwareVersions getFirmwareVersions() {
        return firmwareVersions;
    }

    public void setFirmwareVersions(FirmwareVersions firmwareVersions) {
        this.firmwareVersions = firmwareVersions;
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
                    .apply();
        } else {
            preferences.edit()
                    .putString("releaseSWVersion", firmwareVersions.getReleaseSWVersion())
                    .putString("uiProcSWVersion", firmwareVersions.getUiProcSWVersion())
                    .putString("pcProcSWVersion", firmwareVersions.getPcProcSWVersion())
                    .putString("mdTelProcSWVersion", firmwareVersions.getMdTelProcSWVersion())
                    .putString("btInfoPageVersion", firmwareVersions.getBtInfoPageVersion())
                    .putString("safetyProcSWVersion", firmwareVersions.getSafetyProcSWVersion())
                    .putInt("configIndex", firmwareVersions.getConfigIndex())
                    .putInt("historyIndex", firmwareVersions.getHistoryIndex())
                    .putInt("stateIndex", firmwareVersions.getStateIndex())
                    .putInt("vocabularyIndex", firmwareVersions.getVocabularyIndex())
                    .apply();
        }
    }

    public SystemIdentification getSystemIdentification() {
        return systemIdentification;
    }

    public void setSystemIdentification(SystemIdentification systemIdentification) {
        this.systemIdentification = systemIdentification;
        if (systemIdentification == null) {
            preferences.edit()
                    .putString("pumpSerial", null)
                    .putString("manufacturingDate", null)
                    .putLong("systemIdAppendix", 0)
                    .apply();
        } else {
            preferences.edit()
                    .putString("pumpSerial", systemIdentification.getSerialNumber())
                    .putString("manufacturingDate", systemIdentification.getManufacturingDate())
                    .putLong("systemIdAppendix", systemIdentification.getSystemIdAppendix())
                    .apply();
        }
    }

    public void reset() {
        setPaired(false);
        setMacAddress(null);
        setCommId(0);
        setIncomingKey(null);
        setOutgoingKey(null);
        setLastNonceReceived(null);
        setLastNonceSent(null);
        setFirmwareVersions(null);
        setSystemIdentification(null);
        setMacAddress(null);
    }
}
