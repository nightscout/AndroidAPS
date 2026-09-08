package app.aaps.ui.compose.overview

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which times the basal rebuild has to re-read the profile at.
 *
 * The rebuild used to call `profileFunction.getProfile(time)` once per minute across the whole graph
 * window - about 1,500 calls. That lookup caches on a second-granularity key, drops the entire cache
 * at 30,000 entries, and **never caches a null**, so a cold cache turned one basal rebuild into
 * roughly 1,500 `getEffectiveProfileSwitchActiveAt` database reads. Being that slow is what left the
 * race wide open: four separate collectors trigger a basal rebuild, and a slow one could still be
 * running when a newer one started.
 *
 * The profile can only change where an effective profile switch begins, so those are the only times
 * worth re-reading at. This is the part of that with a decision in it, kept separate because the
 * rebuild itself needs six collaborators before it can run at all.
 */
class ProfileBoundariesTest {

    private fun switchAt(timestamp: Long) = EPS(
        timestamp = timestamp,
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MMOL,
        originalProfileName = "test",
        originalCustomizedName = "test",
        originalTimeshift = 0,
        originalPercentage = 100,
        originalDuration = 0,
        originalEnd = 0,
        iCfg = ICfg("test", 0, 0)
    )

    private val windowStart = 1_000_000L

    @Test
    fun `no switches means the profile is read once, before the loop`() {
        assertEquals(emptyList(), profileBoundariesIn(emptyList(), windowStart))
    }

    @Test
    fun `a switch inside the window is a boundary`() {
        val inside = windowStart + 60_000L

        assertEquals(listOf(inside), profileBoundariesIn(listOf(switchAt(inside)), windowStart))
    }

    /**
     * The switch the window opens with is not a boundary.
     *
     * It is the profile already fetched before the loop starts, so re-reading at it would be a wasted
     * query - and it would land on the wrong minute, because the loop steps in whole minutes and a
     * profile switch does not.
     */
    @Test
    fun `a switch before the window is not a boundary`() {
        assertEquals(emptyList(), profileBoundariesIn(listOf(switchAt(windowStart - 60_000L)), windowStart))
    }

    /** Exactly at the start is the same case: it is the opening profile, not a change within. */
    @Test
    fun `a switch exactly at the window start is not a boundary`() {
        assertEquals(emptyList(), profileBoundariesIn(listOf(switchAt(windowStart)), windowStart))
    }

    /**
     * Ascending, because the caller walks the list with a single moving index.
     *
     * Out of order, a later boundary would be consumed early and every following minute would be
     * evaluated against the wrong profile - a wrong basal line rather than a slow one.
     */
    @Test
    fun `boundaries come back in ascending order whatever order they arrive in`() {
        val first = windowStart + 60_000L
        val second = windowStart + 120_000L
        val third = windowStart + 180_000L

        val boundaries = profileBoundariesIn(listOf(switchAt(third), switchAt(first), switchAt(second)), windowStart)

        assertEquals(listOf(first, second, third), boundaries)
    }

    @Test
    fun `switches before and inside the window are told apart`() {
        val inside = windowStart + 60_000L

        val boundaries = profileBoundariesIn(
            listOf(switchAt(windowStart - 120_000L), switchAt(inside), switchAt(windowStart)),
            windowStart
        )

        assertEquals(listOf(inside), boundaries)
    }
}
