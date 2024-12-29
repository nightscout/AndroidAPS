package app.aaps.pump.medtronic.comm.history

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
enum class RecordDecodeStatus(var description: String) {

    OK("OK     "),  //
    Ignored("IGNORE "),  //
    NotSupported("N/A YET"),  //
    Error("ERROR  "),  //
    WIP("WIP    "),  //
    Unknown("UNK    ");

}