package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.pump.core.utils.ByteUtil;

public class SendAndListen extends RileyLinkCommand {

    @Inject RileyLinkServiceData rileyLinkServiceData;

    private final byte sendChannel;
    private final byte repeatCount;
    private final int delayBetweenPackets_ms;
    private final byte listenChannel;
    private final int timeout_ms;
    private final byte retryCount;
    private final Integer preambleExtension_ms;
    private final RadioPacket packetToSend;


    public SendAndListen(HasAndroidInjector injector, byte sendChannel, byte repeatCount, byte delayBetweenPackets_ms, byte listenChannel,
            int timeout_ms, byte retryCount, RadioPacket packetToSend

    ) {
        this(injector, sendChannel, repeatCount, delayBetweenPackets_ms, listenChannel, timeout_ms, retryCount, null,
            packetToSend);
    }


    public SendAndListen(HasAndroidInjector injector, byte sendChannel, byte repeatCount, int delayBetweenPackets_ms, byte listenChannel,
                         int timeout_ms, byte retryCount, Integer preambleExtension_ms, RadioPacket packetToSend

    ) {
        super();
        injector.androidInjector().inject(this);
        this.sendChannel = sendChannel;
        this.repeatCount = repeatCount;
        this.delayBetweenPackets_ms = delayBetweenPackets_ms;
        this.listenChannel = listenChannel;
        this.timeout_ms = timeout_ms;
        this.retryCount = retryCount;
        this.preambleExtension_ms = preambleExtension_ms == null ? 0 : preambleExtension_ms;
        this.packetToSend = packetToSend;
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.SendAndListen;
    }


    @Override
    public byte[] getRaw() {

        // If firmware version is not set (error reading version from device, shouldn't happen),
        // we will default to version 2
        boolean isPacketV2 = rileyLinkServiceData.firmwareVersion == null || rileyLinkServiceData.firmwareVersion
                .isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher);

        ArrayList<Byte> bytes = new ArrayList<Byte>();
        bytes.add(this.getCommandType().code);
        bytes.add(this.sendChannel);
        bytes.add(this.repeatCount);

        if (isPacketV2) { // delay is unsigned 16-bit integer
            byte[] delayBuff = ByteBuffer.allocate(4).putInt(delayBetweenPackets_ms).array();
            bytes.add(delayBuff[2]);
            bytes.add(delayBuff[3]);
        } else {
            bytes.add((byte)delayBetweenPackets_ms);
        }

        bytes.add(this.listenChannel);

        byte[] timeoutBuff = ByteBuffer.allocate(4).putInt(timeout_ms).array();

        bytes.add(timeoutBuff[0]);
        bytes.add(timeoutBuff[1]);
        bytes.add(timeoutBuff[2]);
        bytes.add(timeoutBuff[3]);

        bytes.add(retryCount);

        if (isPacketV2) { // 2.x (and probably higher versions) support preamble extension
            byte[] preambleBuf = ByteBuffer.allocate(4).putInt(preambleExtension_ms).array();
            bytes.add(preambleBuf[2]);
            bytes.add(preambleBuf[3]);
        }

        return ByteUtil.concat(ByteUtil.getByteArrayFromList(bytes), packetToSend.getEncoded());

    }
}
