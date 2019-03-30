package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class FirmwareVersions {

    private String releaseSWVersion;
    private String uiProcSWVersion;
    private String pcProcSWVersion;
    private String mdTelProcSWVersion;
    private String btInfoPageVersion;
    private String safetyProcSWVersion;
    private int configIndex;
    private int historyIndex;
    private int stateIndex;
    private int vocabularyIndex;

    public String getReleaseSWVersion() {
        return this.releaseSWVersion;
    }

    public String getUiProcSWVersion() {
        return this.uiProcSWVersion;
    }

    public String getPcProcSWVersion() {
        return this.pcProcSWVersion;
    }

    public String getMdTelProcSWVersion() {
        return this.mdTelProcSWVersion;
    }

    public String getBtInfoPageVersion() {
        return this.btInfoPageVersion;
    }

    public String getSafetyProcSWVersion() {
        return this.safetyProcSWVersion;
    }

    public int getConfigIndex() {
        return this.configIndex;
    }

    public int getHistoryIndex() {
        return this.historyIndex;
    }

    public int getStateIndex() {
        return this.stateIndex;
    }

    public int getVocabularyIndex() {
        return this.vocabularyIndex;
    }

    public void setReleaseSWVersion(String releaseSWVersion) {
        this.releaseSWVersion = releaseSWVersion;
    }

    public void setUiProcSWVersion(String uiProcSWVersion) {
        this.uiProcSWVersion = uiProcSWVersion;
    }

    public void setPcProcSWVersion(String pcProcSWVersion) {
        this.pcProcSWVersion = pcProcSWVersion;
    }

    public void setMdTelProcSWVersion(String mdTelProcSWVersion) {
        this.mdTelProcSWVersion = mdTelProcSWVersion;
    }

    public void setBtInfoPageVersion(String btInfoPageVersion) {
        this.btInfoPageVersion = btInfoPageVersion;
    }

    public void setSafetyProcSWVersion(String safetyProcSWVersion) {
        this.safetyProcSWVersion = safetyProcSWVersion;
    }

    public void setConfigIndex(int configIndex) {
        this.configIndex = configIndex;
    }

    public void setHistoryIndex(int historyIndex) {
        this.historyIndex = historyIndex;
    }

    public void setStateIndex(int stateIndex) {
        this.stateIndex = stateIndex;
    }

    public void setVocabularyIndex(int vocabularyIndex) {
        this.vocabularyIndex = vocabularyIndex;
    }
}
