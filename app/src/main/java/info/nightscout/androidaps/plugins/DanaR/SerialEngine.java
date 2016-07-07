package info.nightscout.androidaps.plugins.DanaR;

import android.bluetooth.BluetoothSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.DanaRMessage;
import info.nightscout.androidaps.plugins.DanaR.comm.DanaRMessageHashTable;
import info.nightscout.utils.CRC;

/**
 * Created by mike on 04.07.2016.
 */
public class SerialEngine extends Thread {
    private static Logger log = LoggerFactory.getLogger(SerialEngine.class);

    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private BluetoothSocket mRfcommSocket;
    private final DanaConnection mDc;

    private boolean mKeepRunning = true;
    private byte[] mReadBuff = new byte[0];

    private HashMap<Integer, DanaRMessage> mOutputQueue = new HashMap<Integer, DanaRMessage>();

    public SerialEngine(DanaConnection dc, InputStream inputStream, OutputStream outputStream, BluetoothSocket rfcommSocket) {
        super("SerialEngine"); // Thread name
        mInputStream = inputStream;
        mOutputStream = outputStream;
        mRfcommSocket = rfcommSocket;
        mDc = dc;

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

                // add newData to mReadBuff
                byte[] newReadBuff = new byte[mReadBuff.length + gotBytes];

                System.arraycopy(mReadBuff, 0, newReadBuff, 0, mReadBuff.length);
                System.arraycopy(newData, 0, newReadBuff, mReadBuff.length, gotBytes);
                mReadBuff = newReadBuff;

                if (mReadBuff.length < 3) {
                    continue;
                } // 3rd byte is packet size packet still not complete

                // process all messages we already got
                while (mReadBuff.length > 3) {
                    if (mReadBuff[0] == (byte) 0x7E && mReadBuff[1] == (byte) 0x7E) {
                        int length = (mReadBuff[2] & 0xFF) + 7;
                        // Check if we have enough data
                        if (mReadBuff.length < length) {
                            break;
                        }
                        if (mReadBuff[length - 2] != (byte) 0x2E || mReadBuff[length - 1] != (byte) 0x2E) {
                            log.error("wrong packet lenght=" + length + " data " + DanaRMessage.toHexString(mReadBuff));
                            mDc.disconnect("wrong packet");
                            break;
                        }

                        short crc = CRC.getCrc16(mReadBuff, 3, length - 7);
                        byte crcByte0 = (byte) (crc >> 8 & 0xFF);
                        byte crcByte1 = (byte) (crc & 0xFF);

                        byte crcByte0received = mReadBuff[length - 4];
                        byte crcByte1received = mReadBuff[length - 3];

                        if (crcByte0 != crcByte0received || crcByte1 != crcByte1received) {
                            log.error("CRC Error" + String.format("%02x ", crcByte0) + String.format("%02x ", crcByte1) + String.format("%02x ", crcByte0received) + String.format("%02x ", crcByte1received));
                            mDc.disconnect("crc error");
                            break;
                        }
                        // Packet is verified here. extract data
                        byte[] extractedBuff = new byte[length];
                        System.arraycopy(mReadBuff, 0, extractedBuff, 0, length);
                        // remove extracted data from read buffer
                        byte[] unprocessedData = new byte[mReadBuff.length - length];
                        System.arraycopy(mReadBuff, length, unprocessedData, 0, unprocessedData.length);
                        mReadBuff = unprocessedData;

                        int command = (extractedBuff[5] & 0xFF) | ((extractedBuff[4] << 8) & 0xFF00);

                        // Check if message is out queue. if yes use it
                        DanaRMessage message = mOutputQueue.get(command);
                        // if not get it from hash table
                        if (message == null)
                            message = DanaRMessageHashTable.findMessage(command);
                        if (Config.logDanaMessageDetail)
                            log.debug("<<<<< " + message.getMessageName() + " " + message.toHexString(extractedBuff));

                        mDc.scheduleDisconnection();
                        // process the message content
                        message.received = true;
                        message.handleMessage(extractedBuff);
                        mOutputQueue.remove(message.getCommand());
                        synchronized (message) {
                            message.notify();
                        }
                    } else {
                        log.error("Wrong beginning of packet len=" + mReadBuff.length + "    " + DanaRMessage.toHexString(mReadBuff));
                    }
                }
            }
        } catch (Throwable x) {
            if (x instanceof IOException || "socket closed".equals(x.getMessage())) {
                if (Config.logDanaSerialEngine) log.info("Thread run " + x.getMessage());
            } else {
                if (Config.logDanaSerialEngine) log.error("Thread run ", x);
            }
            mKeepRunning = false;
        }
        try {
            mInputStream.close();
        } catch (Exception e) {
            if (Config.logDanaSerialEngine) log.debug(e.getMessage());
        }
        try {
            mOutputStream.close();
        } catch (Exception e) {
            if (Config.logDanaSerialEngine) log.debug(e.getMessage());
        }
        try {
            mRfcommSocket.close();
        } catch (Exception e) {
            if (Config.logDanaSerialEngine) log.debug(e.getMessage());
        }
        try {
            System.runFinalization();
        } catch (Exception e) {
            if (Config.logDanaSerialEngine) log.debug(e.getMessage());
        }
    }

    public synchronized void sendMessage(DanaRMessage message) {
        mOutputQueue.put(message.getCommand(), message);

        byte[] messageBytes = message.getRawMessageBytes();
        if (Config.logDanaSerialEngine)
            log.debug(">>>>> " + message.getMessageName() + " " + message.toHexString(messageBytes));

        try {
            // Wait until input stream is empty
            while (mInputStream.available() > 0) {
                if (Config.logDanaSerialEngine) log.debug("Waiting for empty input stream");
                synchronized (this.mInputStream) {
                    mInputStream.notify();
                }
                Thread.sleep(100);
            }
            mOutputStream.write(messageBytes);
        } catch (Exception e) {
            log.error("sendMessage exception: ", e);
            e.printStackTrace();
        }

        synchronized (mInputStream) {
            mInputStream.notify();
        }

        synchronized (message) {
            try {
                message.wait(5000);
            } catch (InterruptedException e) {
                log.error("sendMessage InterruptedException", e);
                e.printStackTrace();
            }
        }
        if (mOutputQueue.containsKey(message.getCommand())) {
            log.error("Reply not received " + message.getMessageName());
            mOutputQueue.remove(message.getCommand());
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mDc.scheduleDisconnection();
    }

    public void stopLoop() {
        mKeepRunning = false;
    }

}
