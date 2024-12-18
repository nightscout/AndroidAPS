package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;

public class PodInfoOlderPulseLog extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 3;

    private final ArrayList<byte[]> dwords;

    public PodInfoOlderPulseLog(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        dwords = new ArrayList<>();

        int numberOfDwordLogEntries = ByteUtil.INSTANCE.toInt(encodedData[1], encodedData[2]);
        for (int i = 0; numberOfDwordLogEntries > i; i++) {
            byte[] dword = ByteUtil.INSTANCE.substring(encodedData, 3 + (4 * i), 4);
            dwords.add(dword);
        }
    }

    @NonNull @Override
    public PodInfoType getType() {
        return PodInfoType.OLDER_PULSE_LOG;
    }

    public List<byte[]> getDwords() {
        return Collections.unmodifiableList(dwords);
    }

    @NonNull @Override
    public String toString() {
        String out = "PodInfoOlderPulseLog{" +
                "dwords=[";

        List<String> hexDwords = new ArrayList<>();
        for (byte[] dword : dwords) {
            hexDwords.add(ByteUtil.INSTANCE.shortHexStringWithoutSpaces(dword));
        }
        out += TextUtils.join(", ", hexDwords);
        out += "]}";

        return out;
    }
}
