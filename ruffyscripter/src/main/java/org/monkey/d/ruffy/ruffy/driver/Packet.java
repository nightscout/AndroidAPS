package org.monkey.d.ruffy.ruffy.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by fishermen21 on 16.05.17.
 */

public class Packet {
    public static byte[] padPacket(byte[] packet)
    {
        byte pad;
        byte[] output;

        pad = (byte) (16 - (packet.length % 16));	//(ENCRYPT_BLOCKSIZE - (packet.length % ENCRYPT_BLOCKSIZE));
        if(pad > 0)
        {
            output = new byte[pad+packet.length];
            for(int n=0;n<packet.length;n++)
                output[n] = packet[n];

            for(int i=0;i < pad;i++)
            {
                output[packet.length+i] = pad;
            }
        }
        else
            output =  packet;

        return output;
    }

    public static List<Byte> buildPacket(byte[] command, ByteBuffer payload, boolean address, BTConnection btConn)
    {
        List<Byte> output = new ArrayList<Byte>();

        for(int i=0; i < command.length; i++)
            output.add(command[i]);

        if(address)											//Replace the default address with the real one
        {
            output.remove(command.length-1);				//Remove the last byte (address)
            output.add(btConn.getPumpData().getAddress());		//Add the real address byte
        }

        Packet.addNonce(output, btConn.getPumpData().getNonceTx());

        if(payload!=null)
        {
            payload.rewind();
            for(int i=0;i<payload.capacity();i++)
                output.add(payload.get());
        }

        return output;
    }

    public static void adjustLength(List<Byte> packet, int length)
    {
        packet.set(2, (byte) (length & 0xFF));
        packet.set(3, (byte) (length >> 8));
    }


    public static void addNonce(List<Byte> packet, byte[] nonce)
    {
        for(int i=0;i<nonce.length;i++)
            packet.add(nonce[i]);
    }

    public static void handleRawData(byte buffer[], int bytes, PacketHandler handler) {
        List<Byte> t = new ArrayList<>();
        for (int i = 0; i < bytes; i++)
            t.add(buffer[i]);
        for (List<Byte> x : Frame.frameDeEscaping(t)) {
            byte[] xx = new byte[x.size()];
            for (int i = 0; i < x.size(); i++)
                xx[i] = x.get(i);
            boolean rel = false;
            if (x.size()>1 && (x.get(1) & 0x20) == 0x20) {
                rel = true;

                byte seq = 0x00;
                if ((x.get(1) & 0x80) == 0x80)
                    seq = (byte) 0x80;

                handler.sendImidiateAcknowledge(seq);
            } else {
                rel = false;
            }
            handleRX(xx, x.size(), rel,handler);
        }
    }

    public static enum Response{
        ID,
        SYNC,
        RELIABLE_DATA,
        UNRELIABLE_DATA

    }
    private static void handleRX(byte[] inBuf, int length, boolean reliableFlagged, PacketHandler handler) {

        ByteBuffer buffer = ByteBuffer.wrap(inBuf, 0, length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] nonce, payload, umac, packetNoUmac;

        Byte command;
        buffer.get(); //ignore
        command = buffer.get();

        short payloadlength = buffer.getShort();

        buffer.get(); //ignore

        nonce = new byte[13];
        buffer.get(nonce, 0, nonce.length);

        payload = new byte[payloadlength];
        buffer.get(payload, 0, payload.length);

        umac = new byte[8];
        buffer.get(umac, 0, umac.length);

        packetNoUmac = new byte[buffer.capacity() - umac.length];
        buffer.rewind();
        for (int i = 0; i < packetNoUmac.length; i++)
            packetNoUmac[i] = buffer.get();

        buffer.rewind();

        byte c = (byte)(command & 0x1F);
        switch (c) {
            case 20:
                handler.log("got an id response");
                if (Utils.ccmVerify(packetNoUmac, handler.getToDeviceKey(), umac, nonce)) {
                    handler.handleResponse(Response.ID,reliableFlagged,payload);
                }
                break;
            case 24:
                handler.log("got a sync response ");
                if (Utils.ccmVerify(packetNoUmac, handler.getToDeviceKey(), umac, nonce)) {
                    handler.handleResponse(Response.SYNC,reliableFlagged,payload);
                }
                break;

            case 0x23:
                if (Utils.ccmVerify(packetNoUmac, handler.getToDeviceKey(), umac, nonce)) {
                    handler.handleResponse(Response.RELIABLE_DATA,reliableFlagged,payload);
                }
                break;
            case 0x03:
                if (Utils.ccmVerify(packetNoUmac, handler.getToDeviceKey(), umac, nonce)) {
                    handler.handleResponse(Response.UNRELIABLE_DATA,reliableFlagged,payload);
                }
                break;

            case 0x06:
                if(Utils.ccmVerify(packetNoUmac, handler.getToDeviceKey(), umac, nonce))
                {
                    byte error = 0;
                    String err = "";

                    if(payload.length > 0)
                        error = payload[0];

                    switch(error)
                    {
                        case 0x00:
                            err = "Undefined";
                            break;
                        case 0x0F:
                            err = "Wrong state";
                            break;
                        case 0x33:
                            err = "Invalid service primitive";
                            break;
                        case 0x3C:
                            err = "Invalid payload length";
                            break;
                        case 0x55:
                            err = "Invalid source address";
                            break;
                        case 0x66:
                            err = "Invalid destination address";
                            break;
                    }

                    handler.log( "Error in Transport Layer! ("+err+")");
                    handler.handleErrorResponse(error,err,reliableFlagged,payload);

                }
                break;
            default:
                handler.log("not yet implemented rx command: " + command + " ( " + String.format("%02X", command));

        }
    }
}
