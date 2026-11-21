package app.aaps.plugins.eversense.protocol

import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Random
import java.util.TimeZone

/**
 * Every command the driver sends: its ids, its request, the request framed for several MTUs, and the decoding of many
 * answers (empty, short, random, history headers, glucose frames), in four time zones, against one digest per command
 * of what the first driver produced (see [GoldenDigests]). Three commands decode differently on purpose (lower limit,
 * "glucose unavailable", protocol version text): their digests pin today's decoding, which `DecodingFixesTest` explains.
 */
class ProtocolGoldenTest {

    private val instant = 1_735_689_610_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(instant), ZoneOffset.UTC)
    private val names = javaClass.getResourceAsStream("/protocol-command-classes.txt")!!.bufferedReader().readLines()
    private val golden = GoldenDigests("protocol")

    @Test fun everyCommandMatchesTheFirstDriver() {
        val previous = TimeZone.getDefault()
        try {
            for (name in names) {
                for (zone in listOf("UTC", "Europe/Berlin", "America/New_York", "Asia/Kolkata")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone))
                    PacketTime.withClock(clock) { record(name) }
                }
            }
        } finally {
            TimeZone.setDefault(previous)
        }
        golden.check()
    }

    private fun record(name: String) {
        val group = name.substringAfter(".packets.")
        val type = Class.forName(name)
        for (variant in 0..2) {
            val packet = create(type, variant)
            golden.add(group, metadata(packet))
            golden.add(group, result { invoke(packet, "getRequestData") })
            for (mtu in listOf(5, 20, 185, 512)) {
                for (emptyCiphertext in listOf(false, true)) golden.add(group, request(packet, mtu, emptyCiphertext))
            }
            for ((index, response) in responses(packet).withIndex()) golden.add(group, decode(create(type, variant), response, index % 3))
        }
    }

    private fun create(type: Class<*>, variant: Int): Any {
        val ctor = type.constructors.filterNot { it.isSynthetic }.maxBy { it.parameterCount }
        val args = ctor.parameterTypes.mapIndexed { index, param ->
            when {
                param == Int::class.javaPrimitiveType     -> listOf(10 + index, 0, 255)[variant]
                param == Long::class.javaPrimitiveType    -> instant - 120_000
                param == Boolean::class.javaPrimitiveType -> variant != 1
                param == Double::class.javaPrimitiveType  -> listOf(1.5, 0.0, -1.5)[variant]
                param == Float::class.javaPrimitiveType   -> listOf(1.5f, 0f, -1.5f)[variant]
                param == Byte::class.javaPrimitiveType    -> listOf(1, 0, 255)[variant].toByte()
                param == String::class.java               -> listOf("8.0.4", "", "ABC123")[variant]
                param == ByteArray::class.java            -> ByteArray(listOf(32, 0, 80)[variant]) { it.toByte() }
                param.isEnum                              -> param.enumConstants[variant % param.enumConstants.size]
                else                                      -> error("No constructor fixture for ${type.name}: ${param.name}")
            }
        }.toTypedArray()
        return ctor.newInstance(*args)
    }

    private fun metadata(packet: Any): Any? {
        val value = packet.javaClass.methods.first { it.name == "getSpec" }.invoke(packet)
        return listOf("requestId", "responseId", "typeId", "securityType").map { key ->
            val getter = value.javaClass.methods.first { it.name == key || it.name == "get" + key.replaceFirstChar(Char::uppercase) }
            normalize(getter.invoke(value))
        }
    }

    /** The request as framed for [mtu], with a stand-in cipher (or one that gives nothing) */
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
        // History log headers
        for (kind in listOf(0, 6, 13)) all.add(ByteArray(512).also { it[6] = kind.toByte() })
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
                        ByteBuffer.wrap(frame, 22, 8).order(ByteOrder.LITTLE_ENDIAN).putLong((instant - 946_684_800_000L) / 1000 * 1024)
                        frame[30] = glucose.toByte(); frame[31] = (glucose ushr 8).toByte()
                        frame[32] = 55; frame[174] = trend.toByte()
                    }
                    all.add(frame)
                }
            }
        }
        return all
    }

    /** Decodes [bytes] given in one piece, or in two or three */
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

    private fun result(action: () -> Any?): Any? = try {
        listOf("value", normalize(action()))
    } catch (e: Exception) {
        var cause: Throwable = e
        while (cause is InvocationTargetException) cause = cause.targetException
        listOf("exception", if (cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.simpleName)
    }

    private fun normalize(value: Any?): Any? = when (value) {
        null, is String, is Boolean -> value
        is Number                   -> value.toString()
        is Enum<*>                  -> value.name
        is ByteArray                -> value.joinToString("") { "%02x".format(it) }
        is Collection<*>            -> value.map(::normalize)
        is Map<*, *>                -> value.entries.associate { normalize(it.key).toString() to normalize(it.value) }
        else                        -> value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .sortedBy { it.name }.associate { it.name to normalize(it.apply { isAccessible = true }.get(value)) }
    }
}
