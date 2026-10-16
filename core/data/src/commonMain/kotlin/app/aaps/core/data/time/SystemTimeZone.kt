package app.aaps.core.data.time

/**
 * Time zone offset in milliseconds for a given moment, DST aware.
 *
 * The records keep this as `utcOffset`. It is written to the database, it takes part in
 * `contentEqualsTo` (so a changed value makes a record look different and sync again), it is sent to
 * Nightscout - which validates it and answers 400 when it is wrong - and it goes to Open Humans.
 * So the value has to stay exactly what the old `TimeZone.getDefault().getOffset(timestamp)`
 * produced. Keeping it as a platform function does that at every call site at once, without
 * touching the models or the ~154 places that already pass `utcOffset` themselves.
 */
expect fun systemUtcOffsetAt(timestamp: Long): Long
