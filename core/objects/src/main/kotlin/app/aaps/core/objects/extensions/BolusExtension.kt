package app.aaps.core.objects.extensions

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS

fun BS.iobCalc(time: Long): Iob =
    if (!isValid || type == BS.Type.PRIMING) Iob()
    else iCfg.iobCalcForTreatment(this, time)