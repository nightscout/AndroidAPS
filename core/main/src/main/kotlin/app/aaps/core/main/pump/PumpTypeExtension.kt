package app.aaps.core.main.pump

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Sources
import app.aaps.database.entities.UserEntry

fun PumpType.Companion.fromDbSource(s: UserEntry.Sources): PumpType.Source =
    when (s) {
        UserEntry.Sources.Dana        -> PumpType.Source.Dana
        UserEntry.Sources.DanaR       -> PumpType.Source.DanaR
        UserEntry.Sources.DanaRC      -> PumpType.Source.DanaRC
        UserEntry.Sources.DanaRv2     -> PumpType.Source.DanaRv2
        UserEntry.Sources.DanaRS      -> PumpType.Source.DanaRS
        UserEntry.Sources.DanaI       -> PumpType.Source.DanaI
        UserEntry.Sources.DiaconnG8   -> PumpType.Source.DiaconnG8
        UserEntry.Sources.Insight     -> PumpType.Source.Insight
        UserEntry.Sources.Combo       -> PumpType.Source.Combo
        UserEntry.Sources.Medtronic   -> PumpType.Source.Medtronic
        UserEntry.Sources.Omnipod     -> PumpType.Source.Omnipod
        UserEntry.Sources.OmnipodEros -> PumpType.Source.OmnipodEros
        UserEntry.Sources.OmnipodDash -> PumpType.Source.OmnipodDash
        UserEntry.Sources.EOPatch2    -> PumpType.Source.EOPatch2
        UserEntry.Sources.MDI         -> PumpType.Source.MDI
        UserEntry.Sources.VirtualPump -> PumpType.Source.VirtualPump
        else                          -> PumpType.Source.Unknown
    }

fun PumpType.Source.toUeSource(): Sources =
    when (this) {
        PumpType.Source.Dana        -> Sources.Dana
        PumpType.Source.DanaR       -> Sources.DanaR
        PumpType.Source.DanaRC      -> Sources.DanaRC
        PumpType.Source.DanaRv2     -> Sources.DanaRv2
        PumpType.Source.DanaRS      -> Sources.DanaRS
        PumpType.Source.DanaI       -> Sources.DanaI
        PumpType.Source.DiaconnG8   -> Sources.DiaconnG8
        PumpType.Source.Insight     -> Sources.Insight
        PumpType.Source.Combo       -> Sources.Combo
        PumpType.Source.Medtronic   -> Sources.Medtronic
        PumpType.Source.Omnipod     -> Sources.Omnipod
        PumpType.Source.OmnipodEros -> Sources.OmnipodEros
        PumpType.Source.OmnipodDash -> Sources.OmnipodDash
        PumpType.Source.EOPatch2    -> Sources.EOPatch2
        PumpType.Source.Medtrum     -> Sources.Medtrum
        PumpType.Source.MDI         -> Sources.MDI
        PumpType.Source.VirtualPump -> Sources.VirtualPump
        else                        -> Sources.Unknown
    }
