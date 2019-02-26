package info.nightscout.androidaps.plugins.pump.insight.utils;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamReader extends Thread {

    private static final int BUFFER_SIZE = 1024;

    private InputStream inputStream;
    private Callback callback;

    public InputStreamReader(InputStream inputStream, Callback callback) {
        setName(getClass().getSimpleName());
        this.inputStream = inputStream;
        this.callback = callback;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        try {
            while (!isInterrupted()) {
                bytesRead = inputStream.read(buffer);
                if (bytesRead == -1) callback.onErrorWhileReading(new IOException("Stream closed"));
                else callback.onReceiveBytes(buffer, bytesRead);
            }
        } catch (IOException e) {
            if (!isInterrupted()) callback.onErrorWhileReading(e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
            }
        }
    }

    public void close() {
        interrupt();
        try {
            inputStream.close();
        } catch (IOException e) {
        }
    }

    public interface Callback {
        void onReceiveBytes(byte[] buffer, int bytesRead);
        void onErrorWhileReading(Exception e);
    }
}
