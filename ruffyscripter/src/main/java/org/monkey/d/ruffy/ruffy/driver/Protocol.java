package org.monkey.d.ruffy.ruffy.driver;

import android.bluetooth.BluetoothAdapter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Created by fishermen21 on 16.05.17.
 */

public class Protocol {
    public static void sendSyn(BTConnection btConn)  {

        Utils.incrementArray(btConn.getPumpData().getNonceTx());

        byte[] p_s = {16,23,0,0,0};

        List<Byte> packet = Packet.buildPacket(p_s, null, true, btConn);							//Use real address (gathered in Key Response)
        packet = Utils.ccmAuthenticate(packet, btConn.getPumpData().getToPumpKey(), btConn.getPumpData().getNonceTx());			//Add U-MAC (Use D->P key)

        List<Byte> temp = Frame.frameEscape(packet);
        byte[] ro = new byte[temp.size()];
        int i = 0;
        for(byte b : temp)
            ro[i++]=b;

        btConn.write(ro);
    }
    public static void sendIDReq(BTConnection btConn) {
        btConn.getPumpData().resetTxNonce();														//Reset TX Nonce (previous to this the nonce is not used and is zero)
        Utils.incrementArray(btConn.getPumpData().getNonceTx());														//Increment it to 1

        ByteBuffer ids = ByteBuffer.allocate(17);								//Allocate payload

        String btName = BluetoothAdapter.getDefaultAdapter().getName();				//Get the Device ID

        byte[] deviceId = new byte[13];
        for(int i=0;i<deviceId.length;i++)
        {
            if(i < btName.length())
                deviceId[i] = (byte)btName.charAt(i);
            else
                deviceId[i] = (byte)0x00;
        }

        String swver = "5.04";													//Get the SW Version
        int clientId = 0;

        clientId += (((byte)swver.charAt(3)) - 0x30);
        clientId += (((byte)swver.charAt(2)) - 0x30)*10;
        clientId += (((byte)swver.charAt(0)) - 0x30)*100;
        clientId += (10000);

        ids.order(ByteOrder.LITTLE_ENDIAN);
        ids.putInt(clientId);
        ids.put(deviceId);

        byte[] p_r = {16,0x12,17,0,0};

        List<Byte> packet = Packet.buildPacket(p_r, ids, true,btConn);							//Use real address (gathered in Key Response)
        packet = Utils.ccmAuthenticate(packet, btConn.getPumpData().getToPumpKey(), btConn.getPumpData().getNonceTx());			//Add U-MAC (Use D->P key)

        List<Byte> temp = Frame.frameEscape(packet);
        byte[] ro = new byte[temp.size()];
        int i = 0;
        for(byte b : temp)
            ro[i++]=b;

       btConn.write(ro);
    }

    public static void sendAck(byte sequenceNUmber,BTConnection btConn) {
        btConn.getPumpData().incrementNonceTx();

        List<Byte> packet = Packet.buildPacket(new byte[]{16, 5, 0, 0, 0}, null, true,btConn);

        packet.set(1, (byte) (packet.get(1) | sequenceNUmber));

        packet = Utils.ccmAuthenticate(packet, btConn.getPumpData().getToPumpKey(), btConn.getPumpData().getNonceTx());

        List<Byte> temp = Frame.frameEscape(packet);
        byte[] ro = new byte[temp.size()];
        int i = 0;
        for (byte b : temp)
            ro[i++] = b;

        btConn.write(ro);
    }
}
