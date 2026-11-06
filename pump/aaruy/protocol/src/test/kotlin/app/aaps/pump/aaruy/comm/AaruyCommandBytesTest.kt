package app.aaps.pump.aaruy.comm

import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Queue

/**
 * What the client sends for each command, in each pump state, and which listener calls it makes.
 *
 * The expected lines in `aaruy_commands.txt` were made once from the vendor AAR, when the Kotlin port still matched
 * it byte by byte. The AAR is gone now, so this file keeps that check. A change here is a change on the wire:
 * make it only on purpose, and test it on a real pump.
 */
class AaruyCommandBytesTest {

    private val expected = javaClass.getResourceAsStream("/aaruy_commands.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }

    @Test
    fun commandsSendTheSameBytesAsTheVendorLibrary() {
        val actual = AaruyCommandScript.run(KotlinClient())
        assertEquals(expected.filter { it.startsWith("cmd ") }.size, actual.size)
        expected.filter { it.startsWith("cmd ") }.zip(actual).forEach { (want, got) -> assertEquals(want, got) }
    }

    @Test
    fun pinFromDeviceNameIsTheSameAsTheVendorLibrary() {
        val pins = expected.filter { it.startsWith("pin ") }
        assertEquals(AaruyCommandScript.pinNames.size, pins.size)
        AaruyCommandScript.pinNames.zip(pins).forEach { (name, line) -> assertEquals(line, "pin [$name] ${AaruyBleClient.pinFromDeviceName(name)}") }
    }

    private class KotlinClient : AaruyCommandScript.Client {

        private val log = mutableListOf<String>()
        private val client = AaruyBleClient(mock(Context::class.java), mock(AAPSLogger::class.java), mock(AaruyBleTransport::class.java, RETURNS_DEEP_STUBS), mock(Looper::class.java))
        private val link: Any = client.javaClass.getDeclaredField("link").apply { isAccessible = true }.get(client)
        private val writing = link.javaClass.getDeclaredField("writing").apply { isAccessible = true }

        init {
            client.setOnBleListener(AaruyCommandScript.recorder(AaruyBleListener::class.java, log) as AaruyBleListener)
            link.javaClass.getDeclaredField("ready").apply { isAccessible = true }.setBoolean(link, true)
        }

        override fun receive(frame: ByteArray) {
            writing.setBoolean(link, true)
            attempt { link.javaClass.getDeclaredMethod("handleFrame", ByteArray::class.java).apply { isAccessible = true }.invoke(link, frame) }
        }

        override fun call(name: String, vararg args: Any) {
            writing.setBoolean(link, true)
            attempt { client.javaClass.getMethod(name, *args.map { AaruyCommandScript.primitive(it) }.toTypedArray()).invoke(client, *args) }
        }

        override fun basicParam(maxBasal: Double, maxBolus: Double, step: Int, low: Double, sound: Int, type: Int) {
            writing.setBoolean(link, true)
            client.setupBasicParamInfo(BasicParam(maxBasal, maxBolus, step, low, sound, type))
        }

        override fun basal(perDay: Double, items: List<Pair<Int, Double>>) {
            writing.setBoolean(link, true)
            client.setupBasalInfo(BasalInfo(BasalItems(perDay, items.size, items.map { BasalItem(it.first, it.second) })))
        }

        override fun snapshot(): String {
            @Suppress("UNCHECKED_CAST") val queue = link.javaClass.getDeclaredField("queue").apply { isAccessible = true }.get(link) as Queue<Any>
            val sent = queue.map { request ->
                fun <T> get(name: String): T {
                    @Suppress("UNCHECKED_CAST")
                    return request.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(request) as T
                }
                AaruyCommandScript.describe(get("paramId"), get("isRead"), get("data"))
            }
            queue.clear()
            val result = "seq=" + link.javaClass.getDeclaredField("sequence").apply { isAccessible = true }.get(link) + " queue=" + sent + " log=" + log
            log.clear()
            return result
        }

        private fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (_: InvocationTargetException) {
                // The vendor library threw at the same places. The snapshot shows what was sent before.
            }
        }
    }
}

/** The commands and pump states that [AaruyCommandBytesTest] runs. */
internal object AaruyCommandScript {

    interface Client {

        fun receive(frame: ByteArray)
        fun call(name: String, vararg args: Any)
        fun basicParam(maxBasal: Double, maxBolus: Double, step: Int, low: Double, sound: Int, type: Int)
        fun basal(perDay: Double, items: List<Pair<Int, Double>>)
        fun snapshot(): String
    }

