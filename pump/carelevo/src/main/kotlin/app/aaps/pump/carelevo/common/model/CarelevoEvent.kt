package app.aaps.pump.carelevo.common.model

/**
 * Marker for one-shot UI events delivered through
 * [app.aaps.pump.carelevo.common.MutableEventFlow] (consumed exactly once per emission) — e.g.
 * the `CarelevoOverviewEvent`/`AlarmEvent` hierarchies in `presentation.model`.
 */
interface Event
