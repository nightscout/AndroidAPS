package app.aaps.core.data.model

import app.aaps.core.data.iob.Iob

/**
 * Insulin on board from this bolus at [time].
 *
 * Lives next to the model because that is all it touches: the work is done by
 * [ICfg.iobCalcForTreatment], which is already here. It used to sit in `:core:objects`, which made
 * every chart that draws an insulin curve depend on that module.
 */
fun BS.iobCalc(time: Long): Iob =
    if (!isValid || type == BS.Type.PRIMING) Iob()
    else iCfg.iobCalcForTreatment(this, time)
