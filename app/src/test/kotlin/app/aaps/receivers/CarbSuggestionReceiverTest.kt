package app.aaps.receivers

import android.content.Intent
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CarbSuggestionReceiverTest : TestBase() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var context: MetroApplication

    private lateinit var sut: CarbSuggestionReceiver

    @BeforeEach
    fun prepare() {
        // The receiver injects through the Application, which implements MetroMemberInjector now. The
        // check in `injectMetroMembers` throws when it does not, so the mock has to answer both the
        // interface and the field filling - the same job the Dagger AndroidInjector did before.
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.injectMembers(any())).thenAnswer {
            val target = it.getArgument<Any>(0)
            if (target is CarbSuggestionReceiver) {
                target.loop = loop
                target.aapsLogger = aapsLogger
            }
            true
        }
        sut = CarbSuggestionReceiver()
    }

    /** A Context that is also the injector, which is what `injectMetroMembers` requires. */
    abstract class MetroApplication : android.app.Application(), MetroMemberInjector

    @Test
    fun passesExplicitDurationToLoop() {
        val intent = mock<Intent>()
        whenever(intent.getIntExtra(eq("ignoreDuration"), anyInt())).thenReturn(30)

        sut.onReceive(context, intent)

        verify(loop).disableCarbSuggestions(30)
    }

    @Test
    fun usesDefaultDurationWhenExtraMissing() {
        val intent = mock<Intent>()
        // Emulate a missing extra: the framework returns the supplied default value
        whenever(intent.getIntExtra(eq("ignoreDuration"), anyInt())).thenAnswer { it.getArgument<Int>(1) }

        sut.onReceive(context, intent)

        verify(loop).disableCarbSuggestions(5)
    }
}
