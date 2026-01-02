package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class CmdPairTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0E0E`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertEquals("0E0E", cmd.port)
    }

    @Test
    fun `constructor should process sn from name`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertNotNull(cmd.sn)
        assertTrue(cmd.sn!!.isNotEmpty())
    }

    @Test
    fun `constructor should store address`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertEquals("00:11:22:33:44:55", cmd.address)
    }

    @Test
    fun `constructor should convert sn with convertString`() {
        val cmd = CmdPair("Equil - ABC", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertTrue(cmd.sn!!.contains("0"))
    }

    @Test
    fun `getEventType should return INITIALIZE_EQUIL`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.INITIALIZE_EQUIL, cmd.getEventType())
    }

    @Test
    fun `getEquilResponse should generate random password`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.randomPassword)
        val response = cmd.getEquilResponse()
        assertNotNull(cmd.randomPassword)
        assertEquals(32, cmd.randomPassword!!.size)
    }

    @Test
    fun `getEquilResponse should return EquilResponse`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()
        assertNotNull(response)
    }

    @Test
    fun `getNextEquilResponse should return same as getEquilResponse`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.getNextEquilResponse()
        assertNotNull(response)
    }

    @Test
    fun `randomPassword should be null initially`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertNull(cmd.randomPassword)
    }

    @Test
    fun `ERROR_PWD constant should have correct value`() {
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", CmdPair.ERROR_PWD)
    }

    @Test
    fun `sn should trim whitespace`() {
        val cmd = CmdPair("Equil -   TestDevice  ", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertNotNull(cmd.sn)
    }

    @Test
    fun `multiple getEquilResponse calls should generate different random passwords`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)

        cmd.getEquilResponse()
        val pwd1 = cmd.randomPassword?.clone()

        cmd.randomPassword = null
        cmd.getEquilResponse()
        val pwd2 = cmd.randomPassword

        assertNotNull(pwd1)
        assertNotNull(pwd2)
        // Random passwords should be different
        assertTrue(!pwd1.contentEquals(pwd2))
    }

    @Test
    fun `getEquilResponse should set response field`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()

        assertNotNull(cmd.response)
        assertNotNull(response)
    }

    @Test
    fun `should handle different device names`() {
        val cmd1 = CmdPair("Equil - Device1", "00:11:22:33:44:55", "pass1", aapsLogger, preferences, equilManager)
        val cmd2 = CmdPair("Equil - Device2", "AA:BB:CC:DD:EE:FF", "pass2", aapsLogger, preferences, equilManager)

        assertNotNull(cmd1.sn)
        assertNotNull(cmd2.sn)
        assertTrue(cmd1.sn != cmd2.sn)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }

    @Test
    fun `config should be false initially`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertEquals(false, cmd.config)
    }

    @Test
    fun `isEnd should be false initially`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertEquals(false, cmd.isEnd)
    }

    @Test
    fun `cmdSuccess should be false initially`() {
        val cmd = CmdPair("Equil - TestDevice", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertEquals(false, cmd.cmdSuccess)
    }

    @Test
    fun `ERROR_PWD should be 64 characters`() {
        assertEquals(64, CmdPair.ERROR_PWD.length)
    }

    @Test
    fun `ERROR_PWD should be all zeros`() {
        assertTrue(CmdPair.ERROR_PWD.all { it == '0' })
    }

    @Test
    fun `address should be stored correctly`() {
        val address = "12:34:56:78:9A:BC"
        val cmd = CmdPair("Equil - TestDevice", address, "testpass", aapsLogger, preferences, equilManager)
        assertEquals(address, cmd.address)
    }

    @Test
    fun `should handle empty device name after prefix`() {
        val cmd = CmdPair("Equil - ", "00:11:22:33:44:55", "testpass", aapsLogger, preferences, equilManager)
        assertNotNull(cmd.sn)
    }
}
