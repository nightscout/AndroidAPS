package info.nightscout.plugins.sync.tidepool.messages

import com.google.gson.annotations.Expose

class CloseDatasetRequestMessage : BaseMessage() {
    @Expose
    internal var dataState = "closed"
}