package info.nightscout.androidaps.utils

class TIR(val date: Long, val lowThreshold: Double, val highThreshold: Double) {
    private var below = 0;
    private var inRange = 0;
    private var above = 0;
    private var error = 0;
    private var count = 0;

    fun error() = run { error++ }
    fun below() = run { below++; count++ }
    fun inRange() = run { inRange++; count++ }
    fun above() = run { above++; count++ }
}
