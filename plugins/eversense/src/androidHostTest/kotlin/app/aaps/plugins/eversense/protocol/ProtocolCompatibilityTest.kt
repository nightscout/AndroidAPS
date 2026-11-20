package app.aaps.plugins.eversense.protocol

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Random
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.mockito.Mockito
import org.mockito.Answers

/** The oracle is compiled from an immutable source snapshot, never from rewritten packet code. */
class ProtocolCompatibilityTest {
    private val instant = 1_735_689_610_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(instant), ZoneOffset.UTC)
    private val names = javaClass.getResourceAsStream("/protocol-command-classes.txt")!!.bufferedReader().readLines()

    @TestFactory fun allCommandContracts(): List<DynamicTest> = names.map { name ->
        DynamicTest.dynamicTest(name.substringAfter(".packets.")) {
            val previous = TimeZone.getDefault()
            try {
                for (zone in listOf("UTC", "Europe/Berlin", "America/New_York", "Asia/Kolkata")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone))
                    PacketTime.withClock(clock) { compare(name) }
                }
            } finally { TimeZone.setDefault(previous) }
        }
    }

    private fun compare(name: String) {
        val oldName = name.replace("app.aaps.plugins.eversense", "app.aaps.plugins.eversense.reference")
        val current = Class.forName(name)
        val reference = Class.forName(oldName)
        for (variant in 0..2) {
            val original = create(reference, variant)
            val rewritten = create(current, variant)
            assertEquals(metadata(original), metadata(rewritten), "$name descriptor")
            assertEquals(result { invoke(original, "getRequestData") }, result { invoke(rewritten, "getRequestData") }, "$name payload variant=$variant")
            for (mtu in listOf(5, 20, 185, 512)) {
                for (emptyCiphertext in listOf(false, true)) {
                    assertEquals(request(original, mtu, emptyCiphertext), request(rewritten, mtu, emptyCiphertext), "$name framed request variant=$variant mtu=$mtu emptyCiphertext=$emptyCiphertext")
                }
            }
            for ((index, response) in responses(original).withIndex()) {
                val old = create(reference, variant)
                val new = create(current, variant)
                assertEquals(decode(old, response, index % 3), decode(new, response, index % 3), "$name response case=$index variant=$variant size=${response.size}")
            }
        }
    }

    private fun create(type: Class<*>, variant: Int): Any {
        val ctor = type.constructors.filterNot { it.isSynthetic }.maxBy { it.parameterCount }
        val args = ctor.parameterTypes.mapIndexed { index, param ->
            when {
                param == Int::class.javaPrimitiveType -> listOf(10 + index, 0, 255)[variant]
                param == Long::class.javaPrimitiveType -> instant - 120_000
                param == Boolean::class.javaPrimitiveType -> variant != 1
                param == Double::class.javaPrimitiveType -> listOf(1.5, 0.0, -1.5)[variant]
                param == Float::class.javaPrimitiveType -> listOf(1.5f, 0f, -1.5f)[variant]
                param == Byte::class.javaPrimitiveType -> listOf(1, 0, 255)[variant].toByte()
                param == String::class.java -> listOf("8.0.4", "", "ABC123")[variant]
                param == ByteArray::class.java -> ByteArray(listOf(32, 0, 80)[variant]) { it.toByte() }
                param.isEnum -> param.enumConstants[variant % param.enumConstants.size]
                else -> error("No constructor fixture for ${type.name}: ${param.name}")
            }
        }.toTypedArray()
        return ctor.newInstance(*args)
    }

    private fun metadata(packet: Any): List<Any?> {
        val value = packet.javaClass.methods.firstOrNull { it.name == "getSpec" }?.invoke(packet)
            ?: invoke(packet, "getAnnotation")!!
        return listOf("requestId", "responseId", "typeId", "securityType").map { key ->
            val getter = value.javaClass.methods.first { it.name == key || it.name == "get" + key.replaceFirstChar(Char::uppercase) }
            normalize(getter.invoke(value))
        }
    }

    private fun request(packet: Any, mtu: Int, emptyCiphertext: Boolean): Any? = result {
        val method = packet.javaClass.methods.single { it.name == "buildRequest" }
        val crypto = Mockito.mock(method.parameterTypes[0]) { invocation ->
            if (invocation.method.name == "encrypt") {
                if (emptyCiphertext) byteArrayOf() else byteArrayOf(0x7e) + invocation.getArgument<ByteArray>(0).map { (it.toInt() xor 0x55).toByte() }.toByteArray()
            } else Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        method.invoke(packet, crypto, mtu)
    }

    private fun responses(packet: Any): List<ByteArray> {
        val random = Random(191)
        val all = mutableListOf<ByteArray>()
        for (size in listOf(0, 1, 2, 3, 4, 7, 11, 14, 20, 32, 64, 128, 192, 256, 512)) {
            all.add(ByteArray(size))
            all.add(ByteArray(size) { 0x55 })
            all.add(ByteArray(size).also(random::nextBytes))
        }
        // Populate the body independently of parser implementations, including history-log headers.
        for (kind in listOf(0, 6, 13)) {
            all.add(ByteArray(512).also { it[6] = kind.toByte() })
        }
        if (packet.javaClass.simpleName in setOf("GetCurrentGlucosePacket", "GetGlucoseDataPacket")) {
            for (glucose in listOf(0, 19, 20, 100, 449, 450, 451, 65535)) {
                for (trend in listOf(0, 1, 2, 4, 8, 16, 32, 64, 255)) {
                    val frame = ByteArray(192)
                    if (packet.javaClass.simpleName == "GetCurrentGlucosePacket") {
                        frame[0] = 0x88.toByte()
                        frame[4] = 0x21; frame[5] = 0x32 // 2025-01-01 UTC
                        frame[6] = 5 // 00:00:10 UTC
                        frame[9] = glucose.toByte(); frame[10] = (glucose ushr 8).toByte()
                        frame[13] = trend.toByte()
                    } else {
                        frame[0] = 0x42; frame[1] = 0x1f; frame[11] = 10
                        java.nio.ByteBuffer.wrap(frame, 22, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            .putLong((instant - 946_684_800_000L) / 1000 * 1024)
                        frame[30] = glucose.toByte(); frame[31] = (glucose ushr 8).toByte()
                        frame[32] = 55; frame[174] = trend.toByte()
                    }
                    all.add(frame)
                }
            }
        }
        return all
    }

    private fun decode(packet: Any, bytes: ByteArray, split: Int): Any? = result {
        val append = packet.javaClass.methods.single { it.name.startsWith("appendData") }
        if (split == 0) append.invoke(packet, bytes)
        else {
            val boundary = bytes.size / (split + 1)
            append.invoke(packet, bytes.copyOfRange(0, boundary))
            append.invoke(packet, bytes.copyOfRange(boundary, bytes.size))
        }
        invoke(packet, "parseResponse")
    }

    private fun invoke(target: Any, method: String): Any? = target.javaClass.methods.single { it.name == method && it.parameterCount == 0 }.invoke(target)
    private fun result(action: () -> Any?): Any? = try { listOf("value", normalize(action())) } catch (e: Exception) {
        var cause: Throwable = e
        while (cause is InvocationTargetException) cause = cause.targetException
        // Preserve malformed-input outcome categories without coupling to implementation-specific messages.
        listOf("exception", when (cause) {
            is IndexOutOfBoundsException -> "bounds"
            else -> cause.javaClass.simpleName
        })
    }
    private fun normalize(value: Any?): Any? = when (value) {
        null, is String, is Boolean -> value
        is Number -> value.toString()
        is Enum<*> -> value.name
        is ByteArray -> value.joinToString("") { "%02x".format(it) }
        is Collection<*> -> value.map(::normalize)
        is Map<*, *> -> value.entries.associate { normalize(it.key).toString() to normalize(it.value) }
        else -> value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .sortedBy { it.name }.associate { it.name to normalize(it.apply { isAccessible = true }.get(value)) }
    }
}
