package app.aaps.plugins.sync.nfcCommands

import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Minimal stateful [Preferences] double for NfcTagStore tests: get/put/remove of String keys backed by an in-memory map. */
internal class TestNfcPreferences {

    val stored = mutableMapOf<String, String>()

    val preferences: Preferences = mock<Preferences>().also { p ->
        whenever(p.get(any<StringNonPreferenceKey>())).thenAnswer { invocation ->
            val key = invocation.getArgument<StringNonPreferenceKey>(0)
            stored[key.key] ?: key.defaultValue
        }
        whenever(p.put(any<StringNonPreferenceKey>(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<StringNonPreferenceKey>(0)
            stored[key.key] = invocation.getArgument(1)
        }
        whenever(p.remove(any<NonPreferenceKey>())).thenAnswer { invocation ->
            stored.remove(invocation.getArgument<NonPreferenceKey>(0).key)
        }
    }
}