package app.aaps.pump.aaruy.comm

import android.os.Looper
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import java.io.OutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Queue
import kotlin.random.Random

/** Compares the Kotlin port with the vendor AAR on the test classpath. */
class AarDifferentialTest {

    private val random = Random(20261104)
    private val realErr = System.err
    private var compared = 0
    private val sentIds = mutableSetOf<String>()
    private val callbacks = mutableSetOf<String>()

    @BeforeEach
    fun quiet() {
        System.setErr(PrintStream(OutputStream.nullOutputStream()))
        val logger = Class.forName("com.aaruy.mylibrary.AALogger")
        logger.getMethod("setIS_DEBUG", Boolean::class.javaPrimitiveType).invoke(logger.getField("INSTANCE").get(null), false)
    }

    @AfterEach
    fun restore() {
        System.setErr(realErr)
        println("COMPARED $compared sent=${sentIds.sorted()} callbacks=${callbacks.sorted()}")
    }

    private fun cls(name: String) = Class.forName(name)
    private fun inst(name: String) = cls(name).getDeclaredConstructor().newInstance()
    private fun parseOld(name: String, data: ByteArray): Any = cls(name).getMethod("a", ByteArray::class.java).invoke(inst(name), data)!!
    private fun map(kt: String, method: String, arg: Any): Any =
        cls("com.aaruy.mylibrary.open.data.$kt").getMethod(method, arg.javaClass).invoke(null, arg)!!

    private fun outcome(block: () -> Any?): String =
        try {
            block().toString()
        } catch (e: InvocationTargetException) {
            "throw " + e.targetException.javaClass.simpleName
        } catch (e: Exception) {
            "throw " + e.javaClass.simpleName
        }

    private fun same(what: String, old: () -> Any?, new: () -> Any?) {
        assertEquals(outcome(old), outcome(new), what)
        compared++
    }

    private fun bytes(size: Int) = random.nextBytes(size)

    private fun sizes(vararg exact: Int): List<Int> = (0..70).toList() + exact.toList()

    @Test
    fun parsersMatch() {
        repeat(300) {
            for (size in sizes(8 + 4 + 3, 8 + 4 + 3 * 24, 32, 5 + 12, 5 + 12 * 10, 1 + 6, 1 + 6 * 10, 13, 14, 15)) {
                val data = bytes(size)
                val hex = data.toHex()
                same("basic $hex", { map("BasicParamKt", "getBasicParam", parseOld("com.base.e", data)) }, { BasicParamPacket.parse(data).toBasicParam() })
                same("basal $hex", { map("BasalInfoKt", "getBasalInfo", parseOld("com.base.d", data)) }, { BasalSettingPacket.parse(data).toBasalInfo() })
                same("log $hex", { map("LogInfoKt", "getLogInfo", parseOld("com.base.w", data)) }, { HistoryLogPacket.parse(data).toLogInfo() })
                same("daily $hex", { map("DailyInfoKt", "getDailyInfo", parseOld("com.base.u", data)) }, { HistoryDailyPacket.parse(data).toDailyInfo() })
                for (running in -1..9) {
                    val status = data.copyOf()
                    if (running >= 0 && status.size > 6) status[6] = running.toByte()
                    same("status ${status.toHex()}", { map("StatusInfoKt", "getStatusInfo", parseOld("com.base.p", status)) }, { CurrentStatusPacket.parse(status).toStatusInfo() })
                }
                same("error $hex", { field(parseOld("com.base.s", data), "a", "b") }, { ErrorInfoPacket.parse(data).run { listOf(errorType, error) } })
                same("real $hex", { field(parseOld("com.base.a0", data), "a", "b") }, { RealDataPacket.parse(data).run { listOf(remainBattery, bleSignal) } })
                same("report $hex", { field(parseOld("com.base.c0", data), "a", "b", "c", "d") }, { ReportDevicePacket.parse(data).run { listOf(settingTime, deviceType, version, serial) } })
            }
        }
    }

    private fun field(target: Any, vararg names: String) = names.map { target.javaClass.getField(it).get(target) }

