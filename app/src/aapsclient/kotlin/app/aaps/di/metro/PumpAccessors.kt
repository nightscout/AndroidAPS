package app.aaps.di.metro

/**
 * The follower build's copy of the pump accessors: empty, on purpose.
 *
 * A follower has no pump module on its classpath, so
 * none of these types exists here. `MetroGraphs` names this interface from `src/main`, which has to
 * compile for every flavour, and this copy is what makes that possible.
 *
 * See the `src/withPumps` copy for the version with accessors.
 */
interface PumpAccessors