    val pinNames = listOf(
        "aaruy_", "aaruy_X", "aaruy_99999", "aaruy_A0B1C2", "aaruy_ABCDEFGHIJKLMN", "aaruy_A20002B", "aaruy_K12345Z", "aaruy_0000000",
        "aaruy_9999999", "aaruy_ZZZZZZZ", "aaruy_Q2R3S4T5", "aaruy_P0Q1R2S3", "aaruy_0000000000", "aaruy_ZZZZZZZZZZ", "aaruy_C9B8A7", "aaruy_T5U6V7W "
    )

    private val commands: List<Pair<String, (Client) -> Unit>> = buildList {
        for ((now, delayed, minutes) in listOf(Triple(1.0, 0.0, 0), Triple(0.025, 0.0, 0), Triple(0.0, 2.5, 30), Triple(30.0, 0.0, 0), Triple(4.35, 1.15, 60))) {
            for (smb in listOf(true, false)) add("bolus $now $delayed $minutes $smb" to { p: Client -> p.call("setupAndStartBolus", now, delayed, minutes, smb) })
        }
        add("cancelBolus" to { it.call("cancelBolus") })
        for ((rate, minutes) in listOf(1.5 to 30, 0.0 to 60, 0.275 to 90, 2.0 to 0)) add("tbr $rate $minutes" to { p: Client -> p.call("startTempBasal", rate, minutes) })
        add("cancelTbr" to { it.call("cancelTempBasal") })
        for (mode in 0..2) add("closed $mode" to { p: Client -> p.call("setClosedMode", mode) })
        add("continue" to { it.call("continueInject") })
        add("startBasal" to { it.call("startBasal") })
        add("push" to { it.call("pushPosition") })
        add("replace" to { it.call("replaceDevice") })
        for (needle in 0..1) add("needle $needle" to { p: Client -> p.call("needlePushAir", needle.toByte()) })
        for (time in listOf(0L, 1609430400001L, 1790000000000L)) {
            add("daily $time" to { p: Client -> p.call("getHistoryDailyData", time) })
            add("log $time" to { p: Client -> p.call("getHistoryLogData", time) })
        }
        add("unbind" to { it.call("unbindDevice") })
        add("curStatus" to { it.call("getCurStatus") })
        for (type in 0..4) add("confirm $type" to { p: Client -> p.call("confirmReply", type) })
        add("basicParam" to { it.basicParam(1.5, 10.0, 2, 20.0, 1, 3) })
        add("basicParam2" to { it.basicParam(35.0, 25.0, 1, 50.0, 3, 0) })
        add("basal" to { it.basal(12.3, listOf(0 to 0.5, 12 to 0.275, 40 to 1.15)) })
    }

    /** One line per command and pump state: the requests queued and the listener calls made. */
    fun run(client: Client): List<String> {
        val lines = mutableListOf<String>()
        fun feed(paramId: Short, payload: ByteArray) {
            client.receive(AaruyFrame.buildWrite(paramId, 1, payload))
            client.snapshot()
        }
        feed(17, ReportDevicePacket().apply { deviceType = 6; version = 2; serial = 100 }.toBytes())
        feed(0, BasicParamPacket().apply { maxBolus = 10000; pumpType = 3 }.toBytes())
        for (running in 0..8) {
            for ((name, command) in commands) {
                feed(6, ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(5).putShort(200).put(running.toByte()).array())
                command(client)
                lines += "cmd $name running=$running ${client.snapshot()}"
            }
        }
        return lines
    }

    private val timedParams = setOf<Short>(0, 1, 4, 5, 7, 8, 9, 10, 17)

    /** The first 4 bytes of these requests are the phone time, so they are left out. */
    fun describe(paramId: Short, isRead: Boolean, data: ByteArray): String {
        val masked = data.copyOf()
        if (paramId in timedParams) for (i in 0 until minOf(4, masked.size)) masked[i] = 0
        return "$paramId/$isRead/" + masked.joinToString("") { "%02x".format(it) }
    }

    fun recorder(target: Class<*>, log: MutableList<String>): Any =
        Proxy.newProxyInstance(target.classLoader, arrayOf(target)) { _, method, args ->
            if (method.declaringClass == Any::class.java) return@newProxyInstance null
            log.add(method.name + "(" + args.orEmpty().joinToString() + ")")
            Unit
        }

    fun primitive(arg: Any): Class<*> = when (arg) {
        is Double  -> Double::class.javaPrimitiveType!!
        is Int     -> Int::class.javaPrimitiveType!!
        is Long    -> Long::class.javaPrimitiveType!!
        is Boolean -> Boolean::class.javaPrimitiveType!!
        is Byte    -> Byte::class.javaPrimitiveType!!
        else       -> arg.javaClass
    }
}