    @Test
    fun framesMatch() {
        val old = OldPump()
        repeat(5000) {
            val paramId = random.nextInt().toShort()
            val sequence = random.nextInt().toShort()
            val payload = bytes(random.nextInt(0, 120))
            val readClass = cls("com.aaruy.mylibrary.AABleUtil\$b")
            val read = readClass.getDeclaredConstructor(old.util.javaClass).newInstance(old.util)
            readClass.getMethod("b", Short::class.javaPrimitiveType).invoke(read, paramId)
            val oldRead = readClass.getMethod("c", Short::class.javaPrimitiveType).invoke(read, sequence) as ByteArray
            assertEquals(oldRead.toHex(), AaruyFrame.buildRead(paramId, sequence).toHex())
            val writeClass = cls("com.aaruy.mylibrary.AABleUtil\$c")
            val write = writeClass.getDeclaredConstructor(old.util.javaClass).newInstance(old.util)
            writeClass.getMethod("b", Short::class.javaPrimitiveType).invoke(write, paramId)
            val oldWrite = writeClass.getMethod("a", Short::class.javaPrimitiveType, ByteArray::class.java).invoke(write, sequence, payload) as ByteArray
            assertEquals(oldWrite.toHex(), AaruyFrame.buildWrite(paramId, sequence, payload).toHex())
            compared += 2
        }
    }

    @Test
    fun pinMatches() {
        val util = OldPump().util
        val receiverField = util.javaClass.getDeclaredField("myBroadcastReceiver").apply { isAccessible = true }
        val receiver = receiverField.get(util)
        val method = receiver.javaClass.declaredMethods.first { it.parameterTypes.toList() == listOf(String::class.java) && it.returnType == String::class.java }.apply { isAccessible = true }
        val chars = "0123456789QRSTUVWXYZABCP "
        repeat(5000) {
            val name = "aaruy_" + (1..random.nextInt(0, 14)).map { chars[random.nextInt(chars.length)] }.joinToString("")
            assertEquals(method.invoke(receiver, name), AaruyBleClient.pinFromDeviceName(name), name)
            compared++
        }
    }

