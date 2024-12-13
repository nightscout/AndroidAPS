package app.aaps.pump.common.hw.rileylink;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b;
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import app.aaps.pump.common.hw.rileylink.data.BleAdvertisedData;
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem;

/**
 * Created by andy on 17/05/2018.
 */

@Singleton
public class RileyLinkUtil {

    private final List<RLHistoryItem> historyRileyLink = new ArrayList<>();

    private RileyLinkEncodingType encoding;
    private Encoding4b6b encoding4b6b;

    @Inject AAPSLogger aapsLogger;

    @Inject
    public RileyLinkUtil() {
    }

    public RileyLinkEncodingType getEncoding() {
        return encoding;
    }


    public void setEncoding(RileyLinkEncodingType encoding) {
        this.encoding = encoding;

        if (encoding == RileyLinkEncodingType.FourByteSixByteLocal) {
            this.encoding4b6b = new Encoding4b6bGeoff(aapsLogger);
        }
    }


    public void sendBroadcastMessage(String message, Context context) {
        Intent intent = new Intent(message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    public static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }


    @NonNull public static BleAdvertisedData parseAdertisedData(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();
        String name = null;
        if (advertisedData == null) {
            return new BleAdvertisedData(uuids, name);
        }

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0)
                break;

            byte type = buffer.get();
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2) {
                        uuids
                                .add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                        length -= 2;
                    }
                    break;
                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        uuids.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;
                case 0x09:
                    byte[] nameBytes = new byte[length - 1];
                    buffer.get(nameBytes);
                    name = new String(nameBytes, StandardCharsets.UTF_8);
                    break;
                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }
        return new BleAdvertisedData(uuids, name);
    }

    @NonNull public List<RLHistoryItem> getRileyLinkHistory() {
        return historyRileyLink;
    }

    public Encoding4b6b getEncoding4b6b() {
        return encoding4b6b;
    }
}
