package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.queue.CommandQueue;

/**
 * Created by adrian on 2020-01-07.
 */

public interface CommandQueueProvider {
    CommandQueue getCommandQueue();
}
