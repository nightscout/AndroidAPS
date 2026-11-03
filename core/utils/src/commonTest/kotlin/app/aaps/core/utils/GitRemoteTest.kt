package app.aaps.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class GitRemoteTest {

    @Test
    fun `an https github remote becomes owner and repo`() {
        assertEquals("github:nightscout/androidaps", gitRemoteShort("https://github.com/nightscout/AndroidAPS.git"))
    }

    @Test
    fun `the git suffix is optional`() {
        assertEquals("github:nightscout/androidaps", gitRemoteShort("https://github.com/nightscout/AndroidAPS"))
    }

    @Test
    fun `plain http is shortened the same way`() {
        assertEquals("github:owner/repo", gitRemoteShort("http://github.com/owner/repo.git"))
    }

    @Test
    fun `org and net hosts are shortened too`() {
        assertEquals("example:owner/repo", gitRemoteShort("https://example.org/owner/repo.git"))
        assertEquals("example:owner/repo", gitRemoteShort("https://example.net/owner/repo.git"))
    }

    @Test
    fun `a fork is told apart from upstream`() {
        assertEquals("github:someone/androidaps", gitRemoteShort("https://github.com/someone/AndroidAPS.git"))
    }

    @Test
    fun `the no-git answer is passed through so it is visible in the report`() {
        assertEquals("nogitsystemavailable", gitRemoteShort("NoGitSystemAvailable"))
    }

    @Test
    fun `an empty remote stays empty rather than becoming a made up name`() {
        assertEquals("", gitRemoteShort(""))
    }

    @Test
    fun `the result does not depend on the device language`() {
        // The Android copy this replaces lowercased with the default locale, so a Turkish phone
        // turned GITHUB into gıthub and reported a remote that matched no other phone's.
        assertEquals("github:nightscout/androidaps", gitRemoteShort("HTTPS://GITHUB.COM/Nightscout/AndroidAPS.GIT"))
    }
}
