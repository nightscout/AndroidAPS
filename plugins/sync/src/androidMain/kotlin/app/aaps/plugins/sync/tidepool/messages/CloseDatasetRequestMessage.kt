package app.aaps.plugins.sync.tidepool.messages

import com.google.gson.annotations.Expose

class CloseDatasetRequestMessage : BaseMessage() {

    @Expose
    internal var dataState = "closed"
}