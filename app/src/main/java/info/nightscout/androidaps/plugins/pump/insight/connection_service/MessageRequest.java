package info.nightscout.androidaps.plugins.pump.insight.connection_service;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;

public class MessageRequest<T extends AppLayerMessage> implements Comparable<MessageRequest> {

    T request;
    T response;
    Exception exception;

    MessageRequest(T request) {
        this.request = request;
    }

    public T await() throws Exception {
        synchronized (this) {
            while (exception == null && response == null) wait();
            if (exception != null) throw exception;
            return response;
        }
    }

    public T await(long timeout) throws Exception {
        synchronized (this) {
            while (exception == null && response == null) wait(timeout);
            if (exception != null) throw exception;
            return response;
        }
    }

    @Override
    public int compareTo(MessageRequest messageRequest) {
        return request.compareTo(messageRequest.request);
    }

    public T getRequest() {
        return this.request;
    }

    public T getResponse() {
        return this.response;
    }

    public Exception getException() {
        return this.exception;
    }
}