    @Test
    fun commandsAndDispatchMatch() {
        val old = OldPump()
        val new = NewPump()
        fun both(what: String, block: (Pump) -> Unit) {
            block(old)
            block(new)
            val oldShot = old.snapshot(); assertEquals(oldShot, new.snapshot(), what); Regex("-?[0-9]+(?=/(true|false)/)").findAll(oldShot).forEach { sentIds.add(it.value) }; Regex("[a-zA-Z]+(?=[(])").findAll(oldShot.substringAfter("log=")).forEach { callbacks.add(it.value) }
            compared++
        }

        fun feed(what: String, paramId: Short, payload: ByteArray) {
            val frame = AaruyFrame.buildWrite(paramId, 1, payload)
            both("$what ${payload.toHex()}") { it.receive(frame) }
        }

        val commands: List<Pair<String, (Pump) -> Unit>> = buildList {
            for ((now, delayed, minutes) in listOf(Triple(1.0, 0.0, 0), Triple(0.025, 0.0, 0), Triple(0.0, 2.5, 30), Triple(0.0, 2.5, 45), Triple(30.0, 0.0, 0), Triple(4.35, 1.15, 60))) {
                for (smb in listOf(true, false)) add("bolus $now $delayed $minutes $smb" to { p: Pump -> p.call("setupAndStartBolus", now, delayed, minutes, smb) })
            }
            add("cancelBolus" to { it.call("cancelBolus") })
            for ((rate, minutes) in listOf(1.5 to 30, 0.0 to 60, 0.275 to 90, 2.0 to 45, 2.0 to 0)) add("tbr $rate $minutes" to { p: Pump -> p.call("startTempBasal", rate, minutes) })
            add("cancelTbr" to { it.call("cancelTempBasal") })
            for (mode in 0..2) add("closed $mode" to { p: Pump -> p.call("setClosedMode", mode) })
            add("continue" to { it.call("continueInject") })
            add("startBasal" to { it.call("startBasal") })
            add("push" to { it.call("pushPosition") })
            add("replace" to { it.call("replaceDevice") })
            for (needle in 0..1) add("needle $needle" to { p: Pump -> p.call("needlePushAir", needle.toByte()) })
            for (time in listOf(0L, 1609430400000L, 1609430400001L, 1790000000000L)) {
                add("daily $time" to { p: Pump -> p.call("getHistoryDailyData", time) })
                add("log $time" to { p: Pump -> p.call("getHistoryLogData", time) })
            }
            add("unbind" to { it.call("unbindDevice") })
            add("time" to { it.call("updatePumpTime") })
            add("curStatus" to { it.call("getCurStatus") })
            for (type in 0..4) add("confirm $type" to { p: Pump -> p.call("confirmReply", type) })
            add("basicParam" to { it.basicParam(1.5, 10.0, 2, 20.0, 1, 3) })
            add("basicParam2" to { it.basicParam(35.0, 25.0, 1, 50.0, 3, 0) })
            add("basal" to { it.basal(12.3, listOf(0 to 0.5, 12 to 0.275, 40 to 1.15)) })
        }

        for (version in listOf<Byte>(1, 2)) {
            feed("report", 17, ReportDevicePacket().apply { deviceType = 6; this.version = version; serial = 100 }.toBytes())
            for (maxBolus in listOf<Short>(10000, 0)) {
                feed("param", 0, BasicParamPacket().apply { this.maxBolus = maxBolus; pumpType = 3 }.toBytes())
                for (running in 0..8) {
                    for ((name, command) in commands) {
                        val status = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(5).putShort(200).put(running.toByte()).array()
                        feed("status $running", 6, status)
                        both("$name running=$running version=$version maxBolus=$maxBolus", command)
                        both("done $name running=$running") { it.done(4) }
                        both("done2 $name running=$running") { it.done(-3) }
                        feed("status0", 6, ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(5).putShort(200).put(0).array())
                    }
                }
            }
        }

        val ids = listOf<Short>(0, 1, 2, 3, 4, 6, 12, 14, 15, 16, 17, 19, 20, 21, 24, 7, 99, -3, -20, -21)
        repeat(20000) {
            val id = ids[random.nextInt(ids.size)]
            val payload = bytes(random.nextInt(0, 60))
            if (id == 12.toShort() && payload.size >= 2 && random.nextBoolean()) {
                payload[0] = listOf(25, 28, 1)[random.nextInt(3)].toByte()
                payload[1] = listOf(0, 128, 129)[random.nextInt(3)].toByte()
            }
            feed("rx $id", id, payload)
        }
        repeat(5000) {
            val frame = bytes(random.nextInt(0, 70))
            both("raw ${frame.toHex()}") { it.receive(frame) }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private interface Pump {

        fun receive(frame: ByteArray)
        fun done(paramId: Short)
        fun call(name: String, vararg args: Any)
        fun basicParam(maxBasal: Double, maxBolus: Double, step: Int, low: Double, sound: Int, type: Int)
        fun basal(perDay: Double, items: List<Pair<Int, Double>>)
        fun snapshot(): String
    }

    private fun primitive(arg: Any): Class<*> = when (arg) {
        is Double  -> Double::class.javaPrimitiveType!!
        is Int     -> Int::class.javaPrimitiveType!!
        is Long    -> Long::class.javaPrimitiveType!!
        is Boolean -> Boolean::class.javaPrimitiveType!!
        is Byte    -> Byte::class.javaPrimitiveType!!
        else       -> arg.javaClass
    }

    private val timedParams = setOf<Short>(0, 1, 4, 5, 7, 8, 9, 10, 17)

    private fun describe(paramId: Short, isRead: Boolean, data: ByteArray): String {
        val masked = data.copyOf()
        if (paramId in timedParams) for (i in 0 until minOf(4, masked.size)) masked[i] = 0
        return "$paramId/$isRead/${masked.toHex()}"
    }

    private fun recorder(target: Class<*>, log: MutableList<String>): Any =
        Proxy.newProxyInstance(target.classLoader, arrayOf(target)) { _, method, args ->
            if (method.declaringClass == Any::class.java) return@newProxyInstance null
            log.add(method.name + "(" + args.orEmpty().joinToString() + ")")
            Unit
        }

    private fun attempt(block: () -> Unit) {
        try {
            block()
        } catch (e: InvocationTargetException) {
            lastThrow = e.targetException.javaClass.simpleName
        }
    }

    private var lastThrow = ""

    private inner class OldPump : Pump {

        val log = mutableListOf<String>()
        val util: Any = cls("com.aaruy.mylibrary.AABleUtil").getConstructor(Context::class.java).newInstance(mock(Context::class.java))
        val manager: Any = util.javaClass.getDeclaredField("myBleManager").apply { isAccessible = true }.get(util)

        init {
            util.javaClass.getMethod("setOnBleListener", cls("com.aaruy.mylibrary.open.interfaces.MyBleListener"))
                .invoke(util, recorder(cls("com.aaruy.mylibrary.open.interfaces.MyBleListener"), log))
            manager.javaClass.getField("c").set(manager, mock(BluetoothGattCharacteristic::class.java))
        }

        override fun receive(frame: ByteArray) {
            manager.javaClass.getField("i").setBoolean(manager, true)
            attempt { manager.javaClass.getMethod("b", ByteArray::class.java).invoke(manager, frame) }
        }

        override fun done(paramId: Short) {
            val method = manager.javaClass.getMethod(
                "a", Short::class.javaPrimitiveType, manager.javaClass, util.javaClass, BluetoothDevice::class.java
            )
            attempt { method.invoke(null, paramId, manager, util, mock(BluetoothDevice::class.java)) }
            manager.javaClass.getField("i").setBoolean(manager, true)
        }

        override fun call(name: String, vararg args: Any) {
            manager.javaClass.getField("i").setBoolean(manager, true)
            attempt { util.javaClass.getMethod(name, *args.map { primitive(it) }.toTypedArray()).invoke(util, *args) }
        }

        override fun basicParam(maxBasal: Double, maxBolus: Double, step: Int, low: Double, sound: Int, type: Int) {
            val p = cls("com.aaruy.mylibrary.open.data.BasicParam")
            val d = Double::class.javaPrimitiveType
            val i = Int::class.javaPrimitiveType
            val param = p.getConstructor(d, d, i, d, i, i).newInstance(maxBasal, maxBolus, step, low, sound, type)
            manager.javaClass.getField("i").setBoolean(manager, true)
            util.javaClass.getMethod("setupBasicParamInfo", p).invoke(util, param)
        }

        override fun basal(perDay: Double, items: List<Pair<Int, Double>>) {
            val d = Double::class.javaPrimitiveType
            val i = Int::class.javaPrimitiveType
            val item = cls("com.aaruy.mylibrary.open.data.BasalItem")
            val list = items.map { item.getConstructor(i, d).newInstance(it.first, it.second) }
            val itemsClass = cls("com.aaruy.mylibrary.open.data.BasalItems")
            val all = itemsClass.getConstructor(d, i, List::class.java).newInstance(perDay, items.size, list)
            val info = cls("com.aaruy.mylibrary.open.data.BasalInfo")
            manager.javaClass.getField("i").setBoolean(manager, true)
            util.javaClass.getMethod("setupBasalInfo", info).invoke(util, info.getConstructor(itemsClass).newInstance(all))
        }

        override fun snapshot(): String {
            @Suppress("UNCHECKED_CAST") val queue = manager.javaClass.getField("j").get(manager) as Queue<Any>
            val sent = queue.map {
                describe(
                    it.javaClass.getMethod("getParamId-Mh2AYeg").invoke(it) as Short,
                    it.javaClass.getMethod("isRead").invoke(it) as Boolean,
                    it.javaClass.getMethod("getData").invoke(it) as ByteArray
                )
            }
            queue.clear()
            val result = "seq=" + manager.javaClass.getField("g").get(manager) + " queue=" + sent + " log=" + log
            log.clear()
            return result
        }
    }

    private inner class NewPump : Pump {

        val log = mutableListOf<String>()
        val client = AaruyBleClient(mock(Context::class.java), mock(AAPSLogger::class.java), mock(AaruyBleTransport::class.java, RETURNS_DEEP_STUBS), mock(Looper::class.java))
        val manager: Any = client.javaClass.getDeclaredField("link").apply { isAccessible = true }.get(client)
        val writing = manager.javaClass.getDeclaredField("writing").apply { isAccessible = true }

        init {
            client.setOnBleListener(recorder(AaruyBleListener::class.java, log) as AaruyBleListener)
            manager.javaClass.getDeclaredField("ready").apply { isAccessible = true }.setBoolean(manager, true)
        }

        override fun receive(frame: ByteArray) {
            writing.setBoolean(manager, true)
            attempt { manager.javaClass.getDeclaredMethod("handleFrame", ByteArray::class.java).apply { isAccessible = true }.invoke(manager, frame) }
        }

        override fun done(paramId: Short) {
            attempt { manager.javaClass.getDeclaredMethod("onWriteDone", Short::class.javaPrimitiveType).apply { isAccessible = true }.invoke(manager, paramId) }
            writing.setBoolean(manager, true)
        }

        override fun call(name: String, vararg args: Any) {
            writing.setBoolean(manager, true)
            attempt { client.javaClass.getMethod(name, *args.map { primitive(it) }.toTypedArray()).invoke(client, *args) }
        }

        override fun basicParam(maxBasal: Double, maxBolus: Double, step: Int, low: Double, sound: Int, type: Int) {
            writing.setBoolean(manager, true)
            client.setupBasicParamInfo(BasicParam(maxBasal, maxBolus, step, low, sound, type))
        }

        override fun basal(perDay: Double, items: List<Pair<Int, Double>>) {
            writing.setBoolean(manager, true)
            client.setupBasalInfo(BasalInfo(BasalItems(perDay, items.size, items.map { BasalItem(it.first, it.second) })))
        }

        override fun snapshot(): String {
            @Suppress("UNCHECKED_CAST") val queue = manager.javaClass.getDeclaredField("queue").apply { isAccessible = true }.get(manager) as Queue<Any>
            val sent = queue.map { request ->
                fun <T> get(name: String): T {
                    @Suppress("UNCHECKED_CAST")
                    return request.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(request) as T
                }
                describe(get("paramId"), get("isRead"), get("data"))
            }
            queue.clear()
            val result = "seq=" + manager.javaClass.getDeclaredField("sequence").apply { isAccessible = true }.get(manager) + " queue=" + sent + " log=" + log
            log.clear()
            return result
        }
    }
}
