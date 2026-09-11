package app.aaps.core.interfaces.rx.events

/**
 * Emitted when the active calibration plugin's underlying entries change (add, invalidate,
 * delete). Subscribers should refresh anything derived from the calibration fit, including
 * the BG graph pipeline (`PrepareGraphDataWorker`) which needs to re-run `smoothData()` so
 * the new fit is applied to bucketed values.
 */
class EventCalibrationChanged : Event()
