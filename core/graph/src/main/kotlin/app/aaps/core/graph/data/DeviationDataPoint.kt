package app.aaps.core.graph.data

import app.aaps.core.interfaces.graph.Scale

class DeviationDataPoint(x: Double, y: Double, var color: Int, scale: Scale) : ScaledDataPoint(x, y, scale)
