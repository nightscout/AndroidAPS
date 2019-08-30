package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum PodInitActionType {

    PairAndPrime, //
    PairPod(PairAndPrime), //
    PrimePod(PairAndPrime), //


    ;


    PodInitActionType() {

    }


    private PodInitActionType(PodInitActionType parent) {

    }


}
