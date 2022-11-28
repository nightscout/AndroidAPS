package info.nightscout.androidaps.danar;

import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import info.nightscout.androidaps.danar.comm.MessageBase;
import info.nightscout.androidaps.danar.comm.MessageHashTableBase;
import info.nightscout.androidaps.utils.CRC;
import info.nightscout.pump.dana.DanaPump;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;

/**
 * Created by mike on 17.07.2016.
 */
public class SerialIOThread extends Thread {
    private final AAPSLogger aapsLogger;

    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private final BluetoothSocket mRfCommSocket;

    private boolean mKeepRunning = true;
    private byte[] mReadBuff = new byte[0];

    private MessageBase processedMessage;
    private final MessageHashTableBase hashTable;
    private final DanaPump danaPump;

    public SerialIOThread(AAPSLogger aapsLogger, BluetoothSocket rfcommSocket, MessageHashTableBase hashTable, DanaPump danaPump) {
        super();
        this.hashTable = hashTable;
        this.danaPump = danaPump;
        this.aapsLogger = aapsLogger;

        mRfCommSocket = rfcommSocket;
        try {
            mOutputStream = mRfCommSocket.getOutputStream();
            mInputStream = mRfCommSocket.getInputStream();
        } catch (IOException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        this.start();
    }

    @Override
    public final void run() {
        try {
            while (mKeepRunning) {
                int availableBytes = mInputStream.available();
                // Ask for 1024 byte (or more if available)
                byte[] newData = new byte[Math.max(1024, availableBytes)];
                int gotBytes = mInputStream.read(newData);
                // When we are here there is some new data available
                appendToBuffer(newData, gotBytes);

                // process all messages we already got
                while (mReadBuff.length > 3) { // 3rd byte is packet size. continue only if we an determine packet size
                    byte[] extractedBuff = cutMessageFromBuffer();
                    if (extractedBuff == null)
                        break; // message is not complete in buffer (wrong packet calls disconnection)

                    int command = (extractedBuff[5] & 0xFF) | ((extractedBuff[4] << 8) & 0xFF00);

                    MessageBase message;
                    if (processedMessage != null && processedMessage.getCommand() == command) {
                        message = processedMessage;
                    } else {
                        // get it from hash table
                        message = hashTable.findMessage(command);
                    }

                    aapsLogger.debug(LTag.PUMPBTCOMM,
                            "<<<<< " + message.getMessageName() + " " + MessageBase.Companion.toHexString(extractedBuff));

                    // process the message content
                    message.setReceived(true);
                    message.handleMessage(extractedBuff);
                    synchronized (message) {
                        message.notify();
                    }
                }
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("bt socket closed"))
                aapsLogger.error("Thread exception: ", e);
            mKeepRunning = false;
        }
        disconnect("EndOfLoop");
    }

    private void appendToBuffer(byte[] newData, int gotBytes) {
        // add newData to mReadBuff
        byte[] newReadBuff = new byte[mReadBuff.length + gotBytes];
        System.arraycopy(mReadBuff, 0, newReadBuff, 0, mReadBuff.length);
        System.arraycopy(newData, 0, newReadBuff, mReadBuff.length, gotBytes);
        mReadBuff = newReadBuff;
    }

    private byte[] cutMessageFromBuffer() {
        if (mReadBuff[0] == (byte) 0x7E && mReadBuff[1] == (byte) 0x7E) {
            int length = (mReadBuff[2] & 0xFF) + 7;
            // Check if we have enough data
            if (mReadBuff.length < length) {
                return null;
            }
            if (mReadBuff[length - 2] != (byte) 0x2E || mReadBuff[length - 1] != (byte) 0x2E) {
                aapsLogger.error("wrong packet lenght=" + length + " data " + MessageBase.Companion.toHexString(mReadBuff));
                disconnect("wrong packet");
                return null;
            }

            short crc = CRC.INSTANCE.getCrc16(mReadBuff, 3, length - 7);
            byte crcByte0 = (byte) (crc >> 8 & 0xFF);
            byte crcByte1 = (byte) (crc & 0xFF);

            byte crcByte0received = mReadBuff[length - 4];
            byte crcByte1received = mReadBuff[length - 3];

            if (crcByte0 != crcByte0received || crcByte1 != crcByte1received) {
                aapsLogger.error("CRC Error" + String.format("%02x ", crcByte0) + String.format("%02x ", crcByte1) + String.format("%02x ", crcByte0received) + String.format("%02x ", crcByte1received));
                disconnect("crc error");
                return null;
            }
            // Packet is verified here. extract data
            byte[] extractedBuff = new byte[length];
            System.arraycopy(mReadBuff, 0, extractedBuff, 0, length);
            // remove extracted data from read buffer
            byte[] unprocessedData = new byte[mReadBuff.length - length];
            System.arraycopy(mReadBuff, length, unprocessedData, 0, unprocessedData.length);
            mReadBuff = unprocessedData;
            return extractedBuff;
        } else {
            aapsLogger.error("Wrong beginning of packet len=" + mReadBuff.length + "    " + MessageBase.Companion.toHexString(mReadBuff));
            disconnect("Wrong beginning of packet");
            return null;
        }
    }

    public synchronized void sendMessage(MessageBase message) {
        if (!mRfCommSocket.isConnected()) {
            aapsLogger.error("Socket not connected on sendMessage");
            return;
        }
        processedMessage = message;

        byte[] messageBytes = message.getRawMessageBytes();
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + message.getMessageName() + " " + MessageBase.Companion.toHexString(messageBytes));

        try {
            mOutputStream.write(messageBytes);
        } catch (Exception e) {
            aapsLogger.error("sendMessage write exception: ", e);
        }

        synchronized (message) {
            try {
                message.wait(5000);
            } catch (InterruptedException e) {
                aapsLogger.error("sendMessage InterruptedException", e);
            }
        }

        SystemClock.sleep(200);
        if (!message.isReceived()) {
            message.handleMessageNotReceived();
            aapsLogger.error(LTag.PUMPBTCOMM, "Reply not received " + message.getMessageName());
            if (message.getCommand() == 0xF0F1) {
                danaPump.setNewPump(false);
                aapsLogger.debug(LTag.PUMPBTCOMM, "Old firmware detected");
            }
        }
    }

    public void disconnect(String reason) {
        mKeepRunning = false;
        try {
            mInputStream.close();
        } catch (Exception e) {
            aapsLogger.debug(LTag.PUMPBTCOMM, e.getMessage());
        }
        try {
            mOutputStream.close();
        } catch (Exception e) {
            aapsLogger.debug(LTag.PUMPBTCOMM, e.getMessage());
        }
        try {
            mRfCommSocket.close();
        } catch (Exception e) {
            aapsLogger.debug(LTag.PUMPBTCOMM, e.getMessage());
        }
        try {
            System.runFinalization();
        } catch (Exception e) {
            aapsLogger.debug(LTag.PUMPBTCOMM, e.getMessage());
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnected: " + reason);
    }

}
