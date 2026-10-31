package app.aaps.pump.common.defs

import app.aaps.core.interfaces.resources.ResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class PumpDefsTest {

    /** Returns a distinct, predictable string per resource id, so a translation can be traced back. */
    private val rh: ResourceHelper = mock<ResourceHelper>().also {
        whenever(it.gs(any<Int>())).thenAnswer { inv -> "res-${inv.arguments[0]}" }
    }

    // ---- TempBasalPair ----------------------------------------------------------------------

    @Test fun `a percent rate prints with a percent sign`() {
        val pair = TempBasalPair(insulinRate = 150.0, isPercent = true, durationMinutes = 30)

        assertThat(pair.toString()).contains("150.0 %")
        assertThat(pair.toString()).contains("duration=30")
    }

    @Test fun `an absolute rate prints with units`() {
        val pair = TempBasalPair(insulinRate = 0.75, isPercent = false, durationMinutes = 60)

        assertThat(pair.toString()).contains("0.75 U")
    }

    @Test fun `the short constructor leaves the start unset`() {
        val pair = TempBasalPair(1.0, false, 30)

        assertThat(pair.start).isNull()
        assertThat(pair.isActive).isFalse()
        assertThat(pair.id).isNull()
    }

    // The init block derives the end from start + duration. `end` is private with no getter, so this
    // pins the part that is observable: supplying a start must not disturb the rest of the state.
    @Test fun `a start time is kept as given`() {
        val pair = TempBasalPair(1.0, false, 30, start = 10_000L)

        assertThat(pair.start).isEqualTo(10_000L)
        assertThat(pair.durationMinutes).isEqualTo(30)
    }

    @Test fun `the start time can be replaced afterwards`() {
        val pair = TempBasalPair(1.0, false, 30)

        pair.setStartTime(5_000L)

        assertThat(pair.start).isEqualTo(5_000L)
    }

    // ---- PumpHistoryEntryGroup --------------------------------------------------------------

    @Test fun `every group is translated and prints its translation`() {
        val list = PumpHistoryEntryGroup.getTranslatedList(rh)

        assertThat(list).isNotEmpty()
        list.forEach {
            assertThat(it.translated).isNotNull()
            assertThat(it.toString()).isEqualTo(it.translated)
        }
    }

    @Test fun `the translated list covers every declared group`() {
        val list = PumpHistoryEntryGroup.getTranslatedList(rh)

        assertThat(list).containsExactlyElementsIn(PumpHistoryEntryGroup.entries)
    }

    /**
     * Pins current behaviour, which is not the behaviour the types suggest.
     *
     * `getTranslatedList` takes a [PumpTypeGroupConfig] and filters on it, and two entries are marked
     * `// Ypso` in the source. But every entry is declared with the default `PumpTypeGroupConfig.All`,
     * and the non-All branch keeps anything tagged `All` - so both branches return the same full list.
     * No caller passes a non-All value either: `MedtronicHistoryViewModel`, `DashPodHistoryScreen` and
     * `ErosPodHistoryScreen` all use the single-argument overload.
     *
     * The visible consequence is that `EventsOnly` and `EventsNoStat` appear in the history filter of
     * every pump, not only YpsoPump. If that is wrong, the fix is to tag those two entries rather than
     * to change this test.
     */
    @Test fun `the pump type filter currently selects nothing - every config returns the full list`() {
        val all = PumpHistoryEntryGroup.getTranslatedList(rh, PumpTypeGroupConfig.All)
        val ypso = PumpHistoryEntryGroup.getTranslatedList(rh, PumpTypeGroupConfig.YpsoPump)
        val medtronic = PumpHistoryEntryGroup.getTranslatedList(rh, PumpTypeGroupConfig.Medtronic)

        assertThat(ypso).containsExactlyElementsIn(all)
        assertThat(medtronic).containsExactlyElementsIn(all)
        assertThat(all).contains(PumpHistoryEntryGroup.EventsOnly)
    }

    /**
     * Pins the translation cache, which is process-wide and one-shot.
     *
     * `doTranslation` returns early once `translatedList` is non-null, so the first ResourceHelper to
     * reach it decides the strings for the life of the process. A later call with a different helper -
     * after a language change, say - is ignored. Worth knowing before assuming a re-translation happens.
     */
    @Test fun `translation happens once and ignores a later resource helper`() {
        val first = PumpHistoryEntryGroup.getTranslatedList(rh).first().translated

        val other: ResourceHelper = mock<ResourceHelper>().also {
            whenever(it.gs(any<Int>())).thenReturn("SOMETHING-ELSE")
        }
        val afterSecondCall = PumpHistoryEntryGroup.getTranslatedList(other).first().translated

        assertThat(afterSecondCall).isEqualTo(first)
    }

    // ---- PumpDriverState --------------------------------------------------------------------

    // Busy and Suspended count as connected: the link is up, the pump is simply not free. A driver
    // reading this to decide whether to reconnect must not treat them as a lost connection.
    @Test fun `connected covers every state where the link is up`() {
        assertThat(PumpDriverState.entries.filter { it.isConnected() })
            .containsExactly(
                PumpDriverState.Connected,
                PumpDriverState.Initialized,
                PumpDriverState.Busy,
                PumpDriverState.Suspended
            )
    }

    // isInitialized is isConnected minus Connected: being reachable is not the same as being set up.
    @Test fun `initialized is narrower than connected and excludes a bare connection`() {
        assertThat(PumpDriverState.entries.filter { it.isInitialized() })
            .containsExactly(
                PumpDriverState.Initialized,
                PumpDriverState.Busy,
                PumpDriverState.Suspended
            )
        assertThat(PumpDriverState.Connected.isInitialized()).isFalse()
    }

    @Test fun `states before and after a session are neither connected nor initialized`() {
        listOf(
            PumpDriverState.NotInitialized,
            PumpDriverState.Connecting,
            PumpDriverState.Disconnecting,
            PumpDriverState.Disconnected
        ).forEach {
            assertThat(it.isConnected()).isFalse()
            assertThat(it.isInitialized()).isFalse()
        }
    }
}
