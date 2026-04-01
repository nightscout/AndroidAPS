package app.aaps.core.graph.data

import app.aaps.core.interfaces.graph.Scale

class DeviationDataPointLegacy(x: Double, y: Double, var color: Int, scale: Scale) : ScaledDataPoint(x, y, scale)
