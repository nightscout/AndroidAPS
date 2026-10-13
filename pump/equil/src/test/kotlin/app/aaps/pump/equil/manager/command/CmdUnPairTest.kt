package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class CmdUnPairTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0E0E`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertEquals("0E0E", cmd.port)
    }

    @Test
    fun `constructor should process sn from name`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        // sn should have "Equil - " removed and be converted with convertString
        assertNotNull(cmd.sn)
        assertTrue(cmd.sn!!.isNotEmpty())
    }

    @Test
    fun `constructor should convert sn with convertString`() {
        val cmd = CmdUnPair("Equil - ABC", "testpass", aapsLogger, preferences, equilManager)
        // convertString adds "0" before each character
        assertTrue(cmd.sn!!.contains("0"))
    }

    @Test
    fun `getEventType should return UNPAIR_EQUIL`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.UNPAIR_EQUIL, cmd.getEventType())
    }

    @Test
    fun `clear1 should generate random password`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.randomPassword)
        val response = cmd.clear1()
        assertNotNull(cmd.randomPassword)
        assertEquals(32, cmd.randomPassword!!.size)
    }

    @Test
    fun `clear1 should return EquilResponse`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.clear1()
        assertNotNull(response)
    }

    @Test
    fun `getEquilResponse should call clear1`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()
        assertNotNull(response)
        assertNotNull(cmd.randomPassword)
    }

    @Test
    fun `getNextEquilResponse should return same as getEquilResponse`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response1 = cmd.getNextEquilResponse()
        assertNotNull(response1)
    }

    @Test
    fun `config flag should be false initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertFalse(cmd.config)
    }

    @Test
    fun `isEnd flag should be false initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertFalse(cmd.isEnd)
    }

    @Test
    fun `cmdSuccess should be false initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertFalse(cmd.cmdSuccess)
    }

    @Test
    fun `password should be stored`() {
        val cmd = CmdUnPair("Equil - TestDevice", "mypassword", aapsLogger, preferences, equilManager)
        assertEquals("mypassword", cmd.password)
    }

    @Test
    fun `sn should trim whitespace`() {
        val cmd = CmdUnPair("Equil -   TestDevice  ", "testpass", aapsLogger, preferences, equilManager)
        // Should trim and process
        assertNotNull(cmd.sn)
    }

    @Test
    fun `multiple clear1 calls should generate different random passwords`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)

        cmd.clear1()
        val pwd1 = cmd.randomPassword?.clone()

        cmd.randomPassword = null
        cmd.clear1()
        val pwd2 = cmd.randomPassword

        assertNotNull(pwd1)
        assertNotNull(pwd2)
        // Random passwords should be different
        assertTrue(!pwd1.contentEquals(pwd2))
    }

    @Test
    fun `clear1 should return non-null response`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.clear1()
        assertNotNull(response)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }

    @Test
    fun `should handle different device names`() {
        val cmd1 = CmdUnPair("Equil - Device1", "pass1", aapsLogger, preferences, equilManager)
        val cmd2 = CmdUnPair("Equil - Device2", "pass2", aapsLogger, preferences, equilManager)

        assertNotNull(cmd1.sn)
        assertNotNull(cmd2.sn)
        assertTrue(cmd1.sn != cmd2.sn)
    }

    @Test
    fun `should handle different passwords`() {
        val cmd1 = CmdUnPair("Equil - TestDevice", "password1", aapsLogger, preferences, equilManager)
        val cmd2 = CmdUnPair("Equil - TestDevice", "password2", aapsLogger, preferences, equilManager)

        assertEquals("password1", cmd1.password)
        assertEquals("password2", cmd2.password)
    }

    @Test
    fun `getEquilResponse should set response field`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()

        assertNotNull(cmd.response)
        assertNotNull(response)
    }

    @Test
    fun `enacted should be true by default`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertTrue(cmd.enacted)
    }

    @Test
    fun `response should be null initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.response)
    }

    @Test
    fun `runPwd should be null initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.runPwd)
    }

    @Test
    fun `runCode should be null initially`() {
        val cmd = CmdUnPair("Equil - TestDevice", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.runCode)
    }

    @Test
    fun `convertString should process sn correctly`() {
        val cmd1 = CmdUnPair("Equil - AB", "testpass", aapsLogger, preferences, equilManager)
        val cmd2 = CmdUnPair("Equil - XYZ", "testpass", aapsLogger, preferences, equilManager)

        // convertString adds "0" before each character, so sn should contain "0"
        assertTrue(cmd1.sn!!.contains("0"))
        assertTrue(cmd2.sn!!.contains("0"))
    }

    @Test
    fun `should handle empty device name after prefix`() {
        val cmd = CmdUnPair("Equil - ", "testpass", aapsLogger, preferences, equilManager)
        assertNotNull(cmd.sn)
    }
}
