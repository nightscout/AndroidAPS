package info.nightscout.androidaps.plugins.pump.insight.connection_service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;

public class MessageQueue {

    MessageRequest activeRequest;
    final List<MessageRequest> messageRequests = new ArrayList<>();

    public MessageRequest getActiveRequest() {
        return activeRequest;
    }

    public void completeActiveRequest(AppLayerMessage response) {
        if (activeRequest == null) return;
        synchronized (activeRequest) {
            activeRequest.response = response;
            activeRequest.notifyAll();
        }
        activeRequest = null;
    }

    public void completeActiveRequest(Exception exception) {
        if (activeRequest == null) return;
        synchronized (activeRequest) {
            activeRequest.exception = exception;
            activeRequest.notifyAll();
        }
        activeRequest = null;
    }

    public void completePendingRequests(Exception exception) {
        for (MessageRequest messageRequest : messageRequests) {
            synchronized (messageRequest) {
                messageRequest.exception = exception;
                messageRequest.notifyAll();
            }
        }
        messageRequests.clear();
    }

    public void enqueueRequest(MessageRequest messageRequest) {
        messageRequests.add(messageRequest);
        Collections.sort(messageRequests);
    }

    public void nextRequest() {
        if (messageRequests.size() != 0) {
            activeRequest = messageRequests.get(0);
            messageRequests.remove(0);
        }
    }

    public boolean hasPendingMessages() {
        return messageRequests.size() != 0;
    }

    public void reset() {
        activeRequest = null;
        messageRequests.clear();
    }
}
