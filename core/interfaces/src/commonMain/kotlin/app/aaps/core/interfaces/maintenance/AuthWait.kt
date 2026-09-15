package app.aaps.core.interfaces.maintenance

/**
 * How long a cloud sign in waits for the browser to come back.
 *
 * Named once and shared, because the number has to be the same in the two interfaces that declare
 * the wait and in every implementation of them. When they disagree, the shorter one closes the
 * listener while the longer one is still waiting - and the sign in fails with nothing to show why.
 */
const val AUTH_WAIT_MS: Long = 5 * 60 * 1000
