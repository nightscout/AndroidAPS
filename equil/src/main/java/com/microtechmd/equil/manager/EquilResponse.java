package com.microtechmd.equil.manager;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 *
 */

public class EquilResponse {

    private final LinkedList<ByteBuffer> send;
    private String error_message;
    private long delay = 20;

    public EquilResponse() {
        send = new LinkedList<>();
    }

    public boolean hasError() {
        return error_message != null;
    }

    public void add(ByteBuffer buffer) {
        send.add(buffer);
    }

    public boolean shouldDelay() {
        return delay > 0;
    }

    public LinkedList<ByteBuffer> getSend() {
        return send;
    }

    public String getError_message() {
        return error_message;
    }

    public void setError_message(String error_message) {
        this.error_message = error_message;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }
}


