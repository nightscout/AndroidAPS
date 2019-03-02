package info.nightscout.androidaps.plugins.pump.insight.utils;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamWriter extends Thread {

    private static final int BUFFER_SIZE = 1024;

    private OutputStream outputStream;
    private Callback callback;
    private final ByteBuf buffer = new ByteBuf(BUFFER_SIZE);

    public OutputStreamWriter(OutputStream outputStream, Callback callback) {
        setName(getClass().getSimpleName());
        this.outputStream = outputStream;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                synchronized (buffer) {
                    if (buffer.getSize() != 0) {
                        outputStream.write(buffer.readBytes());
                        outputStream.flush();
                        buffer.notifyAll();
                    }
                    buffer.wait();
                }
            }
        } catch (IOException e) {
            if (!isInterrupted()) callback.onErrorWhileWriting(e);
        } catch (InterruptedException ignored) {
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
            }
        }
    }

    public void write(byte[] bytes) {
        synchronized (buffer) {
            buffer.putBytes(bytes);
            buffer.notifyAll();
        }
    }

    public void writeAndWait(byte[] bytes) {
        synchronized (buffer) {
            buffer.putBytes(bytes);
            buffer.notifyAll();
            try {
                buffer.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    public void close() {
        interrupt();
        try {
            outputStream.close();
        } catch (IOException e) {
        }
    }

    public interface Callback {
        void onErrorWhileWriting(Exception e);
    }

}
