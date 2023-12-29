package app.aaps.pump.equil.manager;

import androidx.annotation.NonNull;

public class EquilCmdModel {
    private String code;
    private String iv;
    private String tag;
    private String ciphertext;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    @NonNull @Override public String toString() {
        return "EquilCmdModel{" +
                "code='" + code + '\'' +
                ", iv='" + iv + '\'' +
                ", tag='" + tag + '\'' +
                ", ciphertext='" + ciphertext + '\'' +
                '}';
    }
}
