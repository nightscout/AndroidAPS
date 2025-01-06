package app.aaps.pump.common.hw.rileylink.ble.data;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.NotImplementedException;

import javax.inject.Inject;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil;
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData;
import dagger.android.HasAndroidInjector;
import app.aaps.pump.common.utils.CRC;

/**
 * Created by geoff on 5/30/16.
 */
public class RadioResponse {


    public int rssi;
    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject RileyLinkUtil rileyLinkUtil;
    private boolean decodedOK = false;
    private int responseNumber;
    private byte[] decodedPayload = new byte[0];
    private byte receivedCRC;
    private RileyLinkCommand command;


    public RadioResponse(@NonNull HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public RadioResponse(HasAndroidInjector injector, RileyLinkCommand command /* , byte[] raw */) {
        this(injector);
        this.command = command;
        // init(raw);
    }


    public boolean isValid() {

        // We should check for all listening commands, but only one is actually used
        if (command != null && command.getCommandType() != RileyLinkCommandType.SendAndListen) {
            return true;
        }

        if (!decodedOK) {
            return false;
        }
        if (decodedPayload != null) {
            return receivedCRC == CRC.crc8(decodedPayload);
        }
        return false;
    }


    public void init(byte[] rxData) throws RileyLinkCommunicationException {

        if (rxData == null) {
            return;
        }
        if (rxData.length < 3) {
            // This does not look like something valid heard from a RileyLink device
            return;
        }
        byte[] encodedPayload;

        if (rileyLinkServiceData.firmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher)) {
            encodedPayload = ByteUtil.INSTANCE.substring(rxData, 3, rxData.length - 3);
            rssi = rxData[1];
            responseNumber = rxData[2];
        } else {
            encodedPayload = ByteUtil.INSTANCE.substring(rxData, 2, rxData.length - 2);
            rssi = rxData[0];
            responseNumber = rxData[1];
        }

        try {

            // for non-radio commands we just return the raw response
            // well, for non-radio commands we shouldn't even reach this point
            // but getVersion is kind of exception
            if (command != null && //
                    command.getCommandType() != RileyLinkCommandType.SendAndListen) {
                decodedOK = true;
                decodedPayload = encodedPayload;
                return;
            }

            switch (rileyLinkUtil.getEncoding()) {

                case Manchester:
                case FourByteSixByteRileyLink: {
                    decodedOK = true;
                    decodedPayload = encodedPayload;
                }
                break;

                case FourByteSixByteLocal: {
                    byte[] decodeThis = rileyLinkUtil.getEncoding4b6b().decode4b6b(encodedPayload);

                    if (decodeThis != null && decodeThis.length > 2) {
                        decodedOK = true;

                        decodedPayload = ByteUtil.INSTANCE.substring(decodeThis, 0, decodeThis.length - 1);
                        receivedCRC = decodeThis[decodeThis.length - 1];
                        byte calculatedCRC = CRC.crc8(decodedPayload);
                        if (receivedCRC != calculatedCRC) {
                            aapsLogger.error(LTag.PUMPBTCOMM, String.format("RadioResponse: CRC mismatch, calculated 0x%02x, received 0x%02x",
                                    calculatedCRC, receivedCRC));
                        }
                    } else {
                        throw new RileyLinkCommunicationException(RileyLinkBLEError.TooShortOrNullResponse);
                    }
                }
                break;

                default:
                    throw new NotImplementedException("this {" + rileyLinkUtil.getEncoding().toString()
                            + "} encoding is not supported");
            }
        } catch (NumberFormatException e) {
            decodedOK = false;
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to decode radio data: " + ByteUtil.INSTANCE.shortHexString(encodedPayload));
        }
    }


    public byte[] getPayload() {
        return decodedPayload;
    }
}
