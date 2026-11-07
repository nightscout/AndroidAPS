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
    fun `a host the rule does not know keeps its whole name`() {
        // Cutting a suffix it was never taught would lose which host it was.
        assertEquals("git.example.io:owner/repo", gitRemoteShort("https://git.example.io/owner/repo.git"))
    }

    @Test
    fun `a fork is told apart from upstream`() {
        assertEquals("github:someone/androidaps", gitRemoteShort("https://github.com/someone/AndroidAPS.git"))
    }

    @Test
    fun `a nested group path is kept whole`() {
        assertEquals("gitlab:group/subgroup/repo", gitRemoteShort("https://gitlab.com/group/subgroup/repo.git"))
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
        // The Android copy this replaces lowercased with the default locale. In Turkish that maps I
        // to a dotless i, so GITHUB became gıthub and the phone reported a host no other phone did.
        // The assertion that matters is that an uppercase address lands on the same string as the
        // lowercase one, which is what a locale-sensitive lowercase would break.
        assertEquals(
            gitRemoteShort("https://github.com/nightscout/androidaps.git"),
            gitRemoteShort("HTTPS://GITHUB.COM/Nightscout/AndroidAPS.GIT")
        )
        assertEquals("github:nightscout/androidaps", gitRemoteShort("HTTPS://GITHUB.COM/Nightscout/AndroidAPS.GIT"))
    }

    @Test
    fun `an access token in the remote never reaches the crash report`() {
        // What "git remote get-url" returns after cloning with a token. On iOS this string is built
        // into the app and uploaded as a custom key, so leaving it in would send the token along.
        assertEquals("github:owner/repo", gitRemoteShort("https://ghp_exampletoken@github.com/owner/repo.git"))
        assertEquals("github:owner/repo", gitRemoteShort("https://user:ghp_exampletoken@github.com/owner/repo.git"))
        assertEquals("github:owner/repo", gitRemoteShort("https://oauth2:glpat-exampletoken@github.com/owner/repo.git"))
    }

    @Test
    fun `a credential holding a slash is refused rather than passed on`() {
        // The hole this shape of parsing closes. A password with an unencoded slash used to look
        // like part of the path, so the whole credential survived into the report. Saying "unknown"
        // loses which repository it was, which is much the cheaper mistake.
        assertEquals("unknown", gitRemoteShort("https://user:pa/ss@github.com/owner/repo.git"))
    }

    @Test
    fun `an ssh remote is reported the same as the https one`() {
        // One repository, two ways of cloning it. A crash report must not tell them apart.
        assertEquals("github:nightscout/androidaps", gitRemoteShort("git@github.com:Nightscout/AndroidAPS.git"))
        assertEquals("github:nightscout/androidaps", gitRemoteShort("ssh://git@github.com/Nightscout/AndroidAPS.git"))
        assertEquals(
            gitRemoteShort("https://github.com/nightscout/AndroidAPS.git"),
            gitRemoteShort("git@github.com:nightscout/AndroidAPS.git")
        )
    }

    @Test
    fun `a port does not become part of the repository path`() {
        // A self hosted server reached on another port is still one repository, so the port has to
        // go. It used to be folded into the path, which split the same repository in two.
        assertEquals("git.example:owner/repo", gitRemoteShort("ssh://git@git.example.com:2222/owner/repo.git"))
        assertEquals(
            gitRemoteShort("https://git.example.com/owner/repo.git"),
            gitRemoteShort("ssh://git@git.example.com:2222/owner/repo.git")
        )
    }

    @Test
    fun `a trailing slash does not make a second name for one repository`() {
        assertEquals("github:owner/repo", gitRemoteShort("https://github.com/owner/repo.git/"))
        assertEquals("github:owner/repo", gitRemoteShort("https://github.com/owner/repo/"))
    }

    @Test
    fun `only a real git suffix is removed`() {
        // The old code replaced ".git" anywhere, so a repository named like this lost part of itself.
        assertEquals("github:owner/foo.github", gitRemoteShort("https://github.com/owner/foo.github"))
    }

    @Test
    fun `a local clone says only that it was local`() {
        // The path would carry the account name of whoever built it, and names no repository anyone
        // else can open. Windows counts too - most of this project is built there.
        assertEquals("local", gitRemoteShort("/Users/someone/Github/AndroidAPS"))
        assertEquals("local", gitRemoteShort("file:///Users/someone/Github/AndroidAPS"))
        assertEquals("local", gitRemoteShort("C:\\Users\\someone\\Github\\AndroidAPS"))
        assertEquals("local", gitRemoteShort("E:/GitHub/AndroidAPS"))
    }

    @Test
    fun `an address in no known shape is refused`() {
        // Fails closed on purpose: anything not understood is reported as unknown rather than sent
        // on with whatever was not trimmed off it.
        assertEquals("unknown", gitRemoteShort("https://github.com/owner/re@po.git"))
        assertEquals("unknown", gitRemoteShort("https://github.com/"))
    }
}
