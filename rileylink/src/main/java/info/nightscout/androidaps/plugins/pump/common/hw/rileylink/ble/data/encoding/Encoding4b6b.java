package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;

/**
 * Created by andy on 11/24/18.
 */

public interface Encoding4b6b {

    byte[] encode4b6b(byte[] data);


    byte[] decode4b6b(byte[] data) throws RileyLinkCommunicationException;

}
