package app.aaps.core.objects.extensions

import app.aaps.core.data.model.TT

fun TT.target(): Double =
    (this.lowTarget + this.highTarget) / 2
