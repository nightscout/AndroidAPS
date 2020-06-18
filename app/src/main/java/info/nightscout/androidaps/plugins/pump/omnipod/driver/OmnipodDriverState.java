package info.nightscout.androidaps.plugins.pump.omnipod.driver;

public enum OmnipodDriverState {

    NotInitalized, // when we start
    Initalized_NoPod, // driver is initalized, but there is no pod
    Initalized_PodInitializing, // driver is initalized, pod is initalizing
    Initalized_PodAttached, // driver is initalized, pod is there

}
