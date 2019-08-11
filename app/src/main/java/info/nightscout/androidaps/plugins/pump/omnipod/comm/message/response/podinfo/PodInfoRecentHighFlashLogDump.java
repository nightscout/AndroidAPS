package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

public class PodInfoRecentHighFlashLogDump extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 3;

    private final ArrayList<byte[]> dwords;

    private final int lastEntryIndex;

    public PodInfoRecentHighFlashLogDump(byte[] encodedData, int bodyLength) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        lastEntryIndex = ByteUtil.toInt(encodedData[1], encodedData[2]);
        dwords = new ArrayList<>();

        int numberOfDwords = (bodyLength - 3) / 4;

        for (int i = 0; numberOfDwords > i; i++) {
            byte[] dword = ByteUtil.substring(encodedData, 3 + (4 * i), 4);
            dwords.add(dword);
        }
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.RECENT_HIGH_FLASH_LOG_DUMP;
    }

    public List<byte[]> getDwords() {
        return Collections.unmodifiableList(dwords);
    }

    public int getLastEntryIndex() {
        return lastEntryIndex;
    }

    @Override
    public String toString() {
        return "PodInfoRecentHighFlashLogDump{" +
                "lastEntryIndex=" + lastEntryIndex +
                ",dwords=" + dwords +
                '}';
    }
}
