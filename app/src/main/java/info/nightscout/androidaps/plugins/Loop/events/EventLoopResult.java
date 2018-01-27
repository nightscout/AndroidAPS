package info.nightscout.androidaps.plugins.Loop.events;

import info.nightscout.androidaps.plugins.Loop.APSResult;

public class EventLoopResult {
    public final APSResult apsResult;

    public EventLoopResult(APSResult apsResult) {
        this.apsResult = apsResult;
    }
}

