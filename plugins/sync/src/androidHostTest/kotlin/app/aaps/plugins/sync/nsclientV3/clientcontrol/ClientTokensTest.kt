package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.nssdk.exceptions.NotAllowedNightscoutException
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.authorization.NSAuthRole
import app.aaps.core.nssdk.localmodel.authorization.NSAuthSubject
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ClientTokensTest {

    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: ClientTokens

    private val clientRole = NSAuthRole(id = "r1", name = ClientTokens.ROLE_NAME, permissions = ClientTokens.ROLE_PERMISSIONS)
    private val tabletToken = NSAuthSubject(id = "s1", name = "aaps-Tablet", roles = listOf(ClientTokens.ROLE_NAME), accessToken = "aapstablet-0123456789abcdef")

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        sut = ClientTokens({ nsClientV3Plugin }, aapsLogger)
    }

    @Test
    fun `without a Nightscout client nothing is tried`() = runTest {
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(null)

        assertThat(sut.tokenFor("Tablet")).isEqualTo(ClientTokens.Result.NotConnected)
    }

    @Test
    fun `a master token without admin rights is NotAllowed`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenAnswer { throw NotAllowedNightscoutException("401") }

        assertThat(sut.tokenFor("Tablet")).isEqualTo(ClientTokens.Result.NotAllowed)
        verify(nsAndroidClient, never()).createAuthSubject(any())
    }

    /** A master that has never made a client token does not get a role it does not use. */
    @Test
    fun `a missing role is stored only when asked to`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(NSAuthRole(name = "admin", permissions = listOf("*"))))

        sut.ensureRole(create = false)
        verify(nsAndroidClient, never()).createAuthRole(any())

        whenever(nsAndroidClient.createAuthRole(any())).thenReturn(clientRole)
        sut.ensureRole(create = true)
        verify(nsAndroidClient).createAuthRole(argThat { name == ClientTokens.ROLE_NAME && permissions == ClientTokens.ROLE_PERMISSIONS })
    }

    /** Someone gave the clients more rights in the Nightscout admin tools: the master sets the role back. */
    @Test
    fun `a changed role is set back`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole.copy(permissions = listOf("*"))))

        assertThat(sut.ensureRole(create = false)).isEqualTo(ClientTokens.Result.Ok(Unit))

        verify(nsAndroidClient).updateAuthRole(argThat { id == "r1" && permissions == ClientTokens.ROLE_PERMISSIONS })
    }

    @Test
    fun `a role as it should be is left alone, in any order`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole.copy(permissions = ClientTokens.ROLE_PERMISSIONS.reversed())))

        sut.ensureRole(create = false)

        verify(nsAndroidClient, never()).updateAuthRole(any())
    }

    /** The same name again is the same phone set up again: it gets its token back instead of a second one. */
    @Test
    fun `an existing client token is used again`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole))
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(listOf(tabletToken))

        assertThat(sut.tokenFor(" Tablet ")).isEqualTo(ClientTokens.Result.Ok(tabletToken))
        verify(nsAndroidClient, never()).createAuthSubject(any())
    }

    /** A subject with this name and other roles is the user's own token: it is never given to a client. */
    @Test
    fun `a token of the same name with other roles is not used`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole))
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(listOf(tabletToken.copy(roles = listOf("admin"))))

        assertThat(sut.tokenFor("Tablet")).isEqualTo(ClientTokens.Result.NameTaken)
        verify(nsAndroidClient, never()).createAuthSubject(any())
    }

    /** The answer to the create has no token; it is read from the list, by the new id. */
    @Test
    fun `a new token is made with only the client role and read back by its id`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole))
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(emptyList(), listOf(tabletToken))
        whenever(nsAndroidClient.createAuthSubject(any())).thenReturn(tabletToken.copy(accessToken = null))

        assertThat(sut.tokenFor("Tablet")).isEqualTo(ClientTokens.Result.Ok(tabletToken))
        verify(nsAndroidClient).createAuthSubject(argThat { name == "aaps-Tablet" && roles == listOf(ClientTokens.ROLE_NAME) && id == null && accessToken == null })
    }

    /**
     * Nightscout serves the subject list from memory and reloads it only when it writes, so a token it has just made
     * can be missing from the list and stay missing. Taking it back is what keeps it from being left behind, invisible,
     * for the next try to trip over - that is how one client ended up with two tokens of the same name.
     */
    @Test
    fun `a new token Nightscout does not list is taken back and made again`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole))
        // Empty for the first check, empty for every read of the first token, then the second one is there.
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), listOf(tabletToken.copy(id = "s2"))
        )
        whenever(nsAndroidClient.createAuthSubject(any())).thenReturn(
            tabletToken.copy(accessToken = null), tabletToken.copy(id = "s2", accessToken = null)
        )

        assertThat(sut.tokenFor("Tablet")).isEqualTo(ClientTokens.Result.Ok(tabletToken.copy(id = "s2")))
        verify(nsAndroidClient).deleteAuthSubject("s1")
        verify(nsAndroidClient, times(2)).createAuthSubject(any())
    }

    /** Both tries came to nothing, and both tokens were taken back: Nightscout is left as it was. */
    @Test
    fun `a token that is never listed fails and leaves nothing behind`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole))
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(emptyList())
        whenever(nsAndroidClient.createAuthSubject(any())).thenReturn(tabletToken.copy(accessToken = null))

        assertThat(sut.tokenFor("Tablet")).isInstanceOf(ClientTokens.Result.Failed::class.java)
        verify(nsAndroidClient, times(ClientTokens.ATTEMPTS)).deleteAuthSubject("s1")
    }

    /** Taking it back failed, so it really is in Nightscout and only the user can clear it: it has to be named. */
    @Test
    fun `a token that cannot be taken back is reported with its name`() = runTest {
        whenever(nsAndroidClient.getAuthRoles()).thenReturn(listOf(clientRole))
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(emptyList())
        whenever(nsAndroidClient.createAuthSubject(any())).thenReturn(tabletToken.copy(accessToken = null))
        whenever(nsAndroidClient.deleteAuthSubject("s1")).thenAnswer { throw RuntimeException("boom") }

        assertThat(sut.tokenFor("Tablet")).isEqualTo(ClientTokens.Result.LeftOver("aaps-Tablet"))
        // Stops at once: a second try would only make a second one next to the token already left behind.
        verify(nsAndroidClient).createAuthSubject(any())
    }

    @Test
    fun `the token list for the screen carries no tokens`() = runTest {
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(listOf(tabletToken))

        val result = sut.subjects()

        assertThat((result as ClientTokens.Result.Ok).value.single().accessToken).isNull()
    }

    @Test
    fun `only a client token is deleted`() = runTest {
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(listOf(tabletToken.copy(roles = listOf("admin"))))

        assertThat(sut.delete("s1")).isEqualTo(ClientTokens.Result.NameTaken)
        verify(nsAndroidClient, never()).deleteAuthSubject(any())
    }

    /** A removed client takes its token along, but not the user's admin token it may have written with. */
    @Test
    fun `deleting by name deletes the client tokens of that name only`() = runTest {
        val usersOwn = NSAuthSubject(id = "s9", name = "aaps-Tablet", roles = listOf("admin"))
        whenever(nsAndroidClient.getAuthSubjects()).thenReturn(listOf(tabletToken, usersOwn))

        assertThat(sut.deleteByName("aaps-Tablet")).isEqualTo(ClientTokens.Result.Ok(1))

        verify(nsAndroidClient).deleteAuthSubject("s1")
        verify(nsAndroidClient, never()).deleteAuthSubject("s9")
    }
}
