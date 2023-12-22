package app.aaps.implementation.extensions

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Sources

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
