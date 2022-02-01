package info.nightscout.androidaps.plugins.pump.insight.app_layer;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.IncompatibleAppVersionException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidAppCRCException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.UnknownAppCommandException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.UnknownServiceException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.AppLayerErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.UnknownAppLayerErrorCodeException;
import info.nightscout.androidaps.plugins.pump.insight.ids.AppCommandIDs;
import info.nightscout.androidaps.plugins.pump.insight.ids.AppErrorIDs;
import info.nightscout.androidaps.plugins.pump.insight.ids.ServiceIDs;
import info.nightscout.androidaps.plugins.pump.insight.satl.DataMessage;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.Cryptograph;

public class AppLayerMessage implements Comparable<AppLayerMessage> {

    private static final byte VERSION = 0x20;

    private final MessagePriority messagePriority;
    private final boolean inCRC;
    private final boolean outCRC;
    private final Service service;

    public AppLayerMessage(MessagePriority messagePriority, boolean inCRC, boolean outCRC, Service service) {
        this.messagePriority = messagePriority;
        this.inCRC = inCRC;
        this.outCRC = outCRC;
        this.service = service;
    }

    protected ByteBuf getData() {
        return new ByteBuf(0);
    }

    protected void parse(ByteBuf byteBuf) throws Exception {

    }

    public ByteBuf serialize(Class<? extends AppLayerMessage> clazz) {
        byte[] data = getData().getBytes();
        ByteBuf byteBuf = new ByteBuf(4 + data.length + (outCRC ? 2 : 0));
        byteBuf.putByte(VERSION);
        byteBuf.putByte(ServiceIDs.IDS.getID(getService()));
        byteBuf.putUInt16LE(AppCommandIDs.IDS.getID(clazz));
        byteBuf.putBytes(data);
        if (outCRC) byteBuf.putUInt16LE(Cryptograph.calculateCRC(data));
        return byteBuf;
    }

    public static AppLayerMessage deserialize(ByteBuf byteBuf) throws Exception {
        byte version = byteBuf.readByte();
        byte service = byteBuf.readByte();
        int command = byteBuf.readUInt16LE();
        int error = byteBuf.readUInt16LE();
        Class<? extends AppLayerMessage> clazz = AppCommandIDs.IDS.getType(command);
        if (clazz == null) throw new UnknownAppCommandException();
        if (version != VERSION) throw new IncompatibleAppVersionException();
        AppLayerMessage message = clazz.newInstance();
        if (ServiceIDs.IDS.getType(service) == null) throw new UnknownServiceException();
        if (error != 0) {
            Class<? extends AppLayerErrorException> exceptionClass = AppErrorIDs.IDS.getType(error);
            if (exceptionClass == null) throw new UnknownAppLayerErrorCodeException(error);
            else throw exceptionClass.getConstructor(int.class).newInstance(error);
        }
        byte[] data = byteBuf.readBytes(byteBuf.getSize() - (message.inCRC ? 2 : 0));
        if (message.inCRC && Cryptograph.calculateCRC(data) != byteBuf.readUInt16LE()) throw new InvalidAppCRCException();
        message.parse(ByteBuf.from(data));
        return message;
    }

    public static DataMessage wrap(AppLayerMessage message) {
        DataMessage dataMessage = new DataMessage();
        dataMessage.setData(message.serialize(message.getClass()));
        return dataMessage;
    }

    public static AppLayerMessage unwrap(DataMessage dataMessage) throws Exception {
        return deserialize(dataMessage.getData());
    }

    @Override
    public int compareTo(AppLayerMessage o) {
        return messagePriority.compareTo(o.messagePriority);
    }

    public Service getService() {
        return this.service;
    }
}
