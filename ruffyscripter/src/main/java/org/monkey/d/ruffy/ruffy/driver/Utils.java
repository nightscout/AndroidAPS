package org.monkey.d.ruffy.ruffy.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by fishermen21 on 16.05.17.
 */

public class Utils {

    public static byte[] generateKey(String strKey)
    {
        byte[] pin = new byte[10];

        for(int i=0;i<strKey.length();i++)
            pin[i] = ((byte)(strKey.charAt(i)));		//Don't convert to decimal here

        byte[] key = new byte[16];

        for(int i = 0; i<16; i++)
        {
            if(i < 10)
            {
                key[i] = pin[i];
            }
            else
            {
                key[i] = (byte) (0xFF ^ pin[i - 10]);
            }
        }

        return key;
    }


    public static byte[] ccmEncrypt(byte[] padded, Object key, byte[] nonce)
    {
        byte[] xi = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};		//Initialization vector
        byte[] u = new byte[8];											//Output array U-MAC

        xi[0] = 0x79;													//Set flags for IV

        for(int i=0;i<nonce.length;i++)								//Copy nonce
            xi[i+1] = nonce[i];

        xi[14] = 0;														//Length is zero
        xi[15] = 0;

        xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);				//Encrypt to generate XI from IV

        for(int i=0;i<(padded.length / 16);i++)
        {
            for(int n=0;n<16;n++)
            {
                xi[n] ^= padded[(i * 16)+n];
            }
            xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);
        }

        //should not happen

        if ((padded.length % 16) != 0)
        {
            for (int i=0; i < 16; i++)
            {
                if ( i < (padded.length % 16) )
                {
                    xi[i] ^= padded[((padded.length / 16) * 16) + i];
                }
                else
                {
                    xi[i] ^= 16 - (padded.length % 16);
                }
            }
            xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);
        }

        for(int i=0;i<u.length;i++)										//Copy XI to U
            u[i] = xi[i];

        xi[0] = 65;														//Set flags

        for(int n=0;n<nonce.length;n++)
            xi[n+1] = nonce[n];

        xi[14] = 0;
        xi[15] = 0;

        xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);				//Encrypt XI

        for(int i=0;i<u.length;i++)										//XOR to create U-MAC
            u[i] ^= xi[i];

        return u;
    }

    public static boolean ccmVerify(byte[] paddedPacket, Object key, byte[] u, byte[] nonce)
    {
        paddedPacket = Packet.padPacket(paddedPacket);

        boolean verified = true;
        byte[] u_prime = Utils.ccmEncrypt(paddedPacket, key, nonce);						//Run encryption and check if U-MAC matches

        for(int i=0;i<u_prime.length;i++)
        {
            if(u_prime[i] != u[i])									//Compare U-MAC values
            {
                verified = false;
                break;
            }
        }

        return verified;
    }

    public static List<Byte> ccmAuthenticate(List<Byte> buffer, Object key, byte[] nonceIn)
    {
        List<Byte> output = new ArrayList<Byte>();
        int origLength = buffer.size();					//Hang on to the original length

        byte[] packet = new byte[buffer.size()];		//Create primitive array
        for(int i=0;i<packet.length;i++)				//Copy to byte array
            packet[i] = buffer.get(i);

        byte[] paddedPacket = Packet.padPacket(packet);
        byte[] nonce = nonceIn;

        byte[] umac = Utils.ccmEncrypt(paddedPacket,key,nonce);							//Generate U-MAC value

        ByteBuffer packetBuffer = ByteBuffer.allocate(origLength + umac.length);
        packetBuffer.order(ByteOrder.LITTLE_ENDIAN);

        packetBuffer.put(paddedPacket, 0, origLength);
        packetBuffer.put(umac);

        for(int i=0;i<packetBuffer.array().length;i++)	//Convert to List<Byte>
            output.add(packetBuffer.array()[i]);

        return output;
    }

    public static void addCRC(List<Byte> out)
    {
        short crc = -1;
        Object[] objArr = new Object[1];
        objArr[0] = Short.valueOf((short)-1);
        for (int i = 0; i < out.size(); i += 1) {
            crc = Utils.updateCrc(crc, ((Byte) out.get(i)).byteValue());
            objArr = new Object[1];
            objArr[0] = Short.valueOf(crc);
        }
        out.add(Byte.valueOf((byte) (crc & 255)));
        out.add(Byte.valueOf((byte) (crc >> 8)));
        for (int i = 0; i < 8; i += 1) {
            out.add(Byte.valueOf((byte) 0));
        }
    }
    private static short updateCrc(short crc, byte input) {
        Object[] objArr = new Object[1];
        objArr[0] = Byte.valueOf(input);
        short crcTemp = (short) (((short) input) ^ crc);
        crc = (short) (((short) (crc >> 8)) & 255);
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crcTemp);
        if ((crcTemp & 128) > 0) {
            crc = (short) (crc ^ -31736);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 64) > 0) {
            crc = (short) (crc ^ 16900);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 32) > 0) {
            crc = (short) (crc ^ 8450);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 16) > 0) {
            crc = (short) (crc ^ 4225);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 8) > 0) {
            crc = (short) (crc ^ -29624);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 4) > 0) {
            crc = (short) (crc ^ 17956);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 2) > 0) {
            crc = (short) (crc ^ 8978);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        if ((crcTemp & 1) > 0) {
            crc = (short) (crc ^ 4489);
        }
        objArr = new Object[1];
        objArr[0] = Short.valueOf(crc);
        return crc;
    }

    public static int incrementArray(byte[] array) {
        int i = 0, carry = 0;

        array[i]++;
        if (array[i] == 0)
            carry = 1;

        for (i = 1; i < array.length; i++) {
            if (carry == 1) {
                array[i] += carry;
                if (array[i] > 0) {
                    carry = 0;
                    return carry;
                } else
                    carry = 1;
            }
        }

        return carry;
    }

    public static String byteArrayToHexString(byte[] buffer, int bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes; i++) {
            sb.append(String.format("%02X ", buffer[i]));
        }
        return sb.toString();
    }
    public static byte[] hexStringToByteArray(String s) {
        s = s.replaceAll(" ","");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
