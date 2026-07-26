package app.aaps.plugins.aps.loop

import android.content.Intent
import app.aaps.core.interfaces.aps.Loop
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CarbSuggestionReceiverTest : TestBase() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var context: DaggerApplication

    private lateinit var sut: CarbSuggestionReceiver

    @BeforeEach
    fun prepare() {
        // Dagger injects the receiver via context.applicationContext.androidInjector() in super.onReceive()
        val injector = HasAndroidInjector {
            AndroidInjector {
                if (it is CarbSuggestionReceiver) {
                    it.loop = loop
                    it.aapsLogger = aapsLogger
                }
            }
        }
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.androidInjector()).thenReturn(injector.androidInjector())
        sut = CarbSuggestionReceiver()
    }

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
